package com.yongda.ainativeagent.tool.core

data class ToolExecutionResult(
    val contentJson: String,
    val ok: Boolean,
    val errorCode: String? = null,
)
