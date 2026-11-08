package com.yongda.ainativeagent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yongda.ainativeagent.chat.ui.ChatModels
import com.yongda.ainativeagent.chat.ui.ChatScreen
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.chat.vm.AgentLoopExecutor
import com.yongda.ainativeagent.chat.vm.ChatViewModel
import com.yongda.ainativeagent.chat.vm.PrintlnToolTrace
import com.yongda.ainativeagent.llm.deepseek.DeepSeekProvider
import com.yongda.ainativeagent.tool.calendar.CalendarConfirmationController
import com.yongda.ainativeagent.tool.calendar.PermissionRationaleController
import com.yongda.ainativeagent.tool.core.ToolRegistry

/**
 * App 入口：装配 DeepSeek provider + 多步 Agent Loop（面向 [ToolRegistry] 的工具集合）+ ChatViewModel +
 * 纯 UI 的 ChatScreen，并在其上叠加应用级的日历写操作确认弹窗与权限教育弹窗。
 *
 * 平台相关依赖（工具注册表、确认控制器、权限教育控制器、设备时间上下文）全部由宿主 [MainActivity] 从进程级
 * 依赖图注入；本 Composable 与 commonMain 都不直接触碰 Android `Context` / `Activity`。这些控制器长期存活，
 * 配置变更（旋转 / 深色切换 / Activity 重建）后 retained ViewModel 与新 UI 仍观察同一实例，确认/权限流不丢。
 *
 * @param apiKey DeepSeek API key，由 androidApp 的 BuildConfig 注入，不入库。
 * @param registry 已装配好的工具注册表（电量 + 日历 CRUD）。
 * @param confirmationController 日历写操作确认的「工具 ↔ UI」桥；本函数观察其状态渲染确认弹窗。
 * @param rationaleController 权限教育（rationale）的「宿主 ↔ UI」桥；本函数观察其状态渲染说明弹窗。
 * @param timeContextProvider 每轮生成前提供「设备当前时间 / 时区」上下文，供模型处理相对时间。
 * @param systemPrompt 模型系统人设，默认为 Android 平台设定。
 */
@Composable
fun App(
    apiKey: String,
    registry: ToolRegistry,
    confirmationController: CalendarConfirmationController,
    rationaleController: PermissionRationaleController,
    timeContextProvider: () -> String? = { null },
    systemPrompt: String = ANDROID_AGENT_SYSTEM_PROMPT,
) {
    ChatTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ChatTheme.colors.background,
        ) {
            val vm: ChatViewModel = viewModel {
                ChatViewModel(
                    engineFactory = { modelId ->
                        AgentLoopExecutor(
                            provider = DeepSeekProvider.withApiKey(apiKey, model = modelId),
                            registry = registry,
                            trace = PrintlnToolTrace(),
                        )
                    },
                    initialModelId = ChatModels.default.id,
                    systemPrompt = systemPrompt,
                    timeContextProvider = timeContextProvider,
                )
            }
            val state by vm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onSend = vm::send,
                onCancel = vm::cancel,
                onRetry = vm::retry,
                onSelectModel = vm::selectModel,
            )

            val pending by confirmationController.pending.collectAsStateWithLifecycle()
            CalendarConfirmationDialog(
                request = pending,
                onConfirm = { confirmationController.confirm() },
                onDismiss = { confirmationController.cancel() },
            )

            val rationale by rationaleController.pending.collectAsStateWithLifecycle()
            PermissionRationaleDialog(
                request = rationale,
                onProceed = { rationaleController.proceed() },
                onDismiss = { rationaleController.dismiss() },
            )
        }
    }
}
