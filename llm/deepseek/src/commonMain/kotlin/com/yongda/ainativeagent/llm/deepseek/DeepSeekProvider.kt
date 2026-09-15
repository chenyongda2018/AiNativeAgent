package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmConfig
import com.yongda.ainativeagent.llm.core.LlmProvider
import com.yongda.ainativeagent.llm.core.LlmRequest
import com.yongda.ainativeagent.llm.net.createLlmHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * DeepSeek 云端 [LlmProvider] 实现（OpenAI-compatible，SSE 流式）。默认开启思考模式：
 * 流式响应先流出 reasoning_content（→ [LlmChunk.Reasoning]），再流出 content（→ [LlmChunk.Content]）。
 * 请求携带工具时，参数聚合完整后额外流出 [LlmChunk.ToolCallReceived]。
 *
 * @param client 可注入自定义 HttpClient（测试 / 复用连接池）；默认用 [createLlmHttpClient]。
 */
class DeepSeekProvider(
    private val config: LlmConfig,
    private val client: HttpClient = createLlmHttpClient(),
) : LlmProvider {

    override val name: String = "deepseek"

    /** 纯文本回答流：复用 [streamChatDetailed]，仅保留正式回答部分。 */
    override fun streamChat(messages: List<ChatMessage>): Flow<String> =
        streamChatDetailed(messages).mapNotNull { (it as? LlmChunk.Content)?.text }

    override fun streamChatDetailed(messages: List<ChatMessage>): Flow<LlmChunk> =
        stream(LlmRequest(messages))

    override fun stream(request: LlmRequest): Flow<LlmChunk> = flow {
        val toolSpecs = request.tools
            .takeIf { it.isNotEmpty() && request.allowToolCalls }
            ?.map { it.toWire() }
        val body = ChatCompletionRequest(
            model = config.model,
            messages = request.messages.map { it.toWire() },
            stream = true,
            thinking = ThinkingConfig(type = "enabled"),
            tools = toolSpecs,
            toolChoice = if (toolSpecs != null) "auto" else null,
        )
        client.preparePost("${config.baseUrl.trimEnd('/')}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            contentType(ContentType.Application.Json)
            setBody(body)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("DeepSeek 请求失败: ${response.status} ${response.bodyAsText()}")
            }
            response.bodyAsChannel().collectDeepSeekChunks { emit(it) }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.deepseek.com"

        /** 账号 /models 端点当前返回的推荐模型（截至 2026-09；旧的 deepseek-chat 已下线）。 */
        const val DEFAULT_MODEL: String = "deepseek-flash"

        /** 便捷构造：只给 apiKey，baseUrl / model 用默认值。 */
        fun withApiKey(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            model: String = DEFAULT_MODEL,
        ): DeepSeekProvider = DeepSeekProvider(LlmConfig(apiKey, baseUrl, model))
    }
}
