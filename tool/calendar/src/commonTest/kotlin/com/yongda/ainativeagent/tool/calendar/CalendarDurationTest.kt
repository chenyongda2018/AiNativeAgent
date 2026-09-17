package com.yongda.ainativeagent.tool.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarDurationTest {

    @Test
    fun timedDurationUsesSecondsWithTPrefix() {
        // 关键回归：定时时长必须是 PT3600S，而不是非法的 P3600S。
        assertEquals("PT3600S", CalendarTime.rfc2445Duration(0L, 3_600_000L, allDay = false))
        assertEquals("PT0S", CalendarTime.rfc2445Duration(0L, 0L, allDay = false))
    }

    @Test
    fun allDayDurationUsesDays() {
        assertEquals("P1D", CalendarTime.rfc2445Duration(0L, CalendarTime.MILLIS_PER_DAY, allDay = true))
        assertEquals("P3D", CalendarTime.rfc2445Duration(0L, 3 * CalendarTime.MILLIS_PER_DAY, allDay = true))
        // 不足一天兜底为 P1D，避免空/零时长。
        assertEquals("P1D", CalendarTime.rfc2445Duration(0L, 1_000L, allDay = true))
    }

    @Test
    fun parsesDurationBothTimedAndDayForms() {
        assertEquals(3_600_000L, CalendarTime.parseDurationMillis("PT3600S"))
        assertEquals(CalendarTime.MILLIS_PER_DAY, CalendarTime.parseDurationMillis("P1D"))
        assertEquals(7 * CalendarTime.MILLIS_PER_DAY, CalendarTime.parseDurationMillis("P1W"))
        assertNull(CalendarTime.parseDurationMillis(null))
        assertNull(CalendarTime.parseDurationMillis("P"))
        assertNull(CalendarTime.parseDurationMillis("garbage"))
    }

    @Test
    fun resolvePreservesOriginalWhenTimesUnchanged() {
        // 只改标题（时间未变）：保留原合法 DURATION，不破坏 recurring event 的原始表达。
        assertEquals(
            "P1D",
            CalendarTime.resolveRecurringDuration("P1D", 0L, CalendarTime.MILLIS_PER_DAY, allDay = true, timesChanged = false),
        )
        assertEquals(
            "PT1800S",
            CalendarTime.resolveRecurringDuration("PT1800S", 0L, 1_800_000L, allDay = false, timesChanged = false),
        )
    }

    @Test
    fun resolveRegeneratesWhenTimesChangedOrOriginalInvalid() {
        assertEquals(
            "PT7200S",
            CalendarTime.resolveRecurringDuration("PT3600S", 0L, 7_200_000L, allDay = false, timesChanged = true),
        )
        // 原 DURATION 非法时也重算，即便时间“未变”。
        assertEquals(
            "PT3600S",
            CalendarTime.resolveRecurringDuration("P3600S", 0L, 3_600_000L, allDay = false, timesChanged = false),
        )
    }
}
