package com.yongda.ainativeagent.tool.battery

import com.yongda.ainativeagent.tool.core.ToolExecutionResult

interface BatteryLevelTool {
    suspend fun execute(argumentsJson: String): ToolExecutionResult
}
