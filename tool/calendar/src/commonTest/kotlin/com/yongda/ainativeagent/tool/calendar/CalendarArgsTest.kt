package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CalendarArgsTest {

    private fun <T> ok(r: ArgResult<T>): T = when (r) {
        is ArgResult.Ok -> r.value
        is ArgResult.Fail -> fail("expected Ok but was Fail(${r.code}: ${r.message})")
    }

    private fun fail(r: ArgResult<*>): ArgResult.Fail = when (r) {
        is ArgResult.Fail -> r
        is ArgResult.Ok -> fail("expected Fail but was Ok(${r.value})")
    }

    // ---- query ----

    @Test
    fun queryDefaultsLimitAndAllowsEmptyObject() {
        val args = ok(CalendarArgs.parseQuery("{}"))
        assertEquals(C.DEFAULT_QUERY_LIMIT, args.limit)
        assertEquals(null, args.timeMinMillis)
    }

    @Test
    fun queryClampsLimitAndRejectsUnknownKeyAndBadRange() {
        assertEquals(C.MAX_QUERY_LIMIT, ok(CalendarArgs.parseQuery("""{"limit":999}""")).limit)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseQuery("""{"foo":1}""")).code)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseQuery("""{"limit":0}""")).code)
        assertEquals(
            C.ERR_INVALID_TIME_RANGE,
            fail(CalendarArgs.parseQuery("""{"timeMin":"2026-09-16T10:00:00Z","timeMax":"2026-09-16T09:00:00Z"}""")).code,
        )
    }

    // ---- create ----

    @Test
    fun createTimedHappyPath() {
        val args = ok(
            CalendarArgs.parseCreate(
                """{"title":"评审会","start":"2026-09-16T14:00:00+08:00","end":"2026-09-16T15:00:00+08:00","timeZone":"Asia/Shanghai","location":"3 楼"}""",
            ),
        )
        assertEquals("评审会", args.title)
        assertEquals(false, args.allDay)
        assertEquals("Asia/Shanghai", args.timeZone)
        assertTrue(args.endMillis > args.startMillis)
        assertEquals("3 楼", args.location)
    }

    @Test
    fun createAllDayComputesExclusiveEnd() {
        val args = ok(CalendarArgs.parseCreate("""{"title":"休假","allDay":true,"startDate":"2026-09-16","endDate":"2026-09-17"}"""))
        assertTrue(args.allDay)
        assertEquals("UTC", args.timeZone)
        // 含 09-16、09-17 两天 → 结束为 09-18 零点（不含）
        assertEquals(CalendarTime.parseLocalDateToEpochDay("2026-09-18")!! * CalendarTime.MILLIS_PER_DAY, args.endMillis)
    }

    @Test
    fun createRejectsMissingTitleAndMissingTimeAndBadRange() {
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseCreate("""{}""")).code)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseCreate("""{"title":"x"}""")).code) // 缺 start/end/tz
        assertEquals(
            C.ERR_INVALID_TIME_RANGE,
            fail(
                CalendarArgs.parseCreate(
                    """{"title":"x","start":"2026-09-16T15:00:00+08:00","end":"2026-09-16T14:00:00+08:00","timeZone":"Asia/Shanghai"}""",
                ),
            ).code,
        )
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseCreate("""{"title":"x","bogus":1}""")).code)
    }

    @Test
    fun createRejectsWrongTypes() {
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseCreate("""{"title":123}""")).code)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseCreate("""{"title":"x","calendarId":"abc"}""")).code)
    }

    // ---- update ----

    @Test
    fun updateRequiresEventIdAndAtLeastOneField() {
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseUpdate("""{}""")).code)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseUpdate("""{"eventId":5}""")).code)
        val args = ok(CalendarArgs.parseUpdate("""{"eventId":5,"title":"改名"}"""))
        assertEquals(5L, args.eventId)
        assertEquals("改名", args.title)
    }

    @Test
    fun updateValidatesProvidedTimeFormats() {
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseUpdate("""{"eventId":5,"start":"nope"}""")).code)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseUpdate("""{"eventId":5,"startDate":"2026-13-01"}""")).code)
    }

    // ---- delete ----

    @Test
    fun deleteParsesEventId() {
        assertEquals(7L, ok(CalendarArgs.parseDelete("""{"eventId":7}""")))
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseDelete("""{}""")).code)
        assertEquals(C.ERR_INVALID_ARGUMENTS, fail(CalendarArgs.parseDelete("""{"eventId":"7"}""")).code)
    }
}
