package com.yongda.ainativeagent.llm.core

/**
 * 流式对话的一段增量。区分「思考过程」与「正式回答」两类内容：
 * 支持思考模式（如 DeepSeek 的 reasoning_content）的模型会先流出若干 [Reasoning]，再流出 [Content]。
 */
sealed interface LlmChunk {
    val text: String

    /** 思考/推理过程增量，展示在思考区，不属于最终答案。 */
    data class Reasoning(override val text: String) : LlmChunk

    /** 正式回答增量，拼接即为完整回复。 */
    data class Content(override val text: String) : LlmChunk
}
