package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.ToolDefinition
import com.yongda.ainativeagent.llm.net.LlmJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DeepSeek /chat/completions 的请求 / 流式响应 DTO（OpenAI-compatible）。
 * 仅声明本阶段用到的字段，其余由 [com.yongda.ainativeagent.llm.net.LlmJson] 的 ignoreUnknownKeys 忽略。
 */
@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val stream: Boolean = true,
    val thinking: ThinkingConfig? = null,
    val tools: List<ToolSpec>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
)

/** 思考模式开关（DeepSeek V4：type=enabled 时流式响应会先流出 reasoning_content）。 */
@Serializable
internal data class ThinkingConfig(
    val type: String = "enabled",
)

@Serializable
internal data class RequestMessage(
    val role: String,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class WireToolCall(
    val id: String,
    val type: String = "function",
    val function: WireFunction,
)

@Serializable
internal data class WireFunction(
    val name: String,
    val arguments: String,
)

@Serializable
internal data class ToolSpec(
    val type: String = "function",
    val function: FunctionSpec,
)

@Serializable
internal data class FunctionSpec(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)

@Serializable
internal data class ChatCompletionChunk(
    val choices: List<Choice> = emptyList(),
)

@Serializable
internal data class Choice(
    val delta: Delta = Delta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class Delta(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<DeltaToolCall>? = null,
)

@Serializable
internal data class DeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: DeltaFunction? = null,
)

@Serializable
internal data class DeltaFunction(
    val name: String? = null,
    val arguments: String? = null,
)

/** ChatMessage.Role → OpenAI-compatible 协议里的 role 字符串。 */
internal fun ChatMessage.Role.toWire(): String = when (this) {
    ChatMessage.Role.SYSTEM -> "system"
    ChatMessage.Role.USER -> "user"
    ChatMessage.Role.ASSISTANT -> "assistant"
    ChatMessage.Role.TOOL -> "tool"
}

internal fun ChatMessage.toWire(): RequestMessage = RequestMessage(
    role = role.toWire(),
    content = content.takeIf { it.isNotEmpty() || toolCalls.isEmpty() },
    reasoningContent = reasoningContent?.takeIf { it.isNotEmpty() },
    toolCalls = toolCalls.takeIf { it.isNotEmpty() }
        ?.map { WireToolCall(id = it.id, function = WireFunction(it.name, it.argumentsJson)) },
    toolCallId = toolCallId,
)

internal fun ToolDefinition.toWire(): ToolSpec = ToolSpec(
    function = FunctionSpec(
        name = name,
        description = description,
        parameters = LlmJson.parseToJsonElement(parametersJsonSchema),
    ),
)
