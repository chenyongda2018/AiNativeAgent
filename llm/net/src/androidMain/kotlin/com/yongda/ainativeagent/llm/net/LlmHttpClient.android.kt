package com.yongda.ainativeagent.llm.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging

actual fun createLlmHttpClient(
    configure: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(OkHttp) {
    installLlmDefaults()
    install(Logging) {
        level = LogLevel.INFO
    }
    configure()
}
