package com.yongda.ainativeagent.tool.battery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatteryToolLogicTest {

    @Test
    fun normalizesValidPercentages() {
        assertEquals(0, normalizeBatteryLevel(0))
        assertEquals(1, normalizeBatteryLevel(1))
        assertEquals(50, normalizeBatteryLevel(50))
        assertEquals(100, normalizeBatteryLevel(100))
    }

    @Test
    fun rejectsOutOfRangeAndUnknownValuesInsteadOfFabricating() {
        assertNull(normalizeBatteryLevel(-1))
        assertNull(normalizeBatteryLevel(101))
        assertNull(normalizeBatteryLevel(Int.MIN_VALUE))
        assertNull(normalizeBatteryLevel(Int.MAX_VALUE))
    }

    @Test
    fun mapsChargingStatusCorrectly() {
        assertTrue(isChargingFromStatus(2))
        assertTrue(isChargingFromStatus(5))
        assertFalse(isChargingFromStatus(3))
        assertFalse(isChargingFromStatus(4))
        assertFalse(isChargingFromStatus(1))
        assertFalse(isChargingFromStatus(0))
    }

    @Test
    fun buildsSuccessJson() {
        assertEquals(
            "{\"ok\":true,\"levelPercent\":73,\"isCharging\":true}",
            BatteryToolContract.successJson(73, true),
        )
    }
}
