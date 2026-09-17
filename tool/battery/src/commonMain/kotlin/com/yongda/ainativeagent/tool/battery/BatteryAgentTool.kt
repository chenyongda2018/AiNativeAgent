package com.yongda.ainativeagent.tool.battery

import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.tool.core.AgentTool
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 把既有的只读 [BatteryLevelTool] 适配为通用 [AgentTool]，纳入 [com.yongda.ainativeagent.tool.core.ToolRegistry]
 * 与多步 Agent Loop 统一调度。校验空参数、转发执行、兜底异常为结构化失败，取消照常向上传播。
 */
class BatteryAgentTool(
    private val delegate: BatteryLevelTool,
) : AgentTool {

    override val definition: ToolDefinition = BatteryToolContract.definition

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        if (!isValidEmptyArguments(argumentsJson)) {
            return ToolResults.failure(ERROR_INVALID_ARGUMENTS, "Arguments must be an empty JSON object")
        }
        return try {
            delegate.execute(argumentsJson)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ToolResults.failure(ERROR_TOOL_EXECUTION, throwable.message ?: "Tool execution failed")
        }
    }

    private fun isValidEmptyArguments(argumentsJson: String): Boolean {
        val trimmed = argumentsJson.trim()
        if (trimmed.isEmpty()) return true
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return false
        return element is JsonObject && element.isEmpty()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val ERROR_INVALID_ARGUMENTS = "INVALID_ARGUMENTS"
        const val ERROR_TOOL_EXECUTION = "TOOL_EXECUTION_ERROR"
    }
}
