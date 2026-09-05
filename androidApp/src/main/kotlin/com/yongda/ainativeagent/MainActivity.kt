package com.yongda.ainativeagent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yongda.ainativeagent.llm.core.ChatMessage
import com.yongda.ainativeagent.llm.deepseek.DeepSeekProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 仅用于冒烟调用的作用域，随 Activity 销毁取消；UI 阶段接入 ViewModel 后删除。
    private val smokeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }

        smokeTestLlm()
    }

    /** 临时：真机启动时发一句 prompt，把 DeepSeek 流式 token 打到 Logcat（tag=LlmSmoke）。UI 阶段删除。 */
    private fun smokeTestLlm() {
        val apiKey = BuildConfig.DEEPSEEK_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "DEEPSEEK_API_KEY 未配置：在 local.properties 加 DEEPSEEK_API_KEY=sk-xxx 后重新构建")
            return
        }

        val provider = DeepSeekProvider.withApiKey(apiKey)
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "你是一个简洁的助手。"),
            ChatMessage(ChatMessage.Role.USER, "用一句话介绍你自己。"),
        )

        smokeScope.launch {
            val reply = StringBuilder()
            try {
                Log.i(TAG, "---- LLM 流式开始 ----")
                provider.streamChat(messages).collect { delta ->
                    reply.append(delta)
                    Log.i(TAG, "delta=$delta")
                }
                Log.i(TAG, "---- LLM 流式结束，完整回复：$reply")
            } catch (e: Exception) {
                Log.e(TAG, "LLM 调用失败", e)
            }
        }
    }

    override fun onDestroy() {
        smokeScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LlmSmoke"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
