package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.tool.calendar.CalendarToolContracts as C
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** 解析结果：成功携带类型化参数，失败携带结构化错误码与消息。 */
sealed interface ArgResult<out T> {
    data class Ok<T>(val value: T) : ArgResult<T>
    data class Fail(val code: String, val message: String) : ArgResult<Nothing>
}

/** query_calendar_events 的已校验参数（区间缺省由工具用当前时间补齐）。 */
data class QueryArgs(
    val timeMinMillis: Long?,
    val timeMaxMillis: Long?,
    val keyword: String?,
    val limit: Int,
)

/** create_calendar_event 的已校验参数；时间已归一到 epoch 毫秒，[displayTime] 保留用户原始可读表述用于确认 UI。 */
data class CreateArgs(
    val title: String,
    val description: String?,
    val location: String?,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val timeZone: String,
    val calendarId: Long?,
    val displayTime: String,
)

/**
 * update_calendar_event 的已校验参数。各字段 null 表示「未提供、保持原值」；提供的字符串字段格式已校验。
 * 时间相关字段（timed vs allDay）与「end > start」的最终校验在工具内结合既有事件完成。
 */
data class UpdateArgs(
    val eventId: Long,
    val title: String?,
    val description: String?,
    val location: String?,
    val allDay: Boolean?,
    val start: String?,
    val end: String?,
    val timeZone: String?,
    val startDate: String?,
    val endDate: String?,
) {
    val mutableFieldCount: Int
        get() = listOf(title, description, location, allDay, start, end, timeZone, startDate, endDate)
            .count { it != null }
}

/**
 * 四个日历工具的参数解析 / 严格校验。拒绝未知字段、类型错误、缺失必填、时间越界与区间反转，
 * 全部返回结构化 [ArgResult.Fail]，不抛异常。纯逻辑，commonTest 可直接单测。
 */
object CalendarArgs {

    private val json = Json { ignoreUnknownKeys = false }

    fun parseQuery(argumentsJson: String): ArgResult<QueryArgs> {
        val obj = objectOf(argumentsJson) ?: return invalid("参数必须是 JSON 对象")
        rejectUnknownKeys(obj, setOf("timeMin", "timeMax", "keyword", "limit"))?.let { return it }

        val timeMin = when (val r = optionalOffsetMillis(obj, "timeMin")) {
            is ArgResult.Fail -> return r
            is ArgResult.Ok -> r.value
        }
        val timeMax = when (val r = optionalOffsetMillis(obj, "timeMax")) {
            is ArgResult.Fail -> return r
            is ArgResult.Ok -> r.value
        }
        if (timeMin != null && timeMax != null && timeMax <= timeMin) {
            return ArgResult.Fail(C.ERR_INVALID_TIME_RANGE, "timeMax 必须晚于 timeMin")
        }
        val keyword = optionalString(obj, "keyword").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
            ?.takeIf { it.isNotBlank() }

        val limitRaw = obj["limit"]
        val limit = if (limitRaw == null) {
            C.DEFAULT_QUERY_LIMIT
        } else {
            val l = (limitRaw as? JsonPrimitive)?.longOrNull
                ?: return invalid("limit 必须是整数")
            if (l < 1) return invalid("limit 至少为 1")
            if (l > C.MAX_QUERY_LIMIT) C.MAX_QUERY_LIMIT else l.toInt()
        }
        return ArgResult.Ok(QueryArgs(timeMin, timeMax, keyword, limit))
    }

