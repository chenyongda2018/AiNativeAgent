package com.yongda.ainativeagent.chat.vm

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class StreamTextPacerTest {

    @Test
    fun emitsCompleteTextWithoutSplittingSurrogatePairs() = runBlocking {
        val emissions = flowOf("你好🙂abc")
            .paceTextForUi(
                frameInterval = 0.milliseconds,
                catchUpFrames = 1,
                maxCodePointsPerFrame = 1,
            )
            .toList()

        assertEquals(
            listOf("你", "你好", "你好🙂", "你好🙂a", "你好🙂ab", "你好🙂abc"),
            emissions,
        )
    }

    @Test
    fun combinesAllNetworkChunksWithoutDroppingContent() = runBlocking {
        val emissions = flowOf("第一", "批", "\n", "second")
            .paceTextForUi(frameInterval = 0.milliseconds)
            .toList()

        assertEquals("第一批\nsecond", emissions.last())
    }

    @Test
    fun largeChunkIsRevealedAcrossMultipleFrames() = runBlocking {
        val emissions = flowOf("abcdef")
            .paceTextForUi(
                frameInterval = 0.milliseconds,
                catchUpFrames = 2,
                maxCodePointsPerFrame = 2,
            )
            .toList()

        assertEquals(listOf("ab", "abcd", "abcde", "abcdef"), emissions)
    }

    @Test
    fun emptyUpstreamDoesNotEmit() = runBlocking {
        assertEquals(
            emptyList(),
            emptyFlow<String>().paceTextForUi(frameInterval = 0.milliseconds).toList(),
        )
    }

    @Test
    fun propagatesUpstreamFailure() = runBlocking {
        val expected = IllegalStateException("stream failed")

        val actual = assertFailsWith<IllegalStateException> {
            flow {
                emit("partial")
                throw expected
            }.paceTextForUi(frameInterval = 0.milliseconds).toList()
        }

        assertEquals(expected.message, actual.message)
    }
}
