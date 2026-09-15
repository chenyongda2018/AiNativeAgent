package com.yongda.ainativeagent.llm.core

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)
