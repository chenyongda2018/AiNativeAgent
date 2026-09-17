package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C

/** 更新计划：合并后的完整草稿 + 逐字段「前→后」差异（仅含真正变化的字段）。 */
data class UpdatePlan(
    val draft: EventDraft,
    val changes: List<FieldChange>,
)

/**
 * 纯逻辑的更新合并器：把 [UpdateArgs] 覆盖到既有 [CalendarEvent] 上，产出完整 [EventDraft] 与差异列表，
 * 并做最终时间校验（timed vs 全天、end > start）。无平台依赖，commonTest 可直接单测。
 *
 * 语义约定：
 *  - 未提供的字段保持原值；提供空字符串表示清空（description/location）。
 *  - 从「全天 → 定时」必须同时提供 start、end、timeZone（避免凭空造时区）。
 *  - 结果为全天时统一 timeZone="UTC"、[EventDraft.endMillis] 为「不含」的下一天零点。
 */
object CalendarUpdatePlanner {

    fun plan(existing: CalendarEvent, args: UpdateArgs): ArgResult<UpdatePlan> {
        val resultAllDay = args.allDay ?: existing.allDay
        val newTitle = args.title ?: existing.title
        if (newTitle.isBlank()) return ArgResult.Fail(C.ERR_INVALID_ARGUMENTS, "title 不能为空")
        val newDescription = args.description ?: existing.description
        val newLocation = args.location ?: existing.location

        val timePlan = when {
            resultAllDay -> planAllDay(existing, args)
            else -> planTimed(existing, args)
        }
        val time = when (timePlan) {
            is ArgResult.Fail -> return timePlan
            is ArgResult.Ok -> timePlan.value
        }

        val draft = EventDraft(
            calendarId = existing.calendarId,
            title = newTitle,
            description = newDescription,
            location = newLocation,
            startMillis = time.startMillis,
            endMillis = time.endMillis,
            allDay = resultAllDay,
            timeZone = time.timeZone,
        )

        val changes = buildList {
            if (newTitle != existing.title) add(FieldChange("标题", existing.title, newTitle))
            if (newDescription != existing.description) {
                add(FieldChange("描述", existing.description.orEmpty(), newDescription.orEmpty()))
            }
            if (newLocation != existing.location) {
                add(FieldChange("地点", existing.location.orEmpty(), newLocation.orEmpty()))
            }
            if (resultAllDay != existing.allDay) {
                add(FieldChange("全天", existing.allDay.toString(), resultAllDay.toString()))
            }
            val beforeTime = timeText(existing.startMillis, existing.endMillis, existing.allDay, existing.timeZone, existing.startDisplay, existing.endDisplay)
            val afterTime = timeText(time.startMillis, time.endMillis, resultAllDay, time.timeZone, null, null)
            if (existing.startMillis != time.startMillis || existing.endMillis != time.endMillis ||
                (existing.timeZone ?: "") != time.timeZone
            ) {
                add(FieldChange("时间", beforeTime, afterTime))
            }
        }
        if (changes.isEmpty()) {
            return ArgResult.Fail(C.ERR_INVALID_ARGUMENTS, "提供的字段与现有日程一致，没有需要修改的内容")
        }
        return ArgResult.Ok(UpdatePlan(draft, changes))
    }

    private data class TimePlan(val startMillis: Long, val endMillis: Long, val timeZone: String)

    private fun planAllDay(existing: CalendarEvent, args: UpdateArgs): ArgResult<TimePlan> {
        // 从定时事件转为全天：不能用 existing 的 UTC epoch day 推断日期——正时区（如 +08:00）的午夜在 UTC 里
        // 属于前一天，会把全天日程整体挪早一天。因此转换时必须由调用方显式给出 startDate。
        if (!existing.allDay && args.startDate == null) {
            return ArgResult.Fail(
                C.ERR_INVALID_ARGUMENTS,
                "把定时日程改为全天需显式提供 startDate（YYYY-MM-DD），以避免时区导致的日期漂移",
            )
        }
        val startDay = if (args.startDate != null) {
            CalendarTime.parseLocalDateToEpochDay(args.startDate)!!
        } else {
            // 仅当既有事件本就是全天（UTC 零点存储）时才安全，这里 existing.allDay 必为 true。
            existing.startMillis.floorDiv(CalendarTime.MILLIS_PER_DAY)
        }
        val endDay = when {
            args.endDate != null -> CalendarTime.parseLocalDateToEpochDay(args.endDate)!!
            existing.allDay -> existing.endMillis.floorDiv(CalendarTime.MILLIS_PER_DAY) - 1
            else -> startDay
        }
        if (endDay < startDay) return ArgResult.Fail(C.ERR_INVALID_TIME_RANGE, "结束日期不能早于开始日期")
        return ArgResult.Ok(
            TimePlan(startDay * CalendarTime.MILLIS_PER_DAY, (endDay + 1) * CalendarTime.MILLIS_PER_DAY, "UTC"),
        )
    }

    private fun planTimed(existing: CalendarEvent, args: UpdateArgs): ArgResult<TimePlan> {
        val switchingFromAllDay = existing.allDay
        if (switchingFromAllDay && (args.start == null || args.end == null || args.timeZone == null)) {
            return ArgResult.Fail(
                C.ERR_INVALID_ARGUMENTS,
                "从全天改为定时日程需同时提供 start、end 与 timeZone",
            )
        }
        val startMillis = if (args.start != null) {
            CalendarTime.parseOffsetDateTimeToEpochMillis(args.start)!!
        } else {
            existing.startMillis
        }
        val endMillis = if (args.end != null) {
            CalendarTime.parseOffsetDateTimeToEpochMillis(args.end)!!
        } else {
            existing.endMillis
        }
        val timeZone = args.timeZone ?: existing.timeZone ?: "UTC"
        if (endMillis <= startMillis) return ArgResult.Fail(C.ERR_INVALID_TIME_RANGE, "结束时间必须晚于开始时间")
        return ArgResult.Ok(TimePlan(startMillis, endMillis, timeZone))
    }

    /** 人类可读时间文案：全天用日期区间；定时优先平台本地化字符串，回退 UTC ISO + 时区。 */
    fun timeText(
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        timeZone: String?,
        startDisplay: String?,
        endDisplay: String?,
    ): String {
        if (allDay) {
            val startDay = startMillis.floorDiv(CalendarTime.MILLIS_PER_DAY)
            val endDayInclusive = endMillis.floorDiv(CalendarTime.MILLIS_PER_DAY) - 1
            return if (endDayInclusive <= startDay) {
                "${CalendarTime.formatLocalDate(startDay)}（全天）"
            } else {
                "${CalendarTime.formatLocalDate(startDay)} ~ ${CalendarTime.formatLocalDate(endDayInclusive)}（全天）"
            }
        }
        if (startDisplay != null && endDisplay != null) return "$startDisplay ~ $endDisplay"
        val zone = timeZone?.let { "（$it）" }.orEmpty()
        return "${CalendarTime.formatUtcIso(startMillis)} ~ ${CalendarTime.formatUtcIso(endMillis)}$zone"
    }
}
