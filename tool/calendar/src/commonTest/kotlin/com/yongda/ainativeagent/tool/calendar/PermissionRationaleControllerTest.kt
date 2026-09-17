package com.yongda.ainativeagent.tool.calendar

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PermissionRationaleControllerTest {

    @Test
    fun proceedResolvesTrueAndClearsPending() = runTest {
        val controller = PermissionRationaleController()
        val deferred = async { controller.requestRationale(CalendarPermission.READ) }
        yield()
        assertEquals(CalendarPermission.READ, controller.pending.value?.permission)

        controller.proceed()
        assertTrue(deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun dismissResolvesFalseAndClearsPending() = runTest {
        val controller = PermissionRationaleController()
        val deferred = async { controller.requestRationale(CalendarPermission.WRITE) }
        yield()
        assertTrue(controller.pending.value != null)

        controller.dismiss()
        assertEquals(false, deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun cancellingAwaiterClearsPending() = runTest {
        val controller = PermissionRationaleController()
        val deferred = async { controller.requestRationale(CalendarPermission.READ) }
        yield()
        assertTrue(controller.pending.value != null)

        deferred.cancel()
        yield()
        assertNull(controller.pending.value)
    }
}
