package com.yongda.ainativeagent.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yongda.ainativeagent.agent.runtime.AgentTurnEngine
import com.yongda.ainativeagent.agent.runtime.AgentTurnEvent
import com.yongda.ainativeagent.agent.runtime.ConversationHistory
import com.yongda.ainativeagent.agent.runtime.asPlainTurnEngine
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.ChatModel
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.chat.ui.ChatUiState
import com.yongda.ainativeagent.chat.ui.ThinkingContent
import com.yongda.ainativeagent.chat.ui.ToolCallUi
import com.yongda.ainativeagent.chat.ui.ToolCallUiStatus
import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 聊天表现层 ViewModel：把 [AgentTurnEngine] 产出的领域事件流编排成 [ChatUiState]。
 *
 * 职责边界：
 * - 维护 UI 展示状态（[ChatUiState]）与不可变领域会话历史（[ConversationHistory]），通过单一 [MutableStateFlow]
 *   原子更新，确保 `state.value` 同步可见且跨轮保留完整的工具调用上下文；
 * - 负责流式正文显示节流（[paceTextForUi]）、思考过程计时、工具调用状态展示、异常捕获、中断与重试、新建对话重置；
 * - 不直接依赖具体 LLM 实现或具体工具实现。
 */
class ChatViewModel(
    private val engineFactory: (modelId: String) -> AgentTurnEngine,
    initialModelId: String,
    availableModels: List<ChatModel> = emptyList(),
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeContextProvider: () -> String? = { null },
    private val toolLabelResolver: (toolName: String) -> String = { it },
) : ViewModel() {

    /** 单一固定 provider 的便捷构造（测试 / 不需要切换模型也不带工具的场景）。 */
    constructor(
        provider: LlmProvider,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        engineFactory = { provider.asPlainTurnEngine() },
        initialModelId = provider.name,
        availableModels = emptyList(),
        systemPrompt = systemPrompt,
        dispatcher = dispatcher,
    )

    private data class SessionState(
        val epoch: Long = 0L,
        val history: ConversationHistory = ConversationHistory(),
        val ui: ChatUiState,
    )

    private val _session = MutableStateFlow(
        SessionState(
            ui = ChatUiState(
                modelId = initialModelId,
                availableModels = availableModels,
            ),
        ),
    )

    /** 对外暴露只读 UI 状态，与 [_session] 同步映射，无额外异步延迟。 */
    val state: StateFlow<ChatUiState> = MappedStateFlow(_session) { it.ui }

    private var currentModelId = initialModelId
    private val engineCache = mutableMapOf<String, AgentTurnEngine>()
    private fun engine(): AgentTurnEngine = engineCache.getOrPut(currentModelId) { engineFactory(currentModelId) }

    private var streamJob: Job? = null
    private var nextId = 0L

    /** 切换模型：流式进行中忽略。仅改变后续请求所用模型，不影响已有历史。 */
    fun selectModel(id: String) {
        if (id == currentModelId || _session.value.ui.isStreaming) return
        currentModelId = id
        _session.update { st -> st.copy(ui = st.ui.copy(modelId = id)) }
    }

    /** 发送一条用户消息并开始流式生成。流式进行中忽略。 */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _session.value.ui.isStreaming) return
        val userMsg = ChatMessageUi(id = nextId++, role = ChatRole.USER, content = trimmed)
        _session.update { st ->
            st.copy(
                history = st.history.startTurn(trimmed),
                ui = st.ui.copy(messages = st.ui.messages + userMsg, error = null),
            )
        }
        generate()
    }

    /** 中断生成：已到达的增量作为最终结果保留。 */
    fun cancel() {
        streamJob?.cancel()
    }

    /** 重试：丢弃末尾失败的助手消息（并回滚本轮工具轨迹），用当前历史重新生成。 */
    fun retry() {
        if (_session.value.ui.isStreaming) return
        _session.update { st ->
            val msgs = st.ui.messages
            val updatedUi = if (msgs.isNotEmpty() && msgs.last().role == ChatRole.ASSISTANT) {
                st.ui.copy(messages = msgs.dropLast(1), error = null)
            } else {
                st.ui.copy(error = null)
            }
            st.copy(
                history = st.history.rollbackLastTurnResponse(),
                ui = updatedUi,
            )
        }
        generate()
    }

    /** 开启新对话：递增会话代际、取消进行中的生成，并清空历史与错误，保留模型选择。 */
    fun newChat() {
        val activeJob = streamJob
        streamJob = null
        activeJob?.cancel()
        _session.update { st ->
            st.copy(
                epoch = st.epoch + 1,
                history = st.history.clear(),
                ui = ChatUiState(
                    modelId = currentModelId,
                    availableModels = st.ui.availableModels,
                ),
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun generate() {
        val assistantId = nextId++
        var capturedEpoch = 0L
        var llmHistory: List<ChatMessage> = emptyList()
        _session.update { st ->
            capturedEpoch = st.epoch
            llmHistory = st.history.toLlmMessages(effectiveSystemPrompt())
            st.copy(
                ui = st.ui.copy(
                    streamingMessage = ChatMessageUi(
                        id = assistantId,
                        role = ChatRole.ASSISTANT,
                        content = "",
                        streaming = true,
                        modelName = st.ui.selectedModelName,
                    ),
                    error = null,
                ),
            )
        }

        streamJob = viewModelScope.launch(dispatcher, start = CoroutineStart.ATOMIC) {
            val receivedContent = StringBuilder()
            val reasoning = StringBuilder()
            var reasoningStart: TimeMark? = null
            var finalAnswerCommitted = false
            try {
                ensureActive()
                coroutineScope {
                    // 正式回答走显示节奏器（打字机效果）；思考过程与工具状态实时刷新，不参与节奏控制。
                    val contentDeltas = Channel<String>(Channel.BUFFERED)
                    val pacer = launch {
                        contentDeltas.consumeAsFlow()
                            .paceTextForUi()
                            .collect { snapshot -> updateStreamingContent(capturedEpoch, assistantId, snapshot) }
                    }
                    try {
                        engine().run(llmHistory).collect { event ->
                            when (event) {
                                is AgentTurnEvent.ReasoningDelta -> {
                                    val start = reasoningStart
                                        ?: TimeSource.Monotonic.markNow().also { reasoningStart = it }
                                    reasoning.append(event.text)
                                    val seconds = start.elapsedNow().inWholeMilliseconds / 100 / 10f
                                    updateStreamingThinking(capturedEpoch, assistantId, reasoning.toString(), seconds)
                                }
                                is AgentTurnEvent.ContentDelta -> {
                                    receivedContent.append(event.text)
                                    contentDeltas.send(event.text)
                                }
                                is AgentTurnEvent.ToolCallStarted -> {
                                    onToolCallStarted(capturedEpoch, assistantId, event)
                                }
                                is AgentTurnEvent.ToolCallFinished -> {
                                    onToolCallFinished(capturedEpoch, assistantId, event)
                                }
                                is AgentTurnEvent.MessagesCommitted -> {
                                    commitHistoryMessages(capturedEpoch, event.messages)
                                    if (event.messages.lastOrNull()?.let {
                                            it.role == ChatMessage.Role.ASSISTANT && it.toolCalls.isEmpty()
                                        } == true
                                    ) {
                                        finalAnswerCommitted = true
                                    }
                                }
                            }
                        }
                    } finally {
                        contentDeltas.close()
                    }
                    pacer.join()
                }
                finishAssistant(
                    epoch = capturedEpoch,
                    id = assistantId,
                    content = receivedContent.toString(),
                    reasoningText = reasoning.toString(),
                    commitFallbackToHistory = !finalAnswerCommitted,
                )
            } catch (e: CancellationException) {
                // 中断：包含已经到达但尚未被显示节奏器排空的内容，避免丢字；若已被 newChat 重置则 epoch 不匹配会忽略。
                finishAssistant(
                    epoch = capturedEpoch,
                    id = assistantId,
                    content = receivedContent.toString(),
                    reasoningText = reasoning.toString(),
                    commitFallbackToHistory = !finalAnswerCommitted,
                )
                throw e
            } catch (e: Exception) {
                // 网络/协议异常：保留部分内容，展示错误条供重试
                finishAssistant(
                    epoch = capturedEpoch,
                    id = assistantId,
                    content = receivedContent.toString(),
                    reasoningText = reasoning.toString(),
                    error = e.message ?: "请求失败，请重试",
                    commitFallbackToHistory = !finalAnswerCommitted,
                )
            }
        }
    }

    private fun updateStreamingContent(epoch: Long, id: Long, content: String) {
        _session.update { st ->
            if (st.epoch != epoch) return@update st
            val streaming = st.ui.streamingMessage
            if (streaming?.id == id) {
                st.copy(ui = st.ui.copy(streamingMessage = streaming.copy(content = content)))
            } else {
                st
            }
        }
    }

    private fun updateStreamingThinking(epoch: Long, id: Long, text: String, durationSeconds: Float) {
        _session.update { st ->
            if (st.epoch != epoch) return@update st
            val streaming = st.ui.streamingMessage
            if (streaming?.id == id) {
                st.copy(
                    ui = st.ui.copy(
                        streamingMessage = streaming.copy(thinking = ThinkingContent(durationSeconds, text)),
                    ),
                )
            } else {
                st
            }
        }
    }

    private fun onToolCallStarted(epoch: Long, id: Long, event: AgentTurnEvent.ToolCallStarted) {
        val entry = ToolCallUi(
            id = event.callId,
            name = event.toolName,
            label = toolLabelResolver(event.toolName),
            status = ToolCallUiStatus.RUNNING,
        )
        _session.update { st ->
            if (st.epoch != epoch) return@update st
            val streaming = st.ui.streamingMessage
            if (streaming?.id == id) {
                st.copy(ui = st.ui.copy(streamingMessage = streaming.copy(toolCalls = streaming.toolCalls + entry)))
            } else {
                st
            }
        }
    }

    private fun onToolCallFinished(epoch: Long, id: Long, event: AgentTurnEvent.ToolCallFinished) {
        val nextStatus = if (event.ok) ToolCallUiStatus.SUCCEEDED else ToolCallUiStatus.FAILED
        _session.update { st ->
            if (st.epoch != epoch) return@update st
            val streaming = st.ui.streamingMessage
            if (streaming?.id == id) {
                val updatedCalls = streaming.toolCalls.map { item ->
                    if (item.id == event.callId) {
                        item.copy(status = nextStatus, errorCode = event.errorCode)
                    } else {
                        item
                    }
                }
                st.copy(ui = st.ui.copy(streamingMessage = streaming.copy(toolCalls = updatedCalls)))
            } else {
                st
            }
        }
    }

    private fun commitHistoryMessages(epoch: Long, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        _session.update { st ->
            if (st.epoch != epoch) return@update st
            st.copy(history = st.history.appendToCurrentTurn(messages))
        }
    }

    /** 把高频变化的流式草稿一次性并入历史列表。 */
    private fun finishAssistant(
        epoch: Long,
        id: Long,
        content: String,
        reasoningText: String,
        error: String? = null,
        commitFallbackToHistory: Boolean,
    ) {
        _session.update { st ->
            if (st.epoch != epoch) return@update st
            val streaming = st.ui.streamingMessage
            if (streaming?.id == id) {
                val nextHistory = if (commitFallbackToHistory) {
                    st.history.appendToCurrentTurn(
                        listOf(
                            ChatMessage(
                                role = ChatMessage.Role.ASSISTANT,
                                content = content,
                                reasoningContent = reasoningText.takeIf { it.isNotEmpty() },
                            ),
                        ),
                    )
                } else {
                    st.history
                }
                st.copy(
                    history = nextHistory,
                    ui = st.ui.copy(
                        messages = st.ui.messages + streaming.copy(content = content, streaming = false),
                        streamingMessage = null,
                        error = error,
                    ),
                )
            } else {
                st
            }
        }
    }

    /** 系统提示 = 固定人设 + 每轮刷新的设备时间上下文（若提供）。 */
    private fun effectiveSystemPrompt(): String {
        val timeContext = timeContextProvider()?.takeIf { it.isNotBlank() } ?: return systemPrompt
        return "$systemPrompt\n\n$timeContext"
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "你是一个乐于助人的中文 AI 助手，回答简洁、准确。"
    }
}

/** 轻量只读 StateFlow 视图：同步读取当前映射值，收集时做结构化去重。 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<S, R>(
    private val source: StateFlow<S>,
    private val transform: (S) -> R,
) : StateFlow<R> {

    override val replayCache: List<R> get() = listOf(value)

    override val value: R get() = transform(source.value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        var hasPrevious = false
        var previous: Any? = null
        source.collect { state ->
            val next = transform(state)
            if (!hasPrevious || next != previous) {
                hasPrevious = true
                previous = next
                collector.emit(next)
            }
        }
    }
}
