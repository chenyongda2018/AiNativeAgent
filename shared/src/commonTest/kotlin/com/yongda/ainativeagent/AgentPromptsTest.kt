package com.yongda.ainativeagent

import com.yongda.ainativeagent.tool.core.ToolRegistry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentPromptsTest {

    @Test
    fun testAndroidPromptContainsCalendarAndBatteryCapabilities() {
        assertTrue(ANDROID_AGENT_SYSTEM_PROMPT.contains("Android"))
        assertTrue(ANDROID_AGENT_SYSTEM_PROMPT.contains("电量"))
        assertTrue(ANDROID_AGENT_SYSTEM_PROMPT.contains("日历"))
    }

    @Test
    fun testIosPromptDeclaresEnvironmentAndDisclaimsDeviceTools() {
        assertTrue(IOS_CHAT_SYSTEM_PROMPT.contains("iOS"))
        assertTrue(IOS_CHAT_SYSTEM_PROMPT.contains("暂未接入 iOS 本地设备工具"))
        assertTrue(IOS_CHAT_SYSTEM_PROMPT.contains("不支持直接操作设备日历或读取电量"))
        assertFalse(IOS_CHAT_SYSTEM_PROMPT.contains("query_calendar_events"))
    }

    @Test
    fun testEmptyToolRegistryDefinitionsAreEmpty() {
        val registry = ToolRegistry(emptyList())
        assertTrue(registry.definitions().isEmpty())
    }
}
