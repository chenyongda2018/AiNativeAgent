package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarToolsTest {

    private val now = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T00:00:00Z")!!

    private fun seedTimedEvent(ds: FakeCalendarDataSource, id: Long = 42, recurring: Boolean = false): CalendarEvent {
        val start = CalendarTime.parseOffsetDateTimeToEpochMillis("2026-09-16T14:00:00+08:00")!!
        val event = CalendarEvent(
            eventId = id, calendarId = 1, title = "评审会", description = null, location = "3 楼",
            startMillis = start, endMillis = start + 3_600_000L, allDay = false, timeZone = "Asia/Shanghai",
            isRecurring = recurring, calendarName = "个人",
        )
        ds.seed(event)
        return event
    }

    // ---- query ----

    @Test
    fun queryRequestsReadThenReturnsEvents() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds)
        val perms = FakeCalendarPermissionController()
        val tool = QueryCalendarEventsTool(ds, perms, FixedTime(now))

        val result = tool.execute("""{"timeMin":"2026-09-16T00:00:00+08:00","timeMax":"2026-09-17T00:00:00+08:00"}""")

        assertTrue(result.ok)
        assertEquals(listOf(CalendarPermission.READ), perms.requested)
        assertTrue(result.contentJson.contains("评审会"))
        assertTrue(result.contentJson.contains("\"count\":1"))
    }

    @Test
    fun queryDeniedReturnsStructuredErrorAndDoesNotTouchDataSource() = runTest {
        val ds = FakeCalendarDataSource()
        val perms = FakeCalendarPermissionController(mapOf(CalendarPermission.READ to PermissionResult.DENIED))
        val tool = QueryCalendarEventsTool(ds, perms, FixedTime(now))

        val result = tool.execute("{}")

        assertFalse(result.ok)
        assertEquals(C.ERR_PERMISSION_DENIED, result.errorCode)
    }

    @Test
    fun queryUsesDefaultSevenDayWindowFromNow() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds)
        val tool = QueryCalendarEventsTool(ds, FakeCalendarPermissionController(), FixedTime(now))
        val result = tool.execute("{}")
        assertTrue(result.ok)
        assertTrue(result.contentJson.contains("\"count\":1"))
    }

    // ---- create ----

    @Test
    fun createFollowsReadConfirmWriteOrderAndPersists() = runTest {
        val ds = FakeCalendarDataSource()
        val perms = FakeCalendarPermissionController()
        val confirm = FakeConfirmationPresenter()
        val tool = CreateCalendarEventTool(ds, perms, confirm)

        val result = tool.execute(
            """{"title":"牙医","start":"2026-09-20T09:00:00+08:00","end":"2026-09-20T09:30:00+08:00","timeZone":"Asia/Shanghai"}""",
        )

        assertTrue(result.ok)
        // 顺序：先 READ（解析目标），确认后再 WRITE。
        assertEquals(listOf(CalendarPermission.READ, CalendarPermission.WRITE), perms.requested)
        assertEquals(1, confirm.requests.size)
        assertTrue(confirm.requests.first() is CalendarConfirmation.Create)
        assertEquals(1, ds.createdDrafts.size)
        assertEquals("牙医", ds.createdDrafts.first().title)
    }

    @Test
    fun createCancelledConfirmationSkipsWriteAndPersistNothing() = runTest {
        val ds = FakeCalendarDataSource()
        val perms = FakeCalendarPermissionController()
        val confirm = FakeConfirmationPresenter(ConfirmationDecision.CANCELLED)
        val tool = CreateCalendarEventTool(ds, perms, confirm)

        val result = tool.execute(
            """{"title":"牙医","start":"2026-09-20T09:00:00+08:00","end":"2026-09-20T09:30:00+08:00","timeZone":"Asia/Shanghai"}""",
        )

        assertEquals(C.ERR_USER_CANCELLED, result.errorCode)
        assertEquals(listOf(CalendarPermission.READ), perms.requested) // 未申请 WRITE
        assertTrue(ds.createdDrafts.isEmpty())
    }

    @Test
    fun createWithNoWritableCalendarFails() = runTest {
        val ds = FakeCalendarDataSource(calendars = emptyList())
        val perms = FakeCalendarPermissionController()
        val confirm = FakeConfirmationPresenter()
        val tool = CreateCalendarEventTool(ds, perms, confirm)

        val result = tool.execute(
            """{"title":"x","start":"2026-09-20T09:00:00+08:00","end":"2026-09-20T09:30:00+08:00","timeZone":"Asia/Shanghai"}""",
        )

        assertEquals(C.ERR_NO_WRITABLE_CALENDAR, result.errorCode)
        assertTrue(confirm.requests.isEmpty())
    }

    @Test
    fun createWithUnknownCalendarIdFails() = runTest {
        val ds = FakeCalendarDataSource()
        val tool = CreateCalendarEventTool(ds, FakeCalendarPermissionController(), FakeConfirmationPresenter())
        val result = tool.execute(
            """{"title":"x","calendarId":999,"start":"2026-09-20T09:00:00+08:00","end":"2026-09-20T09:30:00+08:00","timeZone":"Asia/Shanghai"}""",
        )
        assertEquals(C.ERR_CALENDAR_NOT_FOUND, result.errorCode)
    }

    @Test
    fun createWritePermanentlyDeniedAfterConfirm() = runTest {
        val ds = FakeCalendarDataSource()
        val perms = FakeCalendarPermissionController(
            mapOf(CalendarPermission.WRITE to PermissionResult.PERMANENTLY_DENIED),
        )
        val tool = CreateCalendarEventTool(ds, perms, FakeConfirmationPresenter())
        val result = tool.execute(
            """{"title":"x","start":"2026-09-20T09:00:00+08:00","end":"2026-09-20T09:30:00+08:00","timeZone":"Asia/Shanghai"}""",
        )
        assertEquals(C.ERR_PERMISSION_PERMANENTLY_DENIED, result.errorCode)
        assertTrue(ds.createdDrafts.isEmpty())
    }

    // ---- update ----

    @Test
    fun updateShowsDiffConfirmsAndPersists() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds, id = 42)
        val perms = FakeCalendarPermissionController()
        val confirm = FakeConfirmationPresenter()
        val tool = UpdateCalendarEventTool(ds, perms, confirm)

        val result = tool.execute("""{"eventId":42,"title":"新评审会"}""")

        assertTrue(result.ok)
        assertEquals(listOf(CalendarPermission.READ, CalendarPermission.WRITE), perms.requested)
        val req = confirm.requests.single() as CalendarConfirmation.Update
        assertEquals("评审会", req.title)
        assertTrue(req.changes.any { it.label == "标题" })
        assertEquals("新评审会", ds.events[42]?.title)
    }

    @Test
    fun updateMissingEventFails() = runTest {
        val ds = FakeCalendarDataSource()
        val tool = UpdateCalendarEventTool(ds, FakeCalendarPermissionController(), FakeConfirmationPresenter())
        val result = tool.execute("""{"eventId":404,"title":"x"}""")
        assertEquals(C.ERR_EVENT_NOT_FOUND, result.errorCode)
    }

    @Test
    fun updateRecurringMarksSeriesInConfirmation() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds, id = 42, recurring = true)
        val confirm = FakeConfirmationPresenter()
        val tool = UpdateCalendarEventTool(ds, FakeCalendarPermissionController(), confirm)
        tool.execute("""{"eventId":42,"title":"改"}""")
        assertTrue((confirm.requests.single() as CalendarConfirmation.Update).recurring)
    }

    // ---- delete ----

    @Test
    fun deleteConfirmsThenRemoves() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds, id = 42)
        val perms = FakeCalendarPermissionController()
        val confirm = FakeConfirmationPresenter()
        val tool = DeleteCalendarEventTool(ds, perms, confirm)

        val result = tool.execute("""{"eventId":42}""")

        assertTrue(result.ok)
        assertEquals(listOf(CalendarPermission.READ, CalendarPermission.WRITE), perms.requested)
        assertTrue(confirm.requests.single() is CalendarConfirmation.Delete)
        assertFalse(ds.events.containsKey(42))
    }

    @Test
    fun deleteCancelledKeepsEvent() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds, id = 42)
        val perms = FakeCalendarPermissionController()
        val tool = DeleteCalendarEventTool(ds, perms, FakeConfirmationPresenter(ConfirmationDecision.CANCELLED))

        val result = tool.execute("""{"eventId":42}""")

        assertEquals(C.ERR_USER_CANCELLED, result.errorCode)
        assertEquals(listOf(CalendarPermission.READ), perms.requested)
        assertTrue(ds.events.containsKey(42))
    }

    @Test
    fun dataSourceFailureBecomesStructuredError() = runTest {
        val ds = FakeCalendarDataSource()
        seedTimedEvent(ds, id = 42)
        ds.failWith = IllegalStateException("provider boom")
        val tool = DeleteCalendarEventTool(ds, FakeCalendarPermissionController(), FakeConfirmationPresenter())
        val result = tool.execute("""{"eventId":42}""")
        assertEquals(C.ERR_CALENDAR_ERROR, result.errorCode)
    }
}
