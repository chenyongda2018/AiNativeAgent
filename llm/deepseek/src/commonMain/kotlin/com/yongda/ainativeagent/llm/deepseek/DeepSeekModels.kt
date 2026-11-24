package com.yongda.ainativeagent.llm.deepseek

/**
 * DeepSeek 可选模型的元数据描述。
 *
 * [id] 直接对应 DeepSeek API `model` 字段。当前 [DeepSeekProvider] 默认开启思考模式
 * （`thinking = ThinkingConfig(type = "enabled")`），因此 [thinkingSupported] 与真实协议行为保持一致。
 */
data class DeepSeekModelInfo(
    val id: String,
    val name: String,
    val badge: String?,
    val tagline: String,
    val contextLimit: String,
    val thinkingSupported: Boolean = true,
)

/** DeepSeek 官方账号可用模型目录（以 `/models` 端点为准）。 */
object DeepSeekModels {
    val all: List<DeepSeekModelInfo> = listOf(
        DeepSeekModelInfo(
            id = "deepseek-flash",
            name = "DeepSeek Flash",
            badge = "推荐",
            tagline = "极速通用，1M 超长上下文，适合日常对话与写作",
            contextLimit = "1M",
            thinkingSupported = true,
        ),
        DeepSeekModelInfo(
            id = "deepseek-v4-pro",
            name = "DeepSeek V4 Pro",
            badge = "旗舰",
            tagline = "更强推理与代码能力，复杂任务首选",
            contextLimit = "1M",
            thinkingSupported = true,
        ),
    )

    val default: DeepSeekModelInfo = all.first()
}
