package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CalendarQueryAndPlannerFixesTest {

    private fun <T> ok(r: ArgResult<T>): T = when (r) {
        is ArgResult.Ok -> r.value
        is ArgResult.Fail -> fail("expected Ok but was Fail(${r.code}: ${r.message})")
    }

    private fun failOf(r: ArgResult<*>): ArgResult.Fail = when (r) {
        is ArgResult.Fail -> r
        is ArgResult.Ok -> fail("expected Fail but was Ok(${r.value})")
    }

    private fun allDayEvent(id: Long, startDate: String, endDateInclusive: String): CalendarEvent {
        val startDay = CalendarTime.parseLocalDateToEpochDay(startDate)!!
        val endDay = CalendarTime.parseLocalDateToEpochDay(endDateInclusive)!!
        return CalendarEvent(
            eventId = id, calendarId = 1, title = "休假", description = null, location = null,
            startMillis = startDay * CalendarTime.MILLIS_PER_DAY,
            endMillis = (endDay + 1) * CalendarTime.MILLIS_PER_DAY, // Provider 语义：exclusive 次日零点
            allDay = true, timeZone = "UTC", isRecurring = false, calendarName = "个人",
        )
    }

    // ---- F5：全天 query 结束日期回显为 inclusive ----

    @Test
    fun singleDayAllDayEventReportsEqualStartAndEndDate() = runTest {
        val ds = FakeCalendarDataSource()
        ds.seed(allDayEvent(1, "2026-09-16", "2026-09-16"))
        val tool = QueryCalendarEventsTool(ds, FakeCalendarPermissionController(), FixedTime(0))

        val result = tool.execute("""{"timeMin":"2026-09-15T00:00:00Z","timeMax":"2026-09-18T00:00:00Z"}""")

        assertTrue(result.ok)
        assertTrue(result.contentJson.contains("\"startDate\":\"2026-09-16\""), result.contentJson)
        // inclusive：单日事件结束日期等于开始日期，而非 Provider 内部的次日 09-17。
        assertTrue(result.contentJson.contains("\"endDate\":\"2026-09-16\""), result.contentJson)
    }

    @Test
    fun multiDayAllDayEventReportsInclusiveEndDate() = runTest {
        val ds = FakeCalendarDataSource()
        ds.seed(allDayEvent(2, "2026-09-16", "2026-09-18"))
        val tool = QueryCalendarEventsTool(ds, FakeCalendarPermissionController(), FixedTime(0))

        val result = tool.execute("""{"timeMin":"2026-09-15T00:00:00Z","timeMax":"2026-09-20T00:00:00Z"}""")

        assertTrue(result.contentJson.contains("\"startDate\":\"2026-09-16\""), result.contentJson)
        assertTrue(result.contentJson.contains("\"endDate\":\"2026-09-18\""), result.contentJson)
    }

    // ---- F7：定时 → 全天 需显式 startDate，避免时区日期漂移 ----

    private fun positiveTzTimedEvent(): CalendarEvent {
        // 2026-09-16 00:30:00+08:00 —— 其 UTC 时刻在 09-15，若用 UTC epoch day 会漂到前一天。
        val start = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T00:30:00+08:00")!!
        return CalendarEvent(
            eventId = 42, calendarId = 1, title = "夜间同步", description = null, location = null,
            startMillis = start, endMillis = start + 3_600_000L, allDay = false, timeZone = "Asia/Shanghai",
            isRecurring = false, calendarName = "个人",
        )
    }

    @Test
    fun timedToAllDayWithoutStartDateIsRejected() {
        val existing = positiveTzTimedEvent()
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":42,"allDay":true}"""))
        assertEquals(C.ERR_INVALID_ARGUMENTS, failOf(CalendarUpdatePlanner.plan(existing, args)).code)
    }

    @Test
    fun timedToAllDayWithExplicitStartDateUsesThatLocalDate() {
        val existing = positiveTzTimedEvent()
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":42,"allDay":true,"startDate":"2026-09-16"}"""))
        val plan = ok(CalendarUpdatePlanner.plan(existing, args))
        assertTrue(plan.draft.allDay)
        // 使用显式本地日期 09-16，而不是 UTC 漂移后的 09-15。
        assertEquals(CalendarTime.parseLocalDateToEpochDay("2026-09-16")!! * CalendarTime.MILLIS_PER_DAY, plan.draft.startMillis)
    }

    // ---- F3 辅助：只改标题时时间不变（数据源据此保留原 DURATION） ----

    @Test
    fun titleOnlyUpdateOnRecurringKeepsTimesUnchanged() {
        val start = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T09:00:00+08:00")!!
        val recurring = CalendarEvent(
            eventId = 7, calendarId = 1, title = "周会", description = null, location = null,
            startMillis = start, endMillis = start + 3_600_000L, allDay = false, timeZone = "Asia/Shanghai",
            isRecurring = true, calendarName = "个人",
        )
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":7,"title":"双周会"}"""))
        val plan = ok(CalendarUpdatePlanner.plan(recurring, args))
        assertEquals(recurring.startMillis, plan.draft.startMillis)
        assertEquals(recurring.endMillis, plan.draft.endMillis)
        assertEquals(1, plan.changes.size)
        assertEquals("标题", plan.changes.single().label)
    }
}
