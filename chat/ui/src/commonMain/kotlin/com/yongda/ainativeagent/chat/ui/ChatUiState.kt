package com.yongda.ainativeagent.chat.ui

import androidx.compose.runtime.Immutable

/** UI 层的消息角色（与网络层解耦，仅区分气泡展示）。 */
enum class ChatRole { USER, ASSISTANT }

/**
 * 一条聊天消息的 UI 模型。
 *
 * @param id 稳定 id，用于 LazyColumn key。
 * @param streaming 该助手消息是否正在流式生成中（用于展示"正在输入"等）。
 */
@Immutable
data class ChatMessageUi(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val streaming: Boolean = false,
)

/**
 * 聊天界面的完整状态。由 ViewModel 产出、UI 消费；本模块不含任何业务/网络依赖。
 *
 * [messages] 只保存已经定稿的消息；高频变化的 [streamingMessage] 单独存放，避免流式输出时
 * 反复复制整个历史列表，也让 Compose 可以跳过没有变化的历史气泡。
 *
 * @param error 非空表示上一次请求失败，UI 展示错误条与"重试"。
 */
@Immutable
data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val streamingMessage: ChatMessageUi? = null,
    val error: String? = null,
) {
    val isStreaming: Boolean get() = streamingMessage != null
}
