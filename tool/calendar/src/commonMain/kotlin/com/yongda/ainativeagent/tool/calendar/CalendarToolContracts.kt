package com.yongda.ainativeagent.tool.calendar

import com.yongda.ainativeagent.llm.core.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 四个日历工具的对外契约：名称、描述、参数 JSON Schema、错误码，以及结构化成功 JSON 构造。
 * Schema 声明形状与必填项供模型参考；真正的严格校验在 [CalendarArgs] 与各工具内完成。
 */
object CalendarToolContracts {

    const val QUERY: String = "query_calendar_events"
    const val CREATE: String = "create_calendar_event"
    const val UPDATE: String = "update_calendar_event"
    const val DELETE: String = "delete_calendar_event"

    // 错误码：稳定字符串，回填给模型也用于测试断言。
    const val ERR_INVALID_ARGUMENTS = "INVALID_ARGUMENTS"
    const val ERR_INVALID_TIME_RANGE = "INVALID_TIME_RANGE"
    const val ERR_PERMISSION_DENIED = "PERMISSION_DENIED"
    const val ERR_PERMISSION_PERMANENTLY_DENIED = "PERMISSION_PERMANENTLY_DENIED"
    const val ERR_PERMISSION_CANCELLED = "PERMISSION_CANCELLED"
    const val ERR_USER_CANCELLED = "USER_CANCELLED"
    const val ERR_NO_WRITABLE_CALENDAR = "NO_WRITABLE_CALENDAR"
    const val ERR_CALENDAR_NOT_FOUND = "CALENDAR_NOT_FOUND"
    const val ERR_EVENT_NOT_FOUND = "EVENT_NOT_FOUND"
    const val ERR_CALENDAR_ERROR = "CALENDAR_ERROR"

    const val DEFAULT_QUERY_LIMIT: Int = 20
    const val MAX_QUERY_LIMIT: Int = 50

    private const val TIME_HINT =
        "ISO-8601 且必须带时区偏移，如 2026-09-16T14:30:00+08:00 或 ...Z"

    val queryDefinition: ToolDefinition = ToolDefinition(
        name = QUERY,
        description = "查询设备日历中某时间区间内的日程（自动展开重复日程为多个实例）。只读，先申请读日历权限。",
        parametersJsonSchema = """
            {"type":"object","properties":{
              "timeMin":{"type":"string","description":"区间起（含），$TIME_HINT；省略默认当前时间"},
              "timeMax":{"type":"string","description":"区间止（含），$TIME_HINT；省略默认起点后 7 天"},
              "keyword":{"type":"string","description":"标题/描述/地点关键词，可选"},
              "limit":{"type":"integer","description":"最多返回条数 1-$MAX_QUERY_LIMIT，默认 $DEFAULT_QUERY_LIMIT"}
            },"additionalProperties":false}
        """.trimIndent(),
    )

    val createDefinition: ToolDefinition = ToolDefinition(
        name = CREATE,
        description = "在设备日历新增一条日程。写操作：会先向用户确认再申请写日历权限后落库。",
        parametersJsonSchema = """
            {"type":"object","properties":{
              "title":{"type":"string","description":"日程标题"},
              "description":{"type":"string"},
              "location":{"type":"string"},
              "allDay":{"type":"boolean","description":"是否全天，默认 false"},
              "start":{"type":"string","description":"开始时间，$TIME_HINT；非全天必填"},
              "end":{"type":"string","description":"结束时间，$TIME_HINT，须晚于 start；非全天必填"},
              "timeZone":{"type":"string","description":"IANA 时区 id，如 Asia/Shanghai；非全天必填"},
              "startDate":{"type":"string","description":"全天开始日期 YYYY-MM-DD；allDay=true 必填"},
              "endDate":{"type":"string","description":"全天结束日期 YYYY-MM-DD（含），可选，默认同 startDate"},
              "calendarId":{"type":"integer","description":"目标日历 id，可选；省略自动选主/首个可写日历"}
            },"required":["title"],"additionalProperties":false}
        """.trimIndent(),
    )

    val updateDefinition: ToolDefinition = ToolDefinition(
        name = UPDATE,
        description = "修改已存在的日程（先用 $QUERY 拿到 eventId）。至少给一个待改字段。重复日程将修改整个系列。写操作：先确认再申请写权限。",
        parametersJsonSchema = """
            {"type":"object","properties":{
              "eventId":{"type":"integer","description":"目标事件 id（来自查询结果）"},
              "title":{"type":"string"},
              "description":{"type":"string"},
              "location":{"type":"string"},
              "allDay":{"type":"boolean"},
              "start":{"type":"string","description":"$TIME_HINT"},
              "end":{"type":"string","description":"$TIME_HINT"},
              "timeZone":{"type":"string","description":"IANA 时区 id"},
              "startDate":{"type":"string","description":"全天开始日期 YYYY-MM-DD"},
              "endDate":{"type":"string","description":"全天结束日期 YYYY-MM-DD（含）"}
            },"required":["eventId"],"additionalProperties":false}
        """.trimIndent(),
    )

    val deleteDefinition: ToolDefinition = ToolDefinition(
        name = DELETE,
        description = "删除一条日程（先用 $QUERY 拿到 eventId）。重复日程将删除整个系列。写操作：先向用户确认再申请写权限。",
        parametersJsonSchema = """
            {"type":"object","properties":{
              "eventId":{"type":"integer","description":"目标事件 id（来自查询结果）"}
            },"required":["eventId"],"additionalProperties":false}
        """.trimIndent(),
    )

    fun querySuccessJson(events: List<CalendarEvent>): String = buildJsonObject {
        put("ok", true)
        put("count", events.size)
        put("events", buildJsonArray {
            events.forEach { e ->
                addJsonObject {
                    put("eventId", e.eventId)
                    put("title", e.title)
                    put("allDay", e.allDay)
                    if (e.allDay) {
                        put("startDate", CalendarTime.formatLocalDate(e.startMillis.floorDiv(CalendarTime.MILLIS_PER_DAY)))
                        // Provider 的全天事件 END 是「不含」的次日 UTC 零点；契约里 endDate 是「含」的当天，故减一天回显，
                        // 与 create/update 接受的 inclusive endDate 全链路一致。单日事件的 endDate 因此等于 startDate。
                        put("endDate", CalendarTime.formatLocalDate(e.endMillis.floorDiv(CalendarTime.MILLIS_PER_DAY) - 1))
                    } else {
                        put("startUtc", CalendarTime.formatUtcIso(e.startMillis))
                        put("endUtc", CalendarTime.formatUtcIso(e.endMillis))
                    }
                    put("startMillis", e.startMillis)
                    put("endMillis", e.endMillis)
                    e.timeZone?.let { put("timeZone", it) }
                    e.location?.let { put("location", it) }
                    e.calendarName?.let { put("calendar", it) }
                    put("recurring", e.isRecurring)
                }
            }
        })
    }.toString()

    fun createSuccessJson(eventId: Long, calendarName: String): String = buildJsonObject {
        put("ok", true)
        put("action", "created")
        put("eventId", eventId)
        put("calendar", calendarName)
    }.toString()

    fun updateSuccessJson(eventId: Long, changedLabels: List<String>): String = buildJsonObject {
        put("ok", true)
        put("action", "updated")
        put("eventId", eventId)
        put("updatedFields", buildJsonArray { changedLabels.forEach { add(it) } })
    }.toString()

    fun deleteSuccessJson(eventId: Long): String = buildJsonObject {
        put("ok", true)
        put("action", "deleted")
        put("eventId", eventId)
    }.toString()
}
