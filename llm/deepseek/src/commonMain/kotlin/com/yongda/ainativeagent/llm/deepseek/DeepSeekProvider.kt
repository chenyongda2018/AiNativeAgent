package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmConfig
import com.yongda.ainativeagent.llm.core.LlmProvider
import com.yongda.ainativeagent.llm.net.LlmJson
import com.yongda.ainativeagent.llm.net.collectSseData
import com.yongda.ainativeagent.llm.net.createLlmHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * DeepSeek 云端 [LlmProvider] 实现（OpenAI-compatible，SSE 流式）。
 *
 * @param client 可注入自定义 HttpClient（测试 / 复用连接池）；默认用 [createLlmHttpClient]。
 */
class DeepSeekProvider(
    private val config: LlmConfig,
    private val client: HttpClient = createLlmHttpClient(),
) : LlmProvider {

    override val name: String = "deepseek"

    override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
        val request = ChatCompletionRequest(
            model = config.model,
            messages = messages.map { RequestMessage(it.role.toWire(), it.content) },
            stream = true,
        )
        client.preparePost("${config.baseUrl}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("DeepSeek 请求失败: ${response.status} ${response.bodyAsText()}")
            }
            response.collectSseData { data ->
                val chunk = LlmJson.decodeFromString<ChatCompletionChunk>(data)
                chunk.choices.firstOrNull()?.delta?.content?.let { emit(it) }
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.deepseek.com"
        const val DEFAULT_MODEL: String = "deepseek-chat"

        /** 便捷构造：只给 apiKey，baseUrl / model 用默认值。 */
        fun withApiKey(
            apiKey: String,
            baseUrl: String = DEFAULT_BASE_URL,
            model: String = DEFAULT_MODEL,
        ): DeepSeekProvider = DeepSeekProvider(LlmConfig(apiKey, baseUrl, model))
    }
}
