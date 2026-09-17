package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CalendarUpdatePlannerTest {

    private fun timed(): CalendarEvent {
        val start = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T14:00:00+08:00")!!
        return CalendarEvent(
            eventId = 42,
            calendarId = 1,
            title = "评审会",
            description = "旧描述",
            location = "3 楼",
            startMillis = start,
            endMillis = start + 3_600_000L,
            allDay = false,
            timeZone = "Asia/Shanghai",
            isRecurring = false,
            calendarName = "个人",
        )
    }

    private fun <T> ok(r: ArgResult<T>): T = when (r) {
        is ArgResult.Ok -> r.value
        is ArgResult.Fail -> fail("expected Ok but was Fail(${r.code}: ${r.message})")
    }

    private fun failOf(r: ArgResult<*>): ArgResult.Fail = when (r) {
        is ArgResult.Fail -> r
        is ArgResult.Ok -> fail("expected Fail but was Ok(${r.value})")
    }

    @Test
    fun changingOnlyTitleProducesSingleDiffAndPreservesTime() {
        val existing = timed()
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":42,"title":"新评审会"}"""))
        val plan = ok(CalendarUpdatePlanner.plan(existing, args))
        assertEquals(1, plan.changes.size)
        assertEquals("标题", plan.changes.first().label)
        assertEquals(existing.startMillis, plan.draft.startMillis)
        assertEquals(existing.endMillis, plan.draft.endMillis)
        assertEquals("新评审会", plan.draft.title)
    }

    @Test
    fun changingTimeProducesTimeDiff() {
        val existing = timed()
        val args = ok(
            CalendarArgs.parseUpdate(
                """{"eventId":42,"start":"2026-09-16T16:00:00+08:00","end":"2026-09-16T17:00:00+08:00"}""",
            ),
        )
        val plan = ok(CalendarUpdatePlanner.plan(existing, args))
        assertTrue(plan.changes.any { it.label == "时间" })
        assertEquals(CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T16:00:00+08:00"), plan.draft.startMillis)
    }

    @Test
    fun switchingTimedToAllDay() {
        val existing = timed()
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":42,"allDay":true,"startDate":"2026-09-20"}"""))
        val plan = ok(CalendarUpdatePlanner.plan(existing, args))
        assertTrue(plan.draft.allDay)
        assertEquals("UTC", plan.draft.timeZone)
        assertEquals(CalendarTime.parseLocalDateToEpochDay("2026-09-20")!! * CalendarTime.MILLIS_PER_DAY, plan.draft.startMillis)
    }

    @Test
    fun switchingAllDayToTimedRequiresFullTime() {
        val allDayStart = CalendarTime.parseLocalDateToEpochDay("2026-09-16")!! * CalendarTime.MILLIS_PER_DAY
        val existing = timed().copy(allDay = true, timeZone = "UTC", startMillis = allDayStart, endMillis = allDayStart + CalendarTime.MILLIS_PER_DAY)
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":42,"allDay":false,"start":"2026-09-16T09:00:00+08:00"}"""))
        assertEquals(C.ERR_INVALID_ARGUMENTS, failOf(CalendarUpdatePlanner.plan(existing, args)).code)
    }

    @Test
    fun invalidTimeRangeRejected() {
        val existing = timed()
        val args = ok(
            CalendarArgs.parseUpdate(
                """{"eventId":42,"start":"2026-09-16T18:00:00+08:00","end":"2026-09-16T17:00:00+08:00"}""",
            ),
        )
        assertEquals(C.ERR_INVALID_TIME_RANGE, failOf(CalendarUpdatePlanner.plan(existing, args)).code)
    }

    @Test
    fun noEffectiveChangeIsRejected() {
        val existing = timed()
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":42,"title":"评审会"}"""))
        assertEquals(C.ERR_INVALID_ARGUMENTS, failOf(CalendarUpdatePlanner.plan(existing, args)).code)
    }
}
