package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarPermissionGatewayTest {

    private class FakeHost(val result: PermissionResult = PermissionResult.GRANTED) : PermissionHost {
        val calls = mutableListOf<CalendarPermission>()
        override suspend fun request(permission: CalendarPermission): PermissionResult {
            calls += permission
            return result
        }
    }

    @Test
    fun noAttachedHostReturnsCancelled() = runTest {
        val gateway = CalendarPermissionGateway()
        assertEquals(PermissionResult.CANCELLED, gateway.request(CalendarPermission.READ))
    }

    @Test
    fun delegatesToAttachedHost() = runTest {
        val gateway = CalendarPermissionGateway()
        val host = FakeHost(PermissionResult.GRANTED)
        gateway.attach(host)
        assertEquals(PermissionResult.GRANTED, gateway.request(CalendarPermission.WRITE))
        assertEquals(listOf(CalendarPermission.WRITE), host.calls)
    }

    @Test
    fun detachStopsDelegation() = runTest {
        val gateway = CalendarPermissionGateway()
        val host = FakeHost()
        gateway.attach(host)
        gateway.detach(host)
        assertEquals(PermissionResult.CANCELLED, gateway.request(CalendarPermission.READ))
    }

    @Test
    fun recreationSwapsToNewHost() = runTest {
        // 模拟 Activity 重建：旧 host detach、新 host attach，网关实例不变（retained VM 持有的正是它）。
        val gateway = CalendarPermissionGateway()
        val old = FakeHost(PermissionResult.DENIED)
        gateway.attach(old)
        gateway.detach(old)
        val fresh = FakeHost(PermissionResult.GRANTED)
        gateway.attach(fresh)

        assertEquals(PermissionResult.GRANTED, gateway.request(CalendarPermission.READ))
        assertEquals(0, old.calls.size)
        assertEquals(listOf(CalendarPermission.READ), fresh.calls)
    }

    @Test
    fun staleDetachDoesNotClearNewerHost() = runTest {
        val gateway = CalendarPermissionGateway()
        val old = FakeHost(PermissionResult.DENIED)
        val fresh = FakeHost(PermissionResult.GRANTED)
        gateway.attach(old)
        gateway.attach(fresh) // 新 host 成为当前
        gateway.detach(old)   // 旧 host 的迟到 detach 不应清掉新 host

        assertEquals(PermissionResult.GRANTED, gateway.request(CalendarPermission.READ))
        assertEquals(listOf(CalendarPermission.READ), fresh.calls)
    }

    @Test
    fun serializesConcurrentRequests() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gatedHost = object : PermissionHost {
            var inFlight = 0
            var maxInFlight = 0
            override suspend fun request(permission: CalendarPermission): PermissionResult {
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                gate.await()
                inFlight--
                return PermissionResult.GRANTED
            }
        }
        val gateway = CalendarPermissionGateway()
        gateway.attach(gatedHost)

        val a = async { gateway.request(CalendarPermission.READ) }
        val b = async { gateway.request(CalendarPermission.WRITE) }
        yield()
        // Mutex 串行化：任一时刻只有一个申请进入宿主。
        assertEquals(1, gatedHost.maxInFlight)
        gate.complete(Unit)
        awaitAll(a, b)
        assertEquals(1, gatedHost.maxInFlight)
    }
}
