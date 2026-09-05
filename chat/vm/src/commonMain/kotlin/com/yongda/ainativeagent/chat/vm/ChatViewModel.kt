package com.yongda.ainativeagent.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yongda.ainativeagent.chat.ui.ChatMessageUi
import com.yongda.ainativeagent.chat.ui.ChatRole
import com.yongda.ainativeagent.chat.ui.ChatUiState
import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 */
class ChatViewModel(
    private val provider: LlmProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var nextId = 0L

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

    private fun generate() {
        val assistantId = nextId++
        _state.update {
            it.copy(
                messages = it.messages + ChatMessageUi(assistantId, ChatRole.ASSISTANT, "", streaming = true),
                isStreaming = true,
                error = null,
            )
        }

        // 构造发给模型的历史：system + 除本次占位外的全部消息
        val history = buildList {
            add(ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt))
            _state.value.messages
                .filter { it.id != assistantId }
                .forEach { add(ChatMessage(it.role.toCore(), it.content)) }
        }

        streamJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                provider.streamChat(history).collect { delta ->
                    sb.append(delta)
                    updateAssistant(assistantId, sb.toString(), streaming = true)
                }
                updateAssistant(assistantId, sb.toString(), streaming = false)
                _state.update { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                // 中断：定格已生成部分，向上传播取消
                updateAssistant(assistantId, sb.toString(), streaming = false)
                _state.update { it.copy(isStreaming = false) }
                throw e
            } catch (e: Exception) {
                // 网络/协议异常：保留部分内容，展示错误条供重试
                updateAssistant(assistantId, sb.toString(), streaming = false)
                _state.update { it.copy(isStreaming = false, error = e.message ?: "请求失败，请重试") }
            }
        }
    }

    private fun updateAssistant(id: Long, content: String, streaming: Boolean) {
        _state.update { st ->
            st.copy(
                messages = st.messages.map {
                    if (it.id == id) it.copy(content = content, streaming = streaming) else it
                },
            )
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
