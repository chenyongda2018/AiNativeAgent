package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.ChatMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)

/** 思考模式开关（DeepSeek V4：type=enabled 时流式响应会先流出 reasoning_content）。 */
@Serializable
internal data class ThinkingConfig(
    val type: String = "enabled",
)

@Serializable
internal data class RequestMessage(
    val role: String,
    val content: String,
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
)

/** ChatMessage.Role → OpenAI-compatible 协议里的 role 字符串。 */
internal fun ChatMessage.Role.toWire(): String = when (this) {
    ChatMessage.Role.SYSTEM -> "system"
    ChatMessage.Role.USER -> "user"
    ChatMessage.Role.ASSISTANT -> "assistant"
}
