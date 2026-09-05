package com.yongda.ainativeagent.llm.core

/**
 * LLM provider 的通用配置。业务层只依赖此抽象，切换 DeepSeek / OpenAI / Gemini 时仅换 provider + config。
 *
 * @param apiKey    云端鉴权用的密钥；不要硬编码，由外部注入（环境变量 / BuildConfig / 设置项）。
 * @param baseUrl   服务根地址，不含具体路径。
 * @param model     模型名。
 */
data class LlmConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
)
