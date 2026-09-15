package com.yongda.ainativeagent.llm.core

/**
 * 一条对话消息。角色语义对齐 OpenAI-compatible 协议，便于各云端 provider 直接映射。
 */
data class ChatMessage(
    val role: Role,
    val content: String,
    val reasoningContent: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
) {
    enum class Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL,
    }
}
