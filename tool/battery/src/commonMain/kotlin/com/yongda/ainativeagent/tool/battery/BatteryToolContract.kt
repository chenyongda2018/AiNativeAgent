package com.yongda.ainativeagent.tool.battery

import com.yongda.ainativeagent.llm.core.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object BatteryToolContract {

    const val NAME: String = "get_battery_level"

    const val DESCRIPTION: String = "获取当前 Android 设备的电量百分比与充电状态"

    const val PARAMETERS_JSON_SCHEMA: String =
        """{"type":"object","properties":{},"additionalProperties":false}"""

    const val ERROR_UNAVAILABLE: String = "BATTERY_UNAVAILABLE"

    val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = DESCRIPTION,
        parametersJsonSchema = PARAMETERS_JSON_SCHEMA,
    )

    fun successJson(levelPercent: Int, isCharging: Boolean): String = buildJsonObject {
        put("ok", true)
        put("levelPercent", levelPercent)
        put("isCharging", isCharging)
    }.toString()
}

/** 归一化电量百分比：仅接受 0..100，其余（未知/-1/越界）返回 null，绝不伪造成 0。 */
fun normalizeBatteryLevel(raw: Int): Int? = raw.takeIf { it in 0..100 }

/**
 * Android BatteryManager 的充电状态整型 → 是否正在通过电源充电。
 * 对齐 BatteryManager.BATTERY_STATUS_*：CHARGING=2、FULL=5 视为在充电；DISCHARGING=3、
 * NOT_CHARGING=4、UNKNOWN=1 及其它未知值视为未充电。
 */
fun isChargingFromStatus(status: Int): Boolean = when (status) {
    BATTERY_STATUS_CHARGING, BATTERY_STATUS_FULL -> true
    else -> false
}

private const val BATTERY_STATUS_CHARGING = 2
private const val BATTERY_STATUS_FULL = 5
