package com.yongda.ainativeagent.agent.runtime

import com.yongda.ainativeagent.llm.core.ChatMessage

/**
 * 一轮 Agent 执行过程向表现层发出的领域事件流。
 *
 * 除流式文本增量（[ReasoningDelta] / [ContentDelta]）外，显式暴露工具调用生命周期
 * （[ToolCallStarted] / [ToolCallFinished]）与结构化消息的原子提交（[MessagesCommitted]），
 * 让 UI 可观测工具进度且历史状态不会因中途取消留下孤立的 tool_calls。
 */
sealed interface AgentTurnEvent {

    /** 思考/推理过程增量，展示在思考区，不属于最终答案。 */
    data class ReasoningDelta(val text: String) : AgentTurnEvent

    /** 正式回答增量，拼接即为本轮最终自然语言回复。 */
    data class ContentDelta(val text: String) : AgentTurnEvent

    /** 开始执行单个工具调用。 */
    data class ToolCallStarted(val callId: String, val toolName: String) : AgentTurnEvent

    /** 单个工具调用执行完成。 */
    data class ToolCallFinished(
        val callId: String,
        val toolName: String,
        val ok: Boolean,
        val errorCode: String? = null,
    ) : AgentTurnEvent

    /**
     * 原子提交本步已完整配对的消息（例如 `[ASSISTANT(toolCalls), TOOL...]` 或最终 `[ASSISTANT]`）。
     */
    data class MessagesCommitted(val messages: List<ChatMessage>) : AgentTurnEvent
}
