package com.yongda.ainativeagent.llm.net

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SseTest {

    @Test
    fun emitsDataEventsAndStopsAtDone() = runBlocking {
        val values = mutableListOf<String>()

        ByteReadChannel(
            "data: {\"content\":\"你\"}\n\n" +
                ": keep-alive\n\n" +
                "event: message\ndata: {\"content\":\"好\"}\n\n" +
                "data: [DONE]\n\n" +
                "data: ignored\n\n",
        ).collectSseData(values::add)

        assertEquals(listOf("{\"content\":\"你\"}", "{\"content\":\"好\"}"), values)
    }

    @Test
    fun joinsMultipleDataLinesInOneEvent() = runBlocking {
        val values = mutableListOf<String>()

        ByteReadChannel("data: first\ndata: second\n\n")
            .collectSseData(values::add)

        assertEquals(listOf("first\nsecond"), values)
    }

    @Test
    fun dispatchesLastEventAtEndOfStream() = runBlocking {
        val values = mutableListOf<String>()

        ByteReadChannel("data: final")
            .collectSseData(values::add)

        assertEquals(listOf("final"), values)
    }
}