    fun parseCreate(argumentsJson: String): ArgResult<CreateArgs> {
        val obj = objectOf(argumentsJson) ?: return invalid("参数必须是 JSON 对象")
        rejectUnknownKeys(
            obj,
            setOf("title", "description", "location", "allDay", "start", "end", "timeZone", "startDate", "endDate", "calendarId"),
        )?.let { return it }

        val title = requiredString(obj, "title").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        if (title.isBlank()) return invalid("title 不能为空")
        val description = optionalString(obj, "description").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val location = optionalString(obj, "location").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val allDay = optionalBoolean(obj, "allDay").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value } ?: false
        val calendarId = optionalLong(obj, "calendarId").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }

        if (allDay) {
            val startDate = requiredString(obj, "startDate").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
            val startDay = CalendarTime.parseLocalDateToEpochDay(startDate)
                ?: return invalid("startDate 必须是有效的 YYYY-MM-DD")
            val endDateStr = optionalString(obj, "endDate").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
            val endDay = if (endDateStr == null) {
                startDay
            } else {
                CalendarTime.parseLocalDateToEpochDay(endDateStr)
                    ?: return invalid("endDate 必须是有效的 YYYY-MM-DD")
            }
            if (endDay < startDay) return ArgResult.Fail(C.ERR_INVALID_TIME_RANGE, "endDate 不能早于 startDate")
            // 全天：UTC 零点起，结束为「不含」的下一天零点（对齐 CalendarContract）。
            val startMillis = startDay * CalendarTime.MILLIS_PER_DAY
            val endMillis = (endDay + 1) * CalendarTime.MILLIS_PER_DAY
            val displayTime = if (endDay == startDay) {
                "${CalendarTime.formatLocalDate(startDay)}（全天）"
            } else {
                "${CalendarTime.formatLocalDate(startDay)} ~ ${CalendarTime.formatLocalDate(endDay)}（全天）"
            }
            return ArgResult.Ok(
                CreateArgs(title, description, location, allDay = true, startMillis, endMillis, timeZone = "UTC", calendarId, displayTime),
            )
        }

        val start = requiredString(obj, "start").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val end = requiredString(obj, "end").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val timeZone = requiredString(obj, "timeZone").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        if (timeZone.isBlank()) return invalid("timeZone 不能为空")
        val startMillis = CalendarTime.parseOffsetDateTimeToEpochMillis(start)
            ?: return invalid("start 必须是带时区偏移的 ISO-8601 时间")
        val endMillis = CalendarTime.parseOffsetDateTimeToEpochMillis(end)
            ?: return invalid("end 必须是带时区偏移的 ISO-8601 时间")
        if (endMillis <= startMillis) return ArgResult.Fail(C.ERR_INVALID_TIME_RANGE, "end 必须晚于 start")
        return ArgResult.Ok(
            CreateArgs(title, description, location, allDay = false, startMillis, endMillis, timeZone, calendarId, "$start ~ $end（$timeZone）"),
        )
    }

    fun parseUpdate(argumentsJson: String): ArgResult<UpdateArgs> {
        val obj = objectOf(argumentsJson) ?: return invalid("参数必须是 JSON 对象")
        rejectUnknownKeys(
            obj,
            setOf("eventId", "title", "description", "location", "allDay", "start", "end", "timeZone", "startDate", "endDate"),
        )?.let { return it }

        val eventId = requiredLong(obj, "eventId").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val title = optionalString(obj, "title").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val description = optionalString(obj, "description").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val location = optionalString(obj, "location").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val allDay = optionalBoolean(obj, "allDay").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val start = optionalString(obj, "start").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val end = optionalString(obj, "end").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val timeZone = optionalString(obj, "timeZone").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val startDate = optionalString(obj, "startDate").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }
        val endDate = optionalString(obj, "endDate").let { if (it is ArgResult.Fail) return it else (it as ArgResult.Ok).value }

        // 提供了的时间字符串必须格式合法（最终 end>start 校验在工具内结合既有事件完成）。
        if (start != null && CalendarTime.parseOffsetDateTimeToEpochMillis(start) == null) {
            return invalid("start 必须是带时区偏移的 ISO-8601 时间")
        }
        if (end != null && CalendarTime.parseOffsetDateTimeToEpochMillis(end) == null) {
            return invalid("end 必须是带时区偏移的 ISO-8601 时间")
        }
        if (startDate != null && CalendarTime.parseLocalDateToEpochDay(startDate) == null) {
            return invalid("startDate 必须是有效的 YYYY-MM-DD")
        }
        if (endDate != null && CalendarTime.parseLocalDateToEpochDay(endDate) == null) {
            return invalid("endDate 必须是有效的 YYYY-MM-DD")
        }

        val args = UpdateArgs(eventId, title, description, location, allDay, start, end, timeZone, startDate, endDate)
        if (args.mutableFieldCount == 0) {
            return invalid("至少要提供一个待修改字段")
        }
        return ArgResult.Ok(args)
    }

    fun parseDelete(argumentsJson: String): ArgResult<Long> {
        val obj = objectOf(argumentsJson) ?: return invalid("参数必须是 JSON 对象")
        rejectUnknownKeys(obj, setOf("eventId"))?.let { return it }
        return requiredLong(obj, "eventId")
    }

    // ---- 通用 JSON 读取 / 校验 ----

    private fun objectOf(argumentsJson: String): JsonObject? {
        val trimmed = argumentsJson.trim().ifEmpty { "{}" }
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return null
        return element as? JsonObject
    }

    private fun rejectUnknownKeys(obj: JsonObject, allowed: Set<String>): ArgResult.Fail? {
        val unknown = obj.keys.firstOrNull { it !in allowed } ?: return null
        return ArgResult.Fail(C.ERR_INVALID_ARGUMENTS, "不支持的参数字段：$unknown")
    }

    private fun requiredString(obj: JsonObject, key: String): ArgResult<String> {
        val prim = obj[key] as? JsonPrimitive ?: return invalid("缺少必填字段 $key")
        if (!prim.isString) return invalid("$key 必须是字符串")
        return ArgResult.Ok(prim.content)
    }

    private fun optionalString(obj: JsonObject, key: String): ArgResult<String?> {
        val el = obj[key] ?: return ArgResult.Ok(null)
        val prim = el as? JsonPrimitive ?: return invalid("$key 必须是字符串")
        if (!prim.isString) return invalid("$key 必须是字符串")
        return ArgResult.Ok(prim.content)
    }

    private fun requiredLong(obj: JsonObject, key: String): ArgResult<Long> {
        val prim = obj[key] as? JsonPrimitive ?: return invalid("缺少必填字段 $key")
        if (prim.isString) return invalid("$key 必须是整数")
        val v = prim.longOrNull ?: return invalid("$key 必须是整数")
        return ArgResult.Ok(v)
    }

    private fun optionalLong(obj: JsonObject, key: String): ArgResult<Long?> {
        val el = obj[key] ?: return ArgResult.Ok(null)
        val prim = el as? JsonPrimitive ?: return invalid("$key 必须是整数")
        if (prim.isString) return invalid("$key 必须是整数")
        val v = prim.longOrNull ?: return invalid("$key 必须是整数")
        return ArgResult.Ok(v)
    }

    private fun optionalBoolean(obj: JsonObject, key: String): ArgResult<Boolean?> {
        val el = obj[key] ?: return ArgResult.Ok(null)
        val prim = el as? JsonPrimitive ?: return invalid("$key 必须是布尔值")
        val v = prim.booleanOrNull ?: return invalid("$key 必须是布尔值")
        return ArgResult.Ok(v)
    }

    private fun optionalOffsetMillis(obj: JsonObject, key: String): ArgResult<Long?> {
        val el = obj[key] ?: return ArgResult.Ok(null)
        val prim = el as? JsonPrimitive ?: return invalid("$key 必须是字符串")
        if (!prim.isString) return invalid("$key 必须是字符串")
        val millis = CalendarTime.parseOffsetDateTimeToEpochMillis(prim.content)
            ?: return invalid("$key 必须是带时区偏移的 ISO-8601 时间")
        return ArgResult.Ok(millis)
    }

    private fun invalid(message: String): ArgResult.Fail = ArgResult.Fail(C.ERR_INVALID_ARGUMENTS, message)
}
