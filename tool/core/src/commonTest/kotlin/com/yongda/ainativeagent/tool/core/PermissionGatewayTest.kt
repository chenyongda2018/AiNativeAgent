package com.yongda.ainativeagent.tool.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionGatewayTest {

    private val readPerm = ToolPermission("test.read", "读", "需要读", "说明")
    private val writePerm = ToolPermission("test.write", "写", "需要写", "说明")

    private class FakeHost(val result: PermissionResult = PermissionResult.GRANTED) : PermissionHost {
        val calls = mutableListOf<ToolPermission>()
        override suspend fun request(permission: ToolPermission): PermissionResult {
            calls += permission
            return result
        }
    }

    @Test
    fun noAttachedHostReturnsCancelled() = runTest {
        val gateway = PermissionGateway()
        assertEquals(PermissionResult.CANCELLED, gateway.request(readPerm))
    }

    @Test
    fun delegatesToAttachedHost() = runTest {
        val gateway = PermissionGateway()
        val host = FakeHost(PermissionResult.GRANTED)
        gateway.attach(host)
        assertEquals(PermissionResult.GRANTED, gateway.request(writePerm))
        assertEquals(listOf(writePerm), host.calls)
    }

    @Test
    fun detachStopsDelegation() = runTest {
        val gateway = PermissionGateway()
        val host = FakeHost()
        gateway.attach(host)
        gateway.detach(host)
        assertEquals(PermissionResult.CANCELLED, gateway.request(readPerm))
    }

    @Test
    fun recreationSwapsToNewHost() = runTest {
        // 模拟 Activity 重建：旧 host detach、新 host attach，网关实例不变（retained VM 持有的正是它）。
        val gateway = PermissionGateway()
        val old = FakeHost(PermissionResult.DENIED)
        gateway.attach(old)
        gateway.detach(old)
        val fresh = FakeHost(PermissionResult.GRANTED)
        gateway.attach(fresh)

        assertEquals(PermissionResult.GRANTED, gateway.request(readPerm))
        assertEquals(0, old.calls.size)
        assertEquals(listOf(readPerm), fresh.calls)
    }

    @Test
    fun staleDetachDoesNotClearNewerHost() = runTest {
        val gateway = PermissionGateway()
        val old = FakeHost(PermissionResult.DENIED)
        val fresh = FakeHost(PermissionResult.GRANTED)
        gateway.attach(old)
        gateway.attach(fresh)
        gateway.detach(old)

        assertEquals(PermissionResult.GRANTED, gateway.request(readPerm))
        assertEquals(listOf(readPerm), fresh.calls)
    }

    @Test
    fun serializesConcurrentRequests() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gatedHost = object : PermissionHost {
            var inFlight = 0
            var maxInFlight = 0
            override suspend fun request(permission: ToolPermission): PermissionResult {
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                gate.await()
                inFlight--
                return PermissionResult.GRANTED
            }
        }
        val gateway = PermissionGateway()
        gateway.attach(gatedHost)

        val a = async { gateway.request(readPerm) }
        val b = async { gateway.request(writePerm) }
        yield()
        assertEquals(1, gatedHost.maxInFlight)
        gate.complete(Unit)
        awaitAll(a, b)
        assertEquals(1, gatedHost.maxInFlight)
    }
}
