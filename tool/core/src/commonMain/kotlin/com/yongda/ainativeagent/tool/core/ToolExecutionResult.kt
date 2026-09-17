package com.yongda.ainativeagent.tool.core

/**
 * 一次工具执行的结构化结果。
 *
 * @param terminal 该结果是否为「本轮终态」：例如用户取消或权限被永久拒绝——继续让模型调用工具只会反复打扰
 *   用户。为 true 时 Agent Loop 应停止后续工具调用，直接进入自然语言收尾。仅对失败有意义。
 */
data class ToolExecutionResult(
    val contentJson: String,
    val ok: Boolean,
    val errorCode: String? = null,
    val terminal: Boolean = false,
)
