package com.yongda.ainativeagent.chat.vm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class StreamTextPacerTest {

    @Test
    fun emitsCompleteTextWithoutSplittingSurrogatePairs() = runTest {
        val emissions = flowOf("你好🙂abc")
            .paceTextForUi(
                frameInterval = 0.milliseconds,
                catchUpFrames = 1,
                normalMaxCodePointsPerFrame = 1,
            )
            .toList()

        assertEquals(
            listOf("你", "你好", "你好🙂", "你好🙂a", "你好🙂ab", "你好🙂abc"),
            emissions,
        )
    }

    @Test
    fun combinesAllNetworkChunksWithoutDroppingContent() = runTest {
        val emissions = flowOf("第一", "批", "\n", "second")
            .paceTextForUi(frameInterval = 0.milliseconds)
            .toList()

        assertEquals("第一批\nsecond", emissions.last())
    }

    @Test
    fun largeChunkIsRevealedAcrossMultipleFrames() = runTest {
        val emissions = flowOf("abcdef")
            .paceTextForUi(
                frameInterval = 0.milliseconds,
                catchUpFrames = 2,
                normalMaxCodePointsPerFrame = 2,
            )
            .toList()

        assertEquals(listOf("ab", "abcd", "abcdef"), emissions)
    }

    @Test
    fun emptyUpstreamDoesNotEmit() = runTest {
        assertEquals(
            emptyList(),
            emptyFlow<String>().paceTextForUi(frameInterval = 0.milliseconds).toList(),
        )
    }

    @Test
    fun propagatesUpstreamFailure() = runTest {
        val expected = IllegalStateException("stream failed")

        val actual = assertFailsWith<IllegalStateException> {
            flow {
                emit("partial")
                throw expected
            }.paceTextForUi(frameInterval = 0.milliseconds).toList()
        }

        assertEquals(expected.message, actual.message)
    }

    @Test
    fun ordinaryBacklogDrainsInAboutOneHundredMillisecondsAtSixteenMillisecondIntervals() = runTest {
        val text = "a".repeat(84)
        val frames = mutableListOf<Pair<Long, String>>()

        flowOf(text).paceTextForUi().collect { frames += testScheduler.currentTime to it }

        assertEquals((0L..96L step 16).toList(), frames.map { it.first })
        assertEquals((12..84 step 12).toList(), frames.map { it.second.length })
        assertEquals(text, frames.last().second)
    }

    @Test
    fun hugeBurstDrainsWithinThirtyFramesWithoutAnExponentialTail() = runTest {
        val text = "x".repeat(100_000)
        val frames = mutableListOf<Pair<Long, String>>()

        flowOf(text).paceTextForUi().collect { frames += testScheduler.currentTime to it }

        assertEquals(30, frames.size)
        assertEquals(464L, frames.last().first)
        assertEquals(text, frames.last().second)
        assertTrue(frames.first().second.length < text.length)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.second.startsWith(a.second) })
        assertTrue(frames.zipWithNext().all { (a, b) -> b.first - a.first == 16L })
    }

    @Test
    fun incomingBurstRaisesTheBudgetDuringAnExistingBacklog() = runTest {
        val frames = mutableListOf<Pair<Long, String>>()
        val burst = "b".repeat(100_000)

        flow {
            emit("a".repeat(84))
            delay(8)
            emit(burst)
        }.paceTextForUi().collect { frames += testScheduler.currentTime to it }

        assertEquals(12, frames.first().second.length)
        assertTrue(frames[1].second.length - frames[0].second.length > 12)
        assertTrue(frames.last().first <= 480L)
        assertEquals("a".repeat(84) + burst, frames.last().second)
    }

    @Test
    fun catchUpBudgetResetsAfterTheBacklogIsEmpty() = runTest {
        val frames = mutableListOf<Pair<Long, String>>()
        val burst = "x".repeat(10_000)

        flow {
            emit(burst)
            delay(1_000)
            emit("a".repeat(84))
        }.paceTextForUi().collect { frames += testScheduler.currentTime to it }

        val ordinaryFrames = frames.filter { it.first >= 1_000 }
        assertEquals((1_000L..1_096L step 16).toList(), ordinaryFrames.map { it.first })
        assertEquals(burst.length + 12, ordinaryFrames.first().second.length)
        assertEquals(burst + "a".repeat(84), frames.last().second)
    }

    @Test
    fun rapidNetworkChunksAreCoalescedAndNeverEmitFasterThanTheFrameInterval() = runTest {
        val frames = mutableListOf<Pair<Long, String>>()

        flow {
            repeat(100) {
                emit("x")
                delay(1)
            }
        }.paceTextForUi().collect { frames += testScheduler.currentTime to it }

        assertEquals(0L, frames.first().first)
        assertTrue(frames.size < 100)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.first - a.first >= 16L })
        assertTrue(frames.last().first <= 212L)
        assertEquals("x".repeat(100), frames.last().second)
    }

    @Test
    fun surrogatePairAcrossDelayedChunksIsNeverSplitBetweenSnapshots() = runTest {
        val frames = mutableListOf<Pair<Long, String>>()

        flow {
            emit("a\uD83D")
            delay(20)
            emit("\uDE42b")
        }.paceTextForUi().collect { frames += testScheduler.currentTime to it }

        assertEquals("a\uD83D\uDE42b", frames.last().second)
        assertTrue(frames.none { it.second.last().isHighSurrogate() })
        assertTrue(frames.zipWithNext().all { (a, b) -> b.first - a.first >= 16L })
    }

    @Test
    fun upstreamCancellationIsNotMistakenForSuccessfulCompletion() = runTest {
        val failure = assertFailsWith<CancellationException> {
            flow<String> {
                throw CancellationException("provider cancelled")
            }.paceTextForUi().toList()
        }

        assertEquals("provider cancelled", failure.message)
    }

    @Test
    fun rejectsInvalidPacingParameters() = runTest {
        assertFailsWith<IllegalArgumentException> {
            flowOf("a").paceTextForUi(frameInterval = (-1).milliseconds).toList()
        }
        assertFailsWith<IllegalArgumentException> {
            flowOf("a").paceTextForUi(catchUpFrames = 0).toList()
        }
        assertFailsWith<IllegalArgumentException> {
            flowOf("a").paceTextForUi(normalMaxCodePointsPerFrame = 0).toList()
        }
        assertFailsWith<IllegalArgumentException> {
            flowOf("a").paceTextForUi(maxDrainFrames = 0).toList()
        }
    }
}
