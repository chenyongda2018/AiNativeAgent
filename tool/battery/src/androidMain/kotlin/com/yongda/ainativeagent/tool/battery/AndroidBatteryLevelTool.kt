package com.yongda.ainativeagent.tool.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.yongda.ainativeagent.tool.core.ToolExecutionResult
import com.yongda.ainativeagent.tool.core.ToolResults
import kotlinx.coroutines.CancellationException

/**
 * 读取真实设备电量的 [BatteryLevelTool]。只持有 applicationContext；平台异常一律转结构化失败结果，
 * 不向上抛出导致聊天崩溃。协程取消（[CancellationException]）照常向上传播。
 */
class AndroidBatteryLevelTool(
    private val appContext: Context,
) : BatteryLevelTool {

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        return try {
            val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return unavailable()
            val level = normalizeBatteryLevel(
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            ) ?: return unavailable()
            val charging = isChargingFromStatus(readChargingStatus())
            ToolExecutionResult(
                contentJson = BatteryToolContract.successJson(level, charging),
                ok = true,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            unavailable()
        }
    }

    private fun readChargingStatus(): Int {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    }

    private fun unavailable(): ToolExecutionResult =
        ToolResults.failure(BatteryToolContract.ERROR_UNAVAILABLE, "Battery status is unavailable")
}
