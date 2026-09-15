package com.yongda.ainativeagent.llm.core

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
)
