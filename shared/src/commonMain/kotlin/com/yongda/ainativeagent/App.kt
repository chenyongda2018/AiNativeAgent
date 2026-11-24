package com.yongda.ainativeagent

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yongda.ainativeagent.agent.runtime.AgentLoopExecutor
import com.yongda.ainativeagent.agent.runtime.PrintlnToolTrace
import com.yongda.ainativeagent.chat.ui.ChatModel
import com.yongda.ainativeagent.chat.ui.ChatScreen
import com.yongda.ainativeagent.chat.ui.theme.ChatTheme
import com.yongda.ainativeagent.chat.vm.ChatViewModel
import com.yongda.ainativeagent.llm.deepseek.DeepSeekModelInfo
import com.yongda.ainativeagent.llm.deepseek.DeepSeekModels
import com.yongda.ainativeagent.llm.deepseek.DeepSeekProvider
import com.yongda.ainativeagent.llm.net.createLlmHttpClient
import com.yongda.ainativeagent.tool.core.PermissionRationaleController
import com.yongda.ainativeagent.tool.core.ToolConfirmationController
import com.yongda.ainativeagent.tool.core.ToolRegistry
import io.ktor.client.HttpClient

/**
 * App 入口：装配 DeepSeek provider + 多步 Agent Loop（面向 [ToolRegistry] 的工具集合）+ ChatViewModel +
 * 纯 UI 的 ChatScreen，并在其上按需叠加应用级的通用操作确认弹窗与权限教育弹窗。
 *
 * 平台相关依赖（工具注册表、通用确认控制器、权限教育控制器、共享 [HttpClient]、设备时间上下文）由宿主注入；
 * 本 Composable 与 commonMain 都不直接触碰 Android `Context` / `Activity` 或具体工具模块。
 */
@Composable
fun App(
    apiKey: String,
    registry: ToolRegistry = ToolRegistry(emptyList()),
    confirmationController: ToolConfirmationController? = null,
    rationaleController: PermissionRationaleController? = null,
    httpClient: HttpClient? = null,
    timeContextProvider: () -> String? = { null },
    systemPrompt: String = ANDROID_AGENT_SYSTEM_PROMPT,
    toolLabelResolver: (String) -> String = ::defaultToolLabel,
) {
    val ownedClient = remember(httpClient) { if (httpClient == null) createLlmHttpClient() else null }
    if (ownedClient != null) {
        DisposableEffect(ownedClient) {
            onDispose { ownedClient.close() }
        }
    }
    val effectiveClient = httpClient ?: requireNotNull(ownedClient)
    val availableModels = remember { DeepSeekModels.all.map { it.toChatModel() } }

    ChatTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ChatTheme.colors.background,
        ) {
            val vm: ChatViewModel = viewModel {
                ChatViewModel(
                    engineFactory = { modelId ->
                        AgentLoopExecutor(
                            provider = DeepSeekProvider.withApiKey(
                                apiKey = apiKey,
                                model = modelId,
                                client = effectiveClient,
                            ),
                            registry = registry,
                            trace = PrintlnToolTrace(),
                        )
                    },
                    initialModelId = DeepSeekModels.default.id,
                    availableModels = availableModels,
                    systemPrompt = systemPrompt,
                    timeContextProvider = timeContextProvider,
                    toolLabelResolver = toolLabelResolver,
                )
            }
            val state by vm.state.collectAsStateWithLifecycle()
            ChatScreen(
                state = state,
                onSend = vm::send,
                onCancel = vm::cancel,
                onRetry = vm::retry,
                onSelectModel = vm::selectModel,
                onNewChat = vm::newChat,
            )

            confirmationController?.let { controller ->
                val pending by controller.pending.collectAsStateWithLifecycle()
                ToolConfirmationDialog(
                    request = pending,
                    onConfirm = { controller.confirm() },
                    onDismiss = { controller.cancel() },
                )
            }

            rationaleController?.let { controller ->
                val rationale by controller.pending.collectAsStateWithLifecycle()
                PermissionRationaleDialog(
                    request = rationale,
                    onProceed = { controller.proceed() },
                    onDismiss = { controller.dismiss() },
                )
            }
        }
    }
}

internal fun DeepSeekModelInfo.toChatModel(): ChatModel = ChatModel(
    id = id,
    name = name,
    badge = badge,
    tagline = tagline,
    contextLimit = contextLimit,
    thinkingSupported = thinkingSupported,
)

internal fun defaultToolLabel(toolName: String): String = when (toolName) {
    "get_battery_level" -> "读取手机电量"
    "query_calendar_events" -> "查询日程"
    "create_calendar_event" -> "新增日程"
    "update_calendar_event" -> "修改日程"
    "delete_calendar_event" -> "删除日程"
    else -> toolName
}
