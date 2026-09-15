package com.yongda.ainativeagent.llm.core

data class LlmRequest(
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition> = emptyList(),
    val allowToolCalls: Boolean = true,
)
