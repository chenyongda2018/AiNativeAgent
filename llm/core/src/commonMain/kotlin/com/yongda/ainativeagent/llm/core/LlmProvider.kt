package com.yongda.ainativeagent.llm.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 统一模型接口。业务层只依赖它，未来可替换 DeepSeek / OpenAI / Gemini / 本地模型。
 */
interface LlmProvider {

    /** provider 标识，用于日志 / trace / 路由。 */
    val name: String

    /**
     * 流式对话：按到达顺序逐段（通常是 token 级）发出增量文本。
     *
     * - 完整回复 = 所有增量拼接。
     * - 收集协程被取消即中断生成。
     * - 网络 / 协议错误以异常形式向上抛出，由调用方决定重试或降级。
     */
    fun streamChat(messages: List<ChatMessage>): Flow<String>

    /**
     * 带类型的流式对话：区分思考过程 [LlmChunk.Reasoning] 与正式回答 [LlmChunk.Content]。
     *
     * 默认实现把 [streamChat] 的纯文本一律当作 [LlmChunk.Content]，因此不支持思考模式的 provider
     * 无需改动；支持思考的 provider（如 DeepSeek 开启 thinking）应重写本方法以额外流出 [LlmChunk.Reasoning]。
     */
    fun streamChatDetailed(messages: List<ChatMessage>): Flow<LlmChunk> =
        streamChat(messages).map { LlmChunk.Content(it) }
}
