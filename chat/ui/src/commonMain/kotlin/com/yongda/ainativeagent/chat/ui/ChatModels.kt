package com.yongda.ainativeagent.chat.ui

import androidx.compose.runtime.Immutable

/**
 * 纯 UI 层的模型展示描述。具体模型列表由上层组合根注入 [ChatUiState.availableModels]，
 * 本模块不绑定任何特定 LLM 供应商。
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
