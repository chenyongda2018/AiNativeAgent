package com.yongda.ainativeagent.tool.core

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolConfirmationControllerTest {

    @Test
    fun publishesPendingThenResolvesConfirmed() = runTest {
        val controller = ToolConfirmationController()
        val request = ToolConfirmationRequest(
            title = "新增日程",
            fields = listOf(
                ConfirmationField("标题", "会议"),
                ConfirmationField("时间", "明天 10:00"),
                ConfirmationField("目标日历", "个人"),
            ),
        )

        val deferred = async { controller.confirm(request) }
        yield()
        assertEquals(request, controller.pending.value)

        controller.confirm()
        assertEquals(ConfirmationDecision.CONFIRMED, deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun cancelResolvesCancelledAndClearsPending() = runTest {
        val controller = ToolConfirmationController()
        val deferred = async {
            controller.confirm(
                ToolConfirmationRequest(
                    title = "删除日程",
                    fields = listOf(ConfirmationField("标题", "会议")),
                    confirmLabel = "删除",
                    destructive = true,
                ),
            )
        }
        yield()
        assertTrue(controller.pending.value?.destructive == true)

        controller.cancel()
        assertEquals(ConfirmationDecision.CANCELLED, deferred.await())
        assertNull(controller.pending.value)
    }

    @Test
    fun cancellingAwaiterClearsPending() = runTest {
        val controller = ToolConfirmationController()
        val deferred = async {
            controller.confirm(
                ToolConfirmationRequest(
                    title = "新增日程",
                    fields = listOf(ConfirmationField("标题", "x")),
                ),
            )
        }
        yield()
        assertTrue(controller.pending.value != null)

        deferred.cancel()
        yield()
        assertNull(controller.pending.value)
    }
}
