package com.yongda.ainativeagent.tool.calendar

/** 内存版日历数据源：供工具单测做 CRUD，绝不触碰真实设备日历。 */
class FakeCalendarDataSource(
    var calendars: List<CalendarInfo> = listOf(
        CalendarInfo(id = 1, displayName = "个人", accountName = "me@example.com", isPrimary = true, isWritable = true),
    ),
) : CalendarDataSource {

    val events = mutableMapOf<Long, CalendarEvent>()
    var nextId = 100L
    var failWith: Throwable? = null

    val createdDrafts = mutableListOf<EventDraft>()
    val updatedDrafts = mutableListOf<Pair<Long, EventDraft>>()
    val deletedIds = mutableListOf<Long>()

    fun seed(event: CalendarEvent) {
        events[event.eventId] = event
    }

    override suspend fun writableCalendars(): List<CalendarInfo> {
        failWith?.let { throw it }
        return calendars
    }

    override suspend fun queryEvents(query: EventQuery): List<CalendarEvent> {
        failWith?.let { throw it }
        val keyword = query.keyword?.lowercase()
        return events.values
            .filter { it.startMillis < query.endMillis && it.endMillis > query.startMillis }
            .filter { e ->
                keyword == null ||
                    e.title.lowercase().contains(keyword) ||
                    (e.description?.lowercase()?.contains(keyword) == true) ||
                    (e.location?.lowercase()?.contains(keyword) == true)
            }
            .sortedBy { it.startMillis }
            .take(query.limit)
    }

    override suspend fun getEvent(eventId: Long): CalendarEvent? {
        failWith?.let { throw it }
        return events[eventId]
    }

    override suspend fun createEvent(draft: EventDraft): Long {
        failWith?.let { throw it }
        val id = nextId++
        createdDrafts += draft
        events[id] = draft.toEvent(id)
        return id
    }

    override suspend fun updateEvent(eventId: Long, draft: EventDraft): Boolean {
        failWith?.let { throw it }
        if (eventId !in events) return false
        updatedDrafts += eventId to draft
        events[eventId] = draft.toEvent(eventId, keepRecurring = events[eventId]?.isRecurring ?: false)
        return true
    }

    override suspend fun deleteEvent(eventId: Long): Boolean {
        failWith?.let { throw it }
        deletedIds += eventId
        return events.remove(eventId) != null
    }

    private fun EventDraft.toEvent(id: Long, keepRecurring: Boolean = false): CalendarEvent {
        val name = calendars.firstOrNull { it.id == calendarId }?.displayName
        return CalendarEvent(
            eventId = id,
            calendarId = calendarId,
            title = title,
            description = description,
            location = location,
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = allDay,
            timeZone = timeZone,
            isRecurring = keepRecurring,
            calendarName = name,
        )
    }
}

/** 记录每次权限申请顺序，并按权限返回可配置结果（默认 GRANTED）。 */
class FakeCalendarPermissionController(
    private val results: Map<CalendarPermission, PermissionResult> = emptyMap(),
    private val default: PermissionResult = PermissionResult.GRANTED,
) : CalendarPermissionController {

    val requested = mutableListOf<CalendarPermission>()

    override suspend fun request(permission: CalendarPermission): PermissionResult {
        requested += permission
        return results[permission] ?: default
    }
}

/** 记录收到的确认请求，并按配置返回决定（默认 CONFIRMED）。 */
class FakeConfirmationPresenter(
    var decision: ConfirmationDecision = ConfirmationDecision.CONFIRMED,
) : CalendarConfirmationPresenter {

    val requests = mutableListOf<CalendarConfirmation>()

    override suspend fun confirm(request: CalendarConfirmation): ConfirmationDecision {
        requests += request
        return decision
    }
}

/** 固定时钟。 */
class FixedTime(private val millis: Long) : CurrentTime {
    override fun nowEpochMillis(): Long = millis
}
