package com.yongda.ainativeagent.tool.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 [ContentResolver] 的真实日历实现：
 *  - 查询走 [CalendarContract.Instances]，按区间展开重复日程为多个实例。
 *  - 单事件读取 / 新增 / 修改 / 删除走 [CalendarContract.Events]（重复日程的写操作作用于整个系列）。
 *
 * 所有 ContentResolver 访问都切到 [io] 调度器；本类只做数据访问，权限已由上层工具在调用前保证。
 */
class AndroidCalendarDataSource(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CalendarDataSource {

    override suspend fun writableCalendars(): List<CalendarInfo> = withContext(io) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val result = mutableListOf<CalendarInfo>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val primaryIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            val accessIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (c.moveToNext()) {
                val access = c.getInt(accessIdx)
                val writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                result += CalendarInfo(
                    id = c.getLong(idIdx),
                    displayName = c.getStringOrEmpty(nameIdx),
                    accountName = c.getStringOrEmpty(accountIdx),
                    isPrimary = c.getInt(primaryIdx) == 1,
                    isWritable = writable,
                )
            }
        }
        result
    }

    override suspend fun queryEvents(query: EventQuery): List<CalendarEvent> = withContext(io) {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(query.startMillis.toString())
            .appendPath(query.endMillis.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.RRULE,
        )
        val keyword = query.keyword?.takeIf { it.isNotBlank() }
        val selection: String?
        val selectionArgs: Array<String>?
        if (keyword != null) {
            val like = "%$keyword%"
            selection = "${CalendarContract.Instances.TITLE} LIKE ? OR " +
                "${CalendarContract.Instances.DESCRIPTION} LIKE ? OR " +
                "${CalendarContract.Instances.EVENT_LOCATION} LIKE ?"
            selectionArgs = arrayOf(like, like, like)
        } else {
            selection = null
            selectionArgs = null
        }
        val result = mutableListOf<CalendarEvent>()
        resolver.query(
            uri, projection, selection, selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && result.size < query.limit) {
                val begin = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                val end = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Instances.END))
                val allDay = c.getInt(c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) == 1
                val tz = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_TIMEZONE))
                result += CalendarEvent(
                    eventId = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)),
                    calendarId = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)),
                    title = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)) ?: "(无标题)",
                    description = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)),
                    location = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)),
                    startMillis = begin,
                    endMillis = end,
                    allDay = allDay,
                    timeZone = tz,
                    isRecurring = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Instances.RRULE)) != null,
                    calendarName = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)),
                    startDisplay = formatDisplay(begin, allDay, tz),
                    endDisplay = formatDisplay(end, allDay, tz),
                )
            }
        }
        result
    }

    override suspend fun getEvent(eventId: Long): CalendarEvent? = withContext(io) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
        )
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@withContext null
            val start = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
            val allDay = c.getInt(c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
            val dtEndIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val durationStr = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.DURATION))
            val end = if (!c.isNull(dtEndIdx)) {
                c.getLong(dtEndIdx)
            } else {
                start + (CalendarTime.parseDurationMillis(durationStr) ?: 0L)
            }
            val tz = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE))
            return@withContext CalendarEvent(
                eventId = eventId,
                calendarId = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)),
                title = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "(无标题)",
                description = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
                location = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
                startMillis = start,
                endMillis = end,
                allDay = allDay,
                timeZone = tz,
                isRecurring = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.RRULE)) != null,
                calendarName = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)),
                startDisplay = formatDisplay(start, allDay, tz),
                endDisplay = formatDisplay(end, allDay, tz),
            )
        }
        null
    }

    override suspend fun createEvent(draft: EventDraft): Long = withContext(io) {
        // 新增事件不是重复日程，统一用 DTEND 表达结束时间。
        val values = draft.toContentValues(existing = null)
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("插入日程失败：ContentResolver 返回空 Uri")
        ContentUris.parseId(uri)
    }

    override suspend fun updateEvent(eventId: Long, draft: EventDraft): Boolean = withContext(io) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val values = draft.toContentValues(existing = readExisting(eventId))
        resolver.update(uri, values, null, null) > 0
    }

    override suspend fun deleteEvent(eventId: Long): Boolean = withContext(io) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null) > 0
    }

    /** 更新写入前读取的既有原始字段，用于判定是否重复日程及时间是否变化（决定 DURATION 保留/重算）。 */
    private class ExistingRaw(
        val recurring: Boolean,
        val dtStart: Long?,
        val dtEnd: Long?,
        val duration: String?,
        val allDay: Boolean,
    )

    /**
     * @param existing 非空表示更新既有事件。重复日程（含 RRULE）必须用 DURATION 而非 DTEND：
     *   时间未变则保留原合法 DURATION，变化时用 [CalendarTime.rfc2445Duration] 生成合法值（全天 P{n}D、定时 PT{n}S）。
     *   非重复事件与新增统一用 DTEND。
     */
    private fun EventDraft.toContentValues(existing: ExistingRaw?): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
            if (existing != null && existing.recurring) {
                val existingDurationMillis = CalendarTime.parseDurationMillis(existing.duration)
                    ?: existing.dtEnd?.let { it - (existing.dtStart ?: startMillis) }
                val timesChanged = existing.dtStart != startMillis ||
                    existing.allDay != allDay ||
                    existingDurationMillis != (endMillis - startMillis)
                putNull(CalendarContract.Events.DTEND)
                put(
                    CalendarContract.Events.DURATION,
                    CalendarTime.resolveRecurringDuration(existing.duration, startMillis, endMillis, allDay, timesChanged),
                )
            } else {
                put(CalendarContract.Events.DTEND, endMillis)
                putNull(CalendarContract.Events.DURATION)
            }
        }
    }

    private fun readExisting(eventId: Long): ExistingRaw? {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
        )
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val dtStartIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val dtEndIdx = c.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            return ExistingRaw(
                recurring = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.RRULE)) != null,
                dtStart = if (c.isNull(dtStartIdx)) null else c.getLong(dtStartIdx),
                dtEnd = if (c.isNull(dtEndIdx)) null else c.getLong(dtEndIdx),
                duration = c.getStringOrNull(c.getColumnIndexOrThrow(CalendarContract.Events.DURATION)),
                allDay = c.getInt(c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1,
            )
        }
        return null
    }    private fun formatDisplay(epochMillis: Long, allDay: Boolean, timeZone: String?): String? = try {
        val zone = if (allDay) java.time.ZoneOffset.UTC else runCatching {
            java.time.ZoneId.of(timeZone)
        }.getOrDefault(java.time.ZoneId.systemDefault())
        val local = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        if (allDay) {
            local.toLocalDate().toString()
        } else {
            local.toLocalDateTime().toString().replace('T', ' ')
        }
    } catch (t: Throwable) {
        null
    }

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun Cursor.getStringOrEmpty(index: Int): String = getStringOrNull(index).orEmpty()
}
