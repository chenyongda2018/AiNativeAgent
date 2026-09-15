package com.yongda.ainativeagent.llm.core

/**
 * 流式对话的一段增量。区分「思考过程」「正式回答」与「工具调用」：
 * 支持思考模式（如 DeepSeek 的 reasoning_content）的模型会先流出若干 [Reasoning]，再流出 [Content]；
 * 支持工具调用的模型在流结束、参数聚合完整后流出 [ToolCallReceived]。
 */
sealed interface LlmChunk {

    /** 思考/推理过程增量，展示在思考区，不属于最终答案。 */
    data class Reasoning(val text: String) : LlmChunk

    /** 正式回答增量，拼接即为完整回复。 */
    data class Content(val text: String) : LlmChunk

    /** 聚合完成的一次工具调用（id、名称、完整参数 JSON 均已补齐）。 */
    data class ToolCallReceived(val toolCall: ToolCall) : LlmChunk
}
