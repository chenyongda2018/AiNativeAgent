package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryIntervalValidationTest {

    private val now = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T12:00:00Z")!!

    @Test
    fun lonePastTimeMaxYieldsInvertedEffectiveIntervalAndIsRejectedBeforePermission() = runTest {
        val ds = FakeCalendarDataSource()
        val perms = FakeCalendarPermissionController()
        val tool = QueryCalendarEventsTool(ds, perms, FixedTime(now))

        // 只给一个过去的 timeMax（省略 timeMin）：默认 timeMin=now → start(now) > end(过去)。
        val result = tool.execute("""{"timeMax":"2026-09-15T00:00:00Z"}""")

        assertEquals(C.ERR_INVALID_TIME_RANGE, result.errorCode)
        // 无意义区间在申请权限、触达数据源之前就被拦截。
        assertTrue(perms.requested.isEmpty(), "不应为无效区间申请日历权限")
    }

    @Test
    fun loneFutureTimeMaxRemainsValid() = runTest {
        val ds = FakeCalendarDataSource()
        val tool = QueryCalendarEventsTool(ds, FakeCalendarPermissionController(), FixedTime(now))

        val result = tool.execute("""{"timeMax":"2026-09-20T00:00:00Z"}""")

        assertTrue(result.ok)
        assertTrue(result.contentJson.contains("\"count\":0"))
    }

    @Test
    fun loneTimeMinStillDefaultsToSevenDayWindow() = runTest {
        val ds = FakeCalendarDataSource()
        val tool = QueryCalendarEventsTool(ds, FakeCalendarPermissionController(), FixedTime(now))

        // 只给 timeMin：end = timeMin + 7 天，恒晚于 start，仍然有效。
        val result = tool.execute("""{"timeMin":"2026-09-16T00:00:00Z"}""")

        assertTrue(result.ok)
    }
}
