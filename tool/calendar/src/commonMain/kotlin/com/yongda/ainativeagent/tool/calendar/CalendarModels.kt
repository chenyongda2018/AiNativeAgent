package com.yongda.ainativeagent.tool.calendar

/** 可写日历（账户下的一个日历）。用于新增事件时选择目标，也用于确认 UI 展示目标日历名。 */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
    val isWritable: Boolean,
)

/**
 * 一条日历事件（查询走 Instances 展开重复事件，因此同一 [eventId] 的重复日程会返回多个实例，
 * 各自带自己的 [startMillis]/[endMillis]）。写操作以 [eventId] 定位底层 Events 行。
 *
 * @param startDisplay/[endDisplay] 平台侧按事件时区格式化的本地可读时间（可空）；确认 UI 优先展示它，
 *   缺省时回退到 [CalendarTime.formatUtcIso]。
 */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val timeZone: String?,
    val isRecurring: Boolean,
    val calendarName: String?,
    val startDisplay: String? = null,
    val endDisplay: String? = null,
)

/**
 * 写入 Events 的完整草稿。全天事件约定 [timeZone] = "UTC"、[startMillis]/[endMillis] 为 UTC 零点、
 * [endMillis] 为「不含」的下一天零点（对齐 CalendarContract 的全天语义）。
 */
data class EventDraft(
    val calendarId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val timeZone: String,
)

/** 查询参数（已校验）。缺省区间由工具用当前时间补齐。 */
data class EventQuery(
    val startMillis: Long,
    val endMillis: Long,
    val keyword: String?,
    val limit: Int,
)

/** 更新时的一处字段变更，用于确认 UI 的「前 → 后」差异展示。 */
data class FieldChange(
    val label: String,
    val before: String,
    val after: String,
)
