package com.yongda.ainativeagent.chat.vm

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProvider
import com.yongda.ainativeagent.llm.core.LlmProtocolException
import com.yongda.ainativeagent.llm.core.LlmRequest
import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.tool.battery.BatteryLevelTool
import com.yongda.ainativeagent.tool.battery.BatteryToolContract
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 单工具调用编排：最多一次工具执行、最多两次 LLM 请求。
 *
 * 第一次请求携带唯一工具 [BatteryToolContract]；若无工具调用则直接结束（普通聊天只发一次请求）。
 * 收到工具调用后严格校验（唯一、名称、空参数），执行电量工具，追加 Assistant Tool Call 与匹配
 * toolCallId 的 Tool Result，再发起不带工具的第二次请求生成最终自然语言回答。
 *
 * 非法情形（未知工具、非法参数、多个调用、工具异常、第二次请求再次调用工具）一律安全失败为结构化结果，
 * 不重复或越权执行平台 API。协程取消照常向上传播。
 */
class SingleToolChatExecutor(
    private val provider: LlmProvider,
    private val batteryTool: BatteryLevelTool,
    private val trace: ToolTrace = ToolTrace.None,
) : ChatTurnEngine {

    override fun run(history: List<ChatMessage>): Flow<LlmChunk> = flow {
        trace.llmRequestStarted()
        val firstRequest = LlmRequest(
            messages = history,
            tools = listOf(BatteryToolContract.definition),
            allowToolCalls = true,
        )
        val toolCalls = mutableListOf<ToolCall>()
        val reasoning = StringBuilder()
        provider.stream(firstRequest).collect { chunk ->
            when (chunk) {
                is LlmChunk.Reasoning -> {
                    reasoning.append(chunk.text)
                    emit(chunk)
                }
                is LlmChunk.Content -> emit(chunk)
                is LlmChunk.ToolCallReceived -> toolCalls.add(chunk.toolCall)
            }
        }
        if (toolCalls.isEmpty()) return@flow

        val multiple = toolCalls.size > 1
        val assistantMessage = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            reasoningContent = reasoning.toString().takeIf { it.isNotEmpty() },
            toolCalls = toolCalls,
        )
        val toolMessages = toolCalls.map { call ->
            trace.toolCallReceived(call.id, call.name, argumentsSummary(call.argumentsJson))
            val result = resolve(call, multiple)
            trace.toolExecutionFinished(
                toolCallId = call.id,
                status = if (result.ok) "OK" else "ERROR",
                errorCode = result.errorCode,
            )
            ChatMessage(
                role = ChatMessage.Role.TOOL,
                content = result.contentJson,
                toolCallId = call.id,
            )
        }

        trace.llmFinalRequestStarted()
        val secondRequest = LlmRequest(
            messages = history + assistantMessage + toolMessages,
            tools = emptyList(),
            allowToolCalls = false,
        )
        provider.stream(secondRequest).collect { chunk ->
            when (chunk) {
                is LlmChunk.Reasoning -> emit(chunk)
                is LlmChunk.Content -> emit(chunk)
                is LlmChunk.ToolCallReceived -> throw LlmProtocolException(
                    "Second request must not produce another tool call",
                )
            }
        }
    }

    private suspend fun resolve(call: ToolCall, multiple: Boolean): ToolExecutionResult = when {
        multiple -> ToolResults.failure(
            ERROR_MULTIPLE_TOOL_CALLS,
            "Only a single tool call per turn is supported",
        )

        call.name != BatteryToolContract.NAME -> ToolResults.failure(
            ERROR_UNKNOWN_TOOL,
            "Unknown tool: ${call.name}",
        )

        !isValidEmptyArguments(call.argumentsJson) -> ToolResults.failure(
            ERROR_INVALID_ARGUMENTS,
            "Arguments must be an empty JSON object",
        )

        else -> try {
            batteryTool.execute(call.argumentsJson)
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

    private fun argumentsSummary(argumentsJson: String): String = "chars=${argumentsJson.length}"

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val ERROR_UNKNOWN_TOOL = "UNKNOWN_TOOL"
        const val ERROR_INVALID_ARGUMENTS = "INVALID_ARGUMENTS"
        const val ERROR_MULTIPLE_TOOL_CALLS = "MULTIPLE_TOOL_CALLS_NOT_SUPPORTED"
        const val ERROR_TOOL_EXECUTION = "TOOL_EXECUTION_ERROR"
    }
}
