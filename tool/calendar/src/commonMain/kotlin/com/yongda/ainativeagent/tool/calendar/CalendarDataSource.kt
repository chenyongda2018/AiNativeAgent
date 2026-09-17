package com.yongda.ainativeagent.tool.calendar

/**
 * 日历数据访问抽象。commonMain 只依赖它，androidMain 用 ContentResolver 实现；测试用 fake，绝不触碰真实日历。
 *
 * 实现约定：所有方法在 IO 调度器执行；平台异常应转为抛出（由工具捕获成结构化错误），协程取消照常传播。
 */
interface CalendarDataSource {

    /** 当前账户下可写的日历列表（用于新增时选目标；空列表表示没有可写日历）。 */
    suspend fun writableCalendars(): List<CalendarInfo>

    /** 通过 Instances 查询区间内的事件实例（展开重复日程），可选关键词过滤，按开始时间升序，最多 [EventQuery.limit] 条。 */
    suspend fun queryEvents(query: EventQuery): List<CalendarEvent>

    /** 按 eventId 读取单个事件（Events 行）；不存在返回 null。用于更新/删除前解析目标与差异。 */
    suspend fun getEvent(eventId: Long): CalendarEvent?

    /** 新增事件，返回新事件 id；失败抛异常。 */
    suspend fun createEvent(draft: EventDraft): Long

    /** 覆盖更新指定事件的全部可写字段；返回受影响行数是否 > 0。 */
    suspend fun updateEvent(eventId: Long, draft: EventDraft): Boolean

    /** 删除指定事件（重复日程为整系列）；返回受影响行数是否 > 0。 */
    suspend fun deleteEvent(eventId: Long): Boolean
}
