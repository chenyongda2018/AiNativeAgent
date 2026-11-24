package com.yongda.ainativeagent.agent.runtime

import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 一轮 Agent 对话的运行时编排入口：给定完整历史，产出 [AgentTurnEvent] 流。
 * 实现可以是纯流式透传（[asPlainTurnEngine]），也可以是多步有界工具循环（[AgentLoopExecutor]）。
 */
fun interface AgentTurnEngine {
    fun run(history: List<ChatMessage>): Flow<AgentTurnEvent>
}

/** 把不带工具的 [LlmProvider] 包装为纯文本对话 [AgentTurnEngine]。 */
fun LlmProvider.asPlainTurnEngine(): AgentTurnEngine = AgentTurnEngine { history ->
    flow {
        val reasoning = StringBuilder()
        val content = StringBuilder()
        streamChatDetailed(history).collect { chunk ->
            when (chunk) {
                is LlmChunk.Reasoning -> {
                    reasoning.append(chunk.text)
                    emit(AgentTurnEvent.ReasoningDelta(chunk.text))
                }
                is LlmChunk.Content -> {
                    content.append(chunk.text)
                    emit(AgentTurnEvent.ContentDelta(chunk.text))
                }
                is LlmChunk.ToolCallReceived -> Unit
            }
        }
        emit(
            AgentTurnEvent.MessagesCommitted(
                listOf(
                    ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        content = content.toString(),
                        reasoningContent = reasoning.toString().takeIf { it.isNotEmpty() },
                    ),
                ),
            ),
        )
    }
}
