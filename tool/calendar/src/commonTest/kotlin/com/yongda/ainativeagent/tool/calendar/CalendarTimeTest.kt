package com.yongda.ainativeagent.tool.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarTimeTest {

    @Test
    fun parsesOffsetDateTimeWithPositiveOffset() {
        // 2026-09-16T14:30:00+08:00 == 2026-09-16T06:30:00Z
        val millis = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T14:30:00+08:00")!!
        assertEquals("2026-09-16T06:30:00Z", CalendarTime.formatUtcIso(millis))
    }

    @Test
    fun parsesZuluAndCompactOffsetAndFraction() {
        val z = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-01-01T00:00:00Z")!!
        assertEquals(0L, z % 1000)
        assertEquals("2026-01-01T00:00:00Z", CalendarTime.formatUtcIso(z))
        val compact = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T14:30-0530")!!
        assertEquals("2026-09-16T20:00:00Z", CalendarTime.formatUtcIso(compact))
        val frac = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T14:30:00.250Z")!!
        assertEquals(250L, frac % 1000)
    }

    @Test
    fun rejectsMissingOffsetAndInvalidFields() {
        assertNull(CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T14:30:00")) // 无偏移
        assertNull(CalendarTime.parseOffsetDateTimeToEpochMillis("2026-13-16T14:30:00Z")) // 月越界
        assertNull(CalendarTime.parseOffsetDateTimeToEpochMillis("2026-02-30T00:00:00Z")) // 2 月无 30 日
        assertNull(CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T24:00:00Z")) // 小时越界
        assertNull(CalendarTime.parseOffsetDateTimeToEpochMillis("not a time"))
    }

    @Test
    fun handlesLeapDayAndReturnsUtc() {
        val millis = CalendarTime.parseOffsetDateTimeToEpochMillis("2024-02-29T12:00:00Z")!!
        assertEquals("2024-02-29T12:00:00Z", CalendarTime.formatUtcIso(millis))
    }

    @Test
    fun parsesLocalDateToEpochDayAndBack() {
        val day = CalendarTime.parseLocalDateToEpochDay("1970-01-01")
        assertEquals(0L, day)
        val day2 = CalendarTime.parseLocalDateToEpochDay("2026-09-16")!!
        assertEquals("2026-09-16", CalendarTime.formatLocalDate(day2))
        assertNull(CalendarTime.parseLocalDateToEpochDay("2026-09-16T00:00:00Z"))
        assertNull(CalendarTime.parseLocalDateToEpochDay("2026-02-30"))
    }

    @Test
    fun civilRoundTripsAcrossEpochBoundary() {
        listOf(
            Triple(1969, 12, 31),
            Triple(1970, 1, 1),
            Triple(2000, 2, 29),
            Triple(2026, 9, 16),
        ).forEach { (y, m, d) ->
            val days = CalendarTime.daysFromCivil(y, m, d)
            assertEquals(Triple(y, m, d), CalendarTime.civilFromDays(days))
        }
    }

    @Test
    fun formatsUtcForNegativeEpoch() {
        // 1969-12-31T23:59:59Z == -1000 ms
        assertEquals("1969-12-31T23:59:59Z", CalendarTime.formatUtcIso(-1000L))
    }
}
