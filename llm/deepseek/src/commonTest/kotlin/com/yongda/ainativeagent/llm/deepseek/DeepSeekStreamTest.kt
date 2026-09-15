package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.LlmChunk
import com.yongda.ainativeagent.llm.core.LlmProtocolException
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeepSeekStreamTest {

    private val toolCallsFinish =
        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\n"

    private fun collect(sse: String): List<LlmChunk> {
        val chunks = mutableListOf<LlmChunk>()
        runBlocking { ByteReadChannel(sse).collectDeepSeekChunks { chunks.add(it) } }
        return chunks
    }

    @Test
    fun parsesSingleToolCallNameAndArguments() {
        val chunks = collect(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\"," +
                "\"type\":\"function\",\"function\":{\"name\":\"get_battery_level\",\"arguments\":\"{}\"}}]}}]}\n\n" +
                toolCallsFinish +
                "data: [DONE]\n\n",
        )

        val call = (chunks.single() as LlmChunk.ToolCallReceived).toolCall
        assertEquals("call_1", call.id)
        assertEquals("get_battery_level", call.name)
        assertEquals("{}", call.argumentsJson)
    }

    @Test
    fun aggregatesIdNameAndArgumentsSplitAcrossDeltas() {
        val chunks = collect(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_9\"," +
                "\"function\":{\"name\":\"get_battery_level\",\"arguments\":\"\"}}]}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                "\"function\":{\"arguments\":\"{\\\"a\\\":\"}}]}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                "\"function\":{\"arguments\":\"1}\"}}]}}]}\n\n" +
                toolCallsFinish +
                "data: [DONE]\n\n",
        )

        val call = (chunks.single() as LlmChunk.ToolCallReceived).toolCall
        assertEquals("call_9", call.id)
        assertEquals("get_battery_level", call.name)
        assertEquals("{\"a\":1}", call.argumentsJson)
    }

    @Test
    fun contentAndReasoningStreamAsBeforeAndToolCallComesLast() {
        val chunks = collect(
            "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"想一下\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c\"," +
                "\"function\":{\"name\":\"t\",\"arguments\":\"{}\"}}]}}]}\n\n" +
                toolCallsFinish +
                "data: [DONE]\n\n",
        )

        assertEquals(LlmChunk.Reasoning("想一下"), chunks[0])
        assertEquals(LlmChunk.Content("你好"), chunks[1])
        assertTrue(chunks[2] is LlmChunk.ToolCallReceived)
    }

    @Test
    fun aggregatesTwoDistinctToolCallsByIndex() {
        val chunks = collect(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[" +
                "{\"index\":0,\"id\":\"a\",\"function\":{\"name\":\"x\",\"arguments\":\"{}\"}}," +
                "{\"index\":1,\"id\":\"b\",\"function\":{\"name\":\"y\",\"arguments\":\"{}\"}}]}}]}\n\n" +
                toolCallsFinish +
                "data: [DONE]\n\n",
        )

        val ids = chunks.map { (it as LlmChunk.ToolCallReceived).toolCall.id }
        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun missingIdIsAProtocolError() {
        assertFailsWith<LlmProtocolException> {
            collect(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                    "\"function\":{\"name\":\"t\",\"arguments\":\"{}\"}}]}}]}\n\n" +
                    toolCallsFinish +
                    "data: [DONE]\n\n",
            )
        }
    }

    @Test
    fun missingNameIsAProtocolError() {
        assertFailsWith<LlmProtocolException> {
            collect(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c\"," +
                    "\"function\":{\"arguments\":\"{}\"}}]}}]}\n\n" +
                    toolCallsFinish +
                    "data: [DONE]\n\n",
            )
        }
    }

    @Test
    fun toolCallWithoutFinishReasonIsProtocolError() {
        assertFailsWith<LlmProtocolException> {
            collect(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c\"," +
                    "\"function\":{\"name\":\"get_battery_level\",\"arguments\":\"{}\"}}]}}]}\n\n" +
                    "data: [DONE]\n\n",
            )
        }
    }

    @Test
    fun interruptedToolCallStreamWithoutDoneIsProtocolError() {
        assertFailsWith<LlmProtocolException> {
            collect(
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c\"," +
                    "\"function\":{\"name\":\"get_battery_level\",\"arguments\":\"{\"}}]}}]}\n\n",
            )
        }
    }

    @Test
    fun plainAnswerProducesNoToolCall() {
        val chunks = collect(
            "data: {\"choices\":[{\"delta\":{\"content\":\"仅文本\"},\"finish_reason\":\"stop\"}]}\n\n" +
                "data: [DONE]\n\n",
        )

        assertEquals(listOf(LlmChunk.Content("仅文本")), chunks)
    }
}
