package com.yongda.ainativeagent

import com.yongda.ainativeagent.llm.deepseek.DeepSeekModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun mapsDeepSeekModelsToUiWithThinkingEnabled() {
        val models = DeepSeekModels.all.map { it.toChatModel() }
        assertTrue(models.isNotEmpty())
        assertEquals(DeepSeekModels.default.id, models.first().id)
        assertTrue(models.all { it.thinkingSupported })
    }

    @Test
    fun resolvesKnownToolLabelsAndFallsBackToRawName() {
        assertEquals("读取手机电量", defaultToolLabel("get_battery_level"))
        assertEquals("查询日程", defaultToolLabel("query_calendar_events"))
        assertEquals("新增日程", defaultToolLabel("create_calendar_event"))
        assertEquals("修改日程", defaultToolLabel("update_calendar_event"))
        assertEquals("删除日程", defaultToolLabel("delete_calendar_event"))
        assertEquals("custom_tool", defaultToolLabel("custom_tool"))
    }
}
