package com.yongda.ainativeagent.chat.vm

/**
 * 单工具调用链路的最小 trace。只记录脱敏摘要：不含 API Key、系统提示词、完整请求体；工具参数只记摘要。
 */
interface ToolTrace {

    fun llmRequestStarted()

    fun toolCallReceived(toolCallId: String, toolName: String, argumentsSummary: String)

    fun toolExecutionFinished(toolCallId: String, status: String, errorCode: String? = null)

    fun llmFinalRequestStarted()

    object None : ToolTrace {
        override fun llmRequestStarted() {}
        override fun toolCallReceived(toolCallId: String, toolName: String, argumentsSummary: String) {}
        override fun toolExecutionFinished(toolCallId: String, status: String, errorCode: String?) {}
        override fun llmFinalRequestStarted() {}
    }
}
