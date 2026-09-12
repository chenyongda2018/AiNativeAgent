package com.yongda.ainativeagent.chat.ui

import androidx.compose.runtime.Immutable

/**
 * 可选模型的单一数据源，UI（选择器 / 顶栏 pill / 空态）与 VM（装配 provider）共用。
 *
 * [id] 直接就是 DeepSeek API `model` 字段要传的字符串，所以选择即可直接喂给 provider，无需额外映射。
 * 列表以 DeepSeek 账号 `/models` 端点返回的真实模型为准（截至 2026-09：仅 deepseek-flash / deepseek-v4-pro；
 * 旧的 deepseek-chat / deepseek-reasoner 已下线）。“深度思考”在新版是请求参数而非独立模型，本阶段未接入，
 * 故 [thinkingSupported] 统一为 false，避免暗示未实现的能力。
 */
@Immutable
data class ChatModel(
    val id: String,
    val name: String,
    val badge: String?,
    val tagline: String,
    val contextLimit: String,
    val thinkingSupported: Boolean = false,
)

object ChatModels {
    val all: List<ChatModel> = listOf(
        ChatModel(
            id = "deepseek-flash",
            name = "DeepSeek Flash",
            badge = "推荐",
            tagline = "极速通用，1M 超长上下文，适合日常对话与写作",
            contextLimit = "1M",
        ),
        ChatModel(
            id = "deepseek-v4-pro",
            name = "DeepSeek V4 Pro",
            badge = "旗舰",
            tagline = "更强推理与代码能力，复杂任务首选",
            contextLimit = "1M",
        ),
    )

    /** 默认模型：DeepSeek 官方推荐的 flash。 */
    val default: ChatModel = all.first()

    fun byId(id: String): ChatModel? = all.firstOrNull { it.id == id }

    /** id → 展示名；未知 id 原样返回，保证 UI 不会显示空白。 */
    fun nameOf(id: String): String = byId(id)?.name ?: id
}
