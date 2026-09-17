package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarConfirmationControllerTest {

    @Test
    fun publishesPendingThenResolvesConfirmed() = runTest {
        val controller = CalendarConfirmationController()
        val request = CalendarConfirmation.Create("会议", "明天 10:00", "个人", allDay = false)

        val deferred = async { controller.confirm(request) }
        yield()
        assertEquals(request, controller.pending.value)

        controller.confirm() // 用户点确认
        assertEquals(ConfirmationDecision.CONFIRMED, deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun cancelResolvesCancelledAndClearsPending() = runTest {
        val controller = CalendarConfirmationController()
        val deferred = async {
            controller.confirm(CalendarConfirmation.Delete("会议", "明天", "个人", recurring = true))
        }
        yield()
        assertTrue(controller.pending.value is CalendarConfirmation.Delete)

        controller.cancel()
        assertEquals(ConfirmationDecision.CANCELLED, deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun cancellingAwaiterClearsPending() = runTest {
        val controller = CalendarConfirmationController()
        val deferred = async {
            controller.confirm(CalendarConfirmation.Create("x", "t", "个人", allDay = true))
        }
        yield()
        assertTrue(controller.pending.value != null)

        deferred.cancel()
        yield()
        assertNull(controller.pending.value)
    }
}
