package com.yongda.ainativeagent.llm.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 各云端 provider 共享的 JSON 配置：容忍未知字段、宽松解析、编码默认值。 */
val LlmJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    // 关键：默认值也要序列化，否则 `stream = true` 等带默认值的字段会被省略。
    encodeDefaults = true
}

/**
 * 创建 LLM 用的 HttpClient。engine 由各平台 [actual] 提供（Android=OkHttp），
 * 公共插件在 [installLlmDefaults] 里统一装配。
 *
 * @param configure 额外的按需配置（超时、重试等），在默认插件之后应用。
 */
expect fun createLlmHttpClient(
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient

/** 装配所有 provider 通用的插件：目前是 JSON 内容协商。 */
fun HttpClientConfig<*>.installLlmDefaults(json: Json = LlmJson) {
    install(ContentNegotiation) {
        json(json)
    }
}
