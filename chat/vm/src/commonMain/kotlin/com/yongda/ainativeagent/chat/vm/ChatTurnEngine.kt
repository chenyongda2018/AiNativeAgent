package com.yongda.ainativeagent.chat.vm

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import kotlinx.coroutines.flow.Flow

/**
 * 一轮聊天的编排入口：给定历史，产出 [LlmChunk] 流。实现可以是纯 passthrough（无工具），
 * 也可以是 [SingleToolChatExecutor]（单工具两段式）。ViewModel 只依赖这层抽象。
 */
fun interface ChatTurnEngine {
    fun run(history: List<ChatMessage>): Flow<LlmChunk>
}
