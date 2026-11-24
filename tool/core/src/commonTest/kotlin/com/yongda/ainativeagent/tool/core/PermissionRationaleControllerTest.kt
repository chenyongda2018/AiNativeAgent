package com.yongda.ainativeagent.tool.core

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PermissionRationaleControllerTest {

    private val readPerm = ToolPermission(
        id = "test.read",
        label = "读取测试",
        rationaleTitle = "需要读取权限",
        rationaleMessage = "用于测试读取权限说明",
    )

    private val writePerm = ToolPermission(
        id = "test.write",
        label = "写入测试",
        rationaleTitle = "需要写入权限",
        rationaleMessage = "用于测试写入权限说明",
    )

    @Test
    fun proceedResolvesTrueAndClearsPending() = runTest {
        val controller = PermissionRationaleController()
        val deferred = async { controller.requestRationale(readPerm) }
        yield()
        assertEquals(readPerm, controller.pending.value?.permission)

        controller.proceed()
        assertTrue(deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun dismissResolvesFalseAndClearsPending() = runTest {
        val controller = PermissionRationaleController()
        val deferred = async { controller.requestRationale(writePerm) }
        yield()
        assertTrue(controller.pending.value != null)

        controller.dismiss()
        assertEquals(false, deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun cancellingAwaiterClearsPending() = runTest {
        val controller = PermissionRationaleController()
        val deferred = async { controller.requestRationale(readPerm) }
        yield()
        assertTrue(controller.pending.value != null)

        deferred.cancel()
        yield()
        assertNull(controller.pending.value)
    }
}
