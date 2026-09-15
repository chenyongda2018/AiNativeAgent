package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProtocolException
import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.llm.net.LlmJson
import com.yongda.ainativeagent.llm.net.collectSseData
import io.ktor.utils.io.ByteReadChannel

/**
 * 解析 DeepSeek SSE 流，逐段回调 [LlmChunk]。reasoning/content 到达即回调；工具调用参数可能被拆到多个
 * delta，按 index 聚合 id、名称与参数片段。
 *
 * 只有在收到明确的 `finish_reason == "tool_calls"` 完成标记后才产出聚合完整的
 * [LlmChunk.ToolCallReceived]；若聚合到片段却在没有该标记时结束（如 [DONE] 或连接中断 EOF），视为不完整，
 * 抛出 [LlmProtocolException]，绝不静默伪造或执行半截调用。缺 id 或名称同样视为协议错误。
 */
internal suspend fun ByteReadChannel.collectDeepSeekChunks(onChunk: suspend (LlmChunk) -> Unit) {
    val builders = LinkedHashMap<Int, ToolCallBuilder>()
    var finishReason: String? = null
    collectSseData { data ->
        val choice = LlmJson.decodeFromString<ChatCompletionChunk>(data).choices.firstOrNull()
            ?: return@collectSseData
        choice.finishReason?.let { finishReason = it }
        val delta = choice.delta
        delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { onChunk(LlmChunk.Reasoning(it)) }
        delta.content?.takeIf { it.isNotEmpty() }?.let { onChunk(LlmChunk.Content(it)) }
        delta.toolCalls?.forEach { fragment ->
            val builder = builders.getOrPut(fragment.index) { ToolCallBuilder() }
            fragment.id?.let { builder.id = it }
            fragment.function?.name?.let { builder.name = it }
            fragment.function?.arguments?.let { builder.arguments.append(it) }
        }
    }
    if (builders.isEmpty()) return
    if (finishReason != FINISH_REASON_TOOL_CALLS) {
        throw LlmProtocolException(
            "Incomplete tool call: stream ended with finish_reason=$finishReason",
        )
    }
    builders.values.forEach { onChunk(LlmChunk.ToolCallReceived(it.build())) }
}

private const val FINISH_REASON_TOOL_CALLS = "tool_calls"

private class ToolCallBuilder {
    var id: String? = null
    var name: String? = null
    val arguments = StringBuilder()

    fun build(): ToolCall {
        val resolvedId = id?.takeIf { it.isNotBlank() }
            ?: throw LlmProtocolException("Tool call is missing id")
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: throw LlmProtocolException("Tool call is missing function name")
        return ToolCall(id = resolvedId, name = resolvedName, argumentsJson = arguments.toString())
    }
}
