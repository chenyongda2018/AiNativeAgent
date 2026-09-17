package com.yongda.ainativeagent.tool.core

import com.yongda.ainativeagent.llm.core.ToolCall
import com.yongda.ainativeagent.llm.core.ToolDefinition
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRegistryTest {

    private class StubTool(name: String, private val json: String = """{"ok":true}""") : AgentTool {
        override val definition = ToolDefinition(name, "desc", """{"type":"object"}""")
        var calls = 0
        override suspend fun execute(argumentsJson: String): ToolExecutionResult {
            calls++
            return ToolExecutionResult(json, ok = true)
        }
    }

    @Test
    fun exposesDefinitionsInRegistrationOrder() {
        val registry = ToolRegistry(listOf(StubTool("a"), StubTool("b")))
        assertEquals(listOf("a", "b"), registry.definitions().map { it.name })
    }

    @Test
    fun dispatchesToNamedTool() = runTest {
        val tool = StubTool("battery")
        val registry = ToolRegistry(listOf(tool))
        val result = registry.execute(ToolCall("id", "battery", "{}"))
        assertTrue(result.ok)
        assertEquals(1, tool.calls)
    }

    @Test
    fun unknownToolReturnsStructuredFailure() = runTest {
        val registry = ToolRegistry(listOf(StubTool("battery")))
        val result = registry.execute(ToolCall("id", "ghost", "{}"))
        assertFalse(result.ok)
        assertEquals(ToolRegistry.ERROR_UNKNOWN_TOOL, result.errorCode)
    }

    @Test
    fun duplicateToolNamesAreRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(StubTool("dup"), StubTool("dup")))
        }
    }
}
