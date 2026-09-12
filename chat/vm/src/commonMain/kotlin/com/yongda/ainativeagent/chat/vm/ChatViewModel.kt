package com.yongda.ainativeagent.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.ChatModels
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.chat.ui.ChatUiState
import com.yongda.ainativeagent.chat.ui.ThinkingContent
import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 聊天 ViewModel：把抽象 [LlmProvider] 的流式输出编排成 [ChatUiState]。
 *
 * 覆盖阶段 0-1 的五项验收：
 *  - 多轮聊天：每次请求携带完整历史（system + 既往 user/assistant）。
 *  - 流式输出：逐增量追加到助手消息 content。
 *  - 网络异常处理：捕获异常 → [ChatUiState.error]，保留已生成的部分内容。
 *  - 中断生成：[cancel] 取消协程，已生成部分定格为完成态。
 *  - 重试：[retry] 丢弃失败的助手占位并用当前历史重发。
 *
 * 只依赖抽象 provider，与 DeepSeek/OpenAI/本地模型无关，可独立复用。
 *
 * @param providerFactory 按 modelId 产出对应的 [LlmProvider]（id 即 API `model` 名）。首次用到某模型时
 *   构造并缓存，切换模型不重建 ViewModel、聊天历史得以保留。
 * @param initialModelId 初始选中的模型 id，写入 [ChatUiState.modelId] 供 UI 展示。
 */
class ChatViewModel(
    private val providerFactory: (modelId: String) -> LlmProvider,
    initialModelId: String,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** 单一固定 provider 的便捷构造（测试 / 不需要切换模型的场景）。 */
    constructor(
        provider: LlmProvider,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this({ provider }, ChatModels.default.id, systemPrompt, dispatcher)

    private val _state = MutableStateFlow(ChatUiState(modelId = initialModelId))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var currentModelId = initialModelId
    private val providerCache = mutableMapOf<String, LlmProvider>()
    private fun provider(): LlmProvider = providerCache.getOrPut(currentModelId) { providerFactory(currentModelId) }

    private var streamJob: Job? = null
    private var nextId = 0L

    /** 切换模型：流式进行中忽略。仅改变后续请求所用模型，不影响已有历史。 */
    fun selectModel(id: String) {
        if (id == currentModelId || _state.value.isStreaming) return
        currentModelId = id
        _state.update { it.copy(modelId = id) }
    }

    /** 发送一条用户消息并开始流式生成。流式进行中忽略。 */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isStreaming) return
        val userMsg = ChatMessageUi(id = nextId++, role = ChatRole.USER, content = trimmed)
        _state.update { it.copy(messages = it.messages + userMsg, error = null) }
        generate()
    }

    /** 中断生成：已到达的增量作为最终结果保留。 */
    fun cancel() {
        streamJob?.cancel()
    }

    /** 重试：丢弃末尾失败的助手消息，用当前历史重新生成。 */
    fun retry() {
        if (_state.value.isStreaming) return
        _state.update { st ->
            val msgs = st.messages
            if (msgs.isNotEmpty() && msgs.last().role == ChatRole.ASSISTANT) {
                st.copy(messages = msgs.dropLast(1), error = null)
            } else {
                st.copy(error = null)
            }
        }
        generate()
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun generate() {
        val assistantId = nextId++
        _state.update {
            it.copy(
                streamingMessage = ChatMessageUi(
                    assistantId,
                    ChatRole.ASSISTANT,
                    "",
                    streaming = true,
                    modelName = ChatModels.nameOf(currentModelId),
                ),
                error = null,
            )
        }

        val messages = _state.value.messages
        streamJob = viewModelScope.launch(dispatcher, start = CoroutineStart.ATOMIC) {
            val receivedContent = StringBuilder()
            try {
                ensureActive()
                // 构造发给模型的历史：system + 除本次占位外的全部消息
                val history = buildList {
                    add(ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt))
                    messages.forEach { add(ChatMessage(it.role.toCore(), it.content)) }
                }
                coroutineScope {
                    // 正式回答走显示节奏器（打字机效果）；思考过程实时累积到 thinking，不参与节奏控制。
                    val contentDeltas = Channel<String>(Channel.BUFFERED)
                    val pacer = launch {
                        contentDeltas.consumeAsFlow()
                            .paceTextForUi()
                            .collect { snapshot -> updateStreamingContent(assistantId, snapshot) }
                    }
                    val reasoning = StringBuilder()
                    var reasoningStart: TimeMark? = null
                    try {
                        provider().streamChatDetailed(history).collect { chunk ->
                            when (chunk) {
                                is LlmChunk.Reasoning -> {
                                    val start = reasoningStart
                                        ?: TimeSource.Monotonic.markNow().also { reasoningStart = it }
                                    reasoning.append(chunk.text)
                                    val seconds = start.elapsedNow().inWholeMilliseconds / 100 / 10f
                                    updateStreamingThinking(assistantId, reasoning.toString(), seconds)
                                }
                                is LlmChunk.Content -> {
                                    receivedContent.append(chunk.text)
                                    contentDeltas.send(chunk.text)
                                }
                            }
                        }
                    } finally {
                        contentDeltas.close()
                    }
                    pacer.join()
                }
                finishAssistant(assistantId, receivedContent.toString())
            } catch (e: CancellationException) {
                // 中断：包含已经到达但尚未被显示节奏器排空的内容，避免丢字。
                finishAssistant(assistantId, receivedContent.toString())
                throw e
            } catch (e: Exception) {
                // 网络/协议异常：保留部分内容，展示错误条供重试
                finishAssistant(
                    id = assistantId,
                    content = receivedContent.toString(),
                    error = e.message ?: "请求失败，请重试",
                )
            }
        }
    }

    private fun updateStreamingContent(id: Long, content: String) {
        _state.update { st ->
            val streaming = st.streamingMessage
            if (streaming?.id == id) {
                st.copy(streamingMessage = streaming.copy(content = content))
            } else {
                st
            }
        }
    }

    private fun updateStreamingThinking(id: Long, text: String, durationSeconds: Float) {
        _state.update { st ->
            val streaming = st.streamingMessage
            if (streaming?.id == id) {
                st.copy(streamingMessage = streaming.copy(thinking = ThinkingContent(durationSeconds, text)))
            } else {
                st
            }
        }
    }

    /** 把高频变化的流式草稿一次性并入历史列表。 */
    private fun finishAssistant(id: Long, content: String, error: String? = null) {
        _state.update { st ->
            val streaming = st.streamingMessage
            if (streaming?.id == id) {
                st.copy(
                    messages = st.messages + streaming.copy(content = content, streaming = false),
                    streamingMessage = null,
                    error = error,
                )
            } else {
                st
            }
        }
    }

    private fun ChatRole.toCore(): ChatMessage.Role = when (this) {
        ChatRole.USER -> ChatMessage.Role.USER
        ChatRole.ASSISTANT -> ChatMessage.Role.ASSISTANT
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "你是一个乐于助人的中文 AI 助手，回答简洁、准确。"
    }
}
