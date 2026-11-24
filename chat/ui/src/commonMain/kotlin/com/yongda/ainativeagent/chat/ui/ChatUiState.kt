package com.yongda.ainativeagent.chat.ui

import androidx.compose.runtime.Immutable

/** UI 层的消息角色（与网络层解耦，仅区分气泡展示）。 */
enum class ChatRole { USER, ASSISTANT }

/** 单次工具调用在 UI 中的状态。 */
enum class ToolCallUiStatus { RUNNING, SUCCEEDED, FAILED }

/** 用于在助手气泡中展示的工具执行轨迹条目。 */
@Immutable
data class ToolCallUi(
    val id: String,
    val name: String,
    val label: String = name,
    val status: ToolCallUiStatus,
    val errorCode: String? = null,
)

@Immutable
data class ThinkingContent(
    val durationSeconds: Float,
    val text: String,
)

@Immutable
data class ChatMessageUi(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val streaming: Boolean = false,
    val thinking: ThinkingContent? = null,
    val modelName: String? = null,
    val toolCalls: List<ToolCallUi> = emptyList(),
)

/**
 * 聊天界面的完整状态。由 ViewModel 产出、UI 消费；本模块不含任何业务/网络依赖。
 *
 * [messages] 只保存已经定稿的消息；高频变化的 [streamingMessage] 单独存放，避免流式输出时
 * 反复复制整个历史列表，也让 Compose 可以跳过没有变化的历史气泡。
 *
 * @param error 非空表示上一次请求失败，UI 展示错误条与"重试"。
 * @param modelId 当前选中的模型 id，驱动顶栏/输入栏胶囊与空态展示，由上层注入。
 * @param availableModels 可供切换的模型列表，由上层注入。
 */
@Immutable
data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val streamingMessage: ChatMessageUi? = null,
    val error: String? = null,
    val modelId: String = "",
    val availableModels: List<ChatModel> = emptyList(),
) {
    val isStreaming: Boolean get() = streamingMessage != null

    /** 当前选中模型的展示名；找不到映射时回退到 [modelId] 或 `"AI"`。 */
    val selectedModelName: String
        get() = availableModels.firstOrNull { it.id == modelId }?.name
            ?: modelId.ifBlank { "AI" }
}
