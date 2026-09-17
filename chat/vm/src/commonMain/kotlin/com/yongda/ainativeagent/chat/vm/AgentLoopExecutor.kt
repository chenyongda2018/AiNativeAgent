package com.yongda.ainativeagent.chat.vm

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProvider
import com.yongda.ainativeagent.llm.core.LlmRequest
import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolRegistry
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 有界、串行的多步 Agent Loop：面向 [ToolRegistry] 的工具集合而非某个具体工具。
 *
 * 每一步向模型发起一次带工具的请求；模型不再请求工具即结束（此时最终自然语言回答已流式发出）。
 * 收到工具调用则串行执行、把 Assistant(tool_calls) 与匹配 toolCallId 的 Tool Result 追加进历史，进入下一步，
 * 从而支持「先查询、再按结果更新/删除」的多轮工具编排。
 *
 * 护栏，防止失控、副作用重复与反复打扰：
 *  - **最大步数** [maxToolSteps]：达到上限后再发一次「禁用工具」的请求，强制模型给出自然语言收尾。
 *  - **禁止并行调用**：一步里出现多个 tool_calls 时不执行，逐个回填结构化错误，引导模型改为单次调用。
 *  - **禁止重复调用**：同名同参（对参数 JSON 做规范化比较，忽略键顺序）的调用在本轮内只执行一次，
 *    重复的回填结构化错误，避免重复写入等副作用。
 *  - **终态短路**：某个工具返回终态结果（如用户取消 / 权限被拒，[ToolExecutionResult.terminal]）后，
 *    禁用后续工具调用并强制自然语言收尾，避免模型在剩余步数里反复弹权限 / 确认框打扰用户。
 *
 * 未知工具、非法并行/重复、工具异常都以结构化 JSON 回填给模型继续推进；协程取消照常向上传播。
 */
class AgentLoopExecutor(
    private val provider: LlmProvider,
    private val registry: ToolRegistry,
    private val trace: ToolTrace = ToolTrace.None,
    private val maxToolSteps: Int = DEFAULT_MAX_TOOL_STEPS,
) : ChatTurnEngine {

    override fun run(history: List<ChatMessage>): Flow<LlmChunk> = flow {
        val working = history.toMutableList()
        val executedSignatures = mutableSetOf<String>()
        var step = 0
        var forceFinal = false

        while (true) {
            val toolsAllowed = step < maxToolSteps && !forceFinal
            if (toolsAllowed) trace.llmRequestStarted() else trace.llmFinalRequestStarted()

            val request = LlmRequest(
                messages = working.toList(),
                tools = if (toolsAllowed) registry.definitions() else emptyList(),
                allowToolCalls = toolsAllowed,
            )

            val toolCalls = mutableListOf<ToolCall>()
            val reasoning = StringBuilder()
            val content = StringBuilder()
            provider.stream(request).collect { chunk ->
                when (chunk) {
                    is LlmChunk.Reasoning -> {
                        reasoning.append(chunk.text)
                        emit(chunk)
                    }
                    is LlmChunk.Content -> {
                        content.append(chunk.text)
                        emit(chunk)
                    }
                    is LlmChunk.ToolCallReceived -> if (toolsAllowed) toolCalls.add(chunk.toolCall)
                }
            }

            // 没有工具调用 → 本步的内容即最终回答，结束循环。
            if (toolCalls.isEmpty()) return@flow

            val assistant = ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = content.toString(),
                reasoningContent = reasoning.toString().takeIf { it.isNotEmpty() },
                toolCalls = toolCalls,
            )
            working.add(assistant)

            val multiple = toolCalls.size > 1
            for (call in toolCalls) {
                trace.toolCallReceived(call.id, call.name, argumentsSummary(call.argumentsJson))
                val result = resolve(call, multiple, executedSignatures)
                trace.toolExecutionFinished(call.id, if (result.ok) "OK" else "ERROR", result.errorCode)
                if (result.terminal) forceFinal = true
                working.add(
                    ChatMessage(role = ChatMessage.Role.TOOL, content = result.contentJson, toolCallId = call.id),
                )
            }
            step++
        }
    }

    private suspend fun resolve(
        call: ToolCall,
        multiple: Boolean,
        executedSignatures: MutableSet<String>,
    ): ToolExecutionResult = when {
        multiple -> ToolResults.failure(
            ERROR_PARALLEL_TOOL_CALLS,
            "本轮不支持并行工具调用，请一次只调用一个工具，按需分多步进行",
        )

        registry.get(call.name) == null -> ToolResults.failure(
            ToolRegistry.ERROR_UNKNOWN_TOOL,
            "Unknown tool: ${call.name}",
        )

        !executedSignatures.add(signatureOf(call)) -> ToolResults.failure(
            ERROR_DUPLICATE_TOOL_CALL,
            "该工具已用相同参数调用过，请勿重复调用；如需推进请改变参数或直接作答",
        )

        else -> try {
            registry.execute(call)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ToolResults.failure(ERROR_TOOL_EXECUTION, throwable.message ?: "Tool execution failed")
        }
    }

    /**
     * 归一化「工具名 + 参数」为去重签名：把参数 JSON 递归规范化（对象键排序、数组顺序保留）后比较，
     * 使仅键顺序不同的等价调用判为重复；无法解析时退回去空白原文。
     */
    private fun signatureOf(call: ToolCall): String {
        val normalizedArgs = runCatching { canonicalize(json.parseToJsonElement(call.argumentsJson.trim())) }
            .getOrElse { call.argumentsJson.trim() }
        return "${call.name}::$normalizedArgs"
    }

    private fun canonicalize(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
                "${JsonPrimitive(key)}:${canonicalize(value)}"
            }
        is JsonArray -> element.joinToString(separator = ",", prefix = "[", postfix = "]") { canonicalize(it) }
        is JsonPrimitive -> element.toString()
    }

    private fun argumentsSummary(argumentsJson: String): String = "chars=${argumentsJson.length}"

    companion object {
        const val DEFAULT_MAX_TOOL_STEPS: Int = 6

        const val ERROR_PARALLEL_TOOL_CALLS = "PARALLEL_TOOL_CALLS_NOT_SUPPORTED"
        const val ERROR_DUPLICATE_TOOL_CALL = "DUPLICATE_TOOL_CALL"
        const val ERROR_TOOL_EXECUTION = "TOOL_EXECUTION_ERROR"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
