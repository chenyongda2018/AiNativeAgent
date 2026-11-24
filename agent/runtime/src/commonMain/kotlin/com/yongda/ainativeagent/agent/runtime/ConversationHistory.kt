package com.yongda.ainativeagent.agent.runtime

import com.yongda.ainativeagent.llm.core.ChatMessage

/**
 * 一次用户交互轮次：包含用户消息以及模型在该轮产出的完整消息轨迹
 * （如零到多组 `ASSISTANT(tool_calls) + TOOL` 中间步骤与最终 `ASSISTANT` 回复）。
 */
data class ConversationTurn(
    val userMessage: ChatMessage,
    val responseMessages: List<ChatMessage> = emptyList(),
)

/**
 * 不可变的领域级会话历史，与 UI 展示消息解耦。
 *
 * 支持：
 * - 跨轮保留完整的工具调用与结果轨迹（避免后续轮次丢失 `tool_calls` / `tool` 上下文）；
 * - 按轮整体回滚（[rollbackLastTurnResponse]），让失败重试不会残留半截工具调用或失败回复；
 * - 不可变值语义，便于在 `StateFlow` 中无锁 CAS 原子更新。
 */
data class ConversationHistory(
    val turns: List<ConversationTurn> = emptyList(),
) {

    /** 开启新的一轮对话，追加用户消息。 */
    fun startTurn(userText: String): ConversationHistory = copy(
        turns = turns + ConversationTurn(
            userMessage = ChatMessage(role = ChatMessage.Role.USER, content = userText),
        ),
    )

    /** 向当前最新一轮追加模型产出的消息片段（如工具调用对或最终助手回复）。 */
    fun appendToCurrentTurn(messages: List<ChatMessage>): ConversationHistory {
        if (turns.isEmpty() || messages.isEmpty()) return this
        val last = turns.last()
        return copy(
            turns = turns.dropLast(1) + last.copy(responseMessages = last.responseMessages + messages),
        )
    }

    /** 回滚最后一轮的模型产出（含其中间工具消息与失败回复），保留该轮用户提问与此前全部轮次。 */
    fun rollbackLastTurnResponse(): ConversationHistory {
        if (turns.isEmpty()) return this
        val last = turns.last()
        return if (last.responseMessages.isEmpty()) {
            this
        } else {
            copy(turns = turns.dropLast(1) + last.copy(responseMessages = emptyList()))
        }
    }

    /** 拼装发给 [com.yongda.ainativeagent.llm.core.LlmProvider] 的完整协议消息序列。 */
    fun toLlmMessages(systemPrompt: String): List<ChatMessage> = buildList {
        add(ChatMessage(role = ChatMessage.Role.SYSTEM, content = systemPrompt))
        turns.forEach { turn ->
            add(turn.userMessage)
            addAll(turn.responseMessages)
        }
    }

    /** 清空全部会话历史。 */
    fun clear(): ConversationHistory = ConversationHistory()
}
