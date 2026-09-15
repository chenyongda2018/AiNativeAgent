package com.yongda.ainativeagent.chat.vm

/**
 * 最小控制台 [ToolTrace]：仅打印脱敏摘要（不含 API Key / 系统提示词 / 完整请求体 / 工具参数原文）。
 */
class PrintlnToolTrace(private val tag: String = "ToolTrace") : ToolTrace {

    override fun llmRequestStarted() {
        println("[$tag] LLM_REQUEST_STARTED")
    }

    override fun toolCallReceived(toolCallId: String, toolName: String, argumentsSummary: String) {
        println("[$tag] TOOL_CALL_RECEIVED id=$toolCallId tool=$toolName args=$argumentsSummary")
    }

    override fun toolExecutionFinished(toolCallId: String, status: String, errorCode: String?) {
        println("[$tag] TOOL_EXECUTION_FINISHED id=$toolCallId status=$status errorCode=$errorCode")
    }

    override fun llmFinalRequestStarted() {
        println("[$tag] LLM_FINAL_REQUEST_STARTED")
    }
}
