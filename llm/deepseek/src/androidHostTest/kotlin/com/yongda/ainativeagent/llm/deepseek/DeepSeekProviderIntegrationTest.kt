package com.yongda.ainativeagent.llm.deepseek

import com.yongda.ainativeagent.llm.core.ChatMessage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 真联网验收：对 DeepSeek 发一句 prompt，逐 token 流式打印并断言非空。
 *
 * key 解析顺序（都不入库）：
 *   1) 环境变量 DEEPSEEK_API_KEY（CI 用）
 *   2) 仓库根的 local.properties 里的 DEEPSEEK_API_KEY（本地 / Android Studio 点绿色按钮直接可用）
 * 两者都没有时自动跳过，不阻断构建。
 */
class DeepSeekProviderIntegrationTest {

    @Test
    fun streamChat_realNetwork_printsTokens() {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) {
            println(
                "[skip] 未找到 DEEPSEEK_API_KEY（环境变量或 local.properties 均无），跳过真联网测试。",
            )
            return
        }

        val provider = DeepSeekProvider.withApiKey(apiKey)
        val messages = listOf(
            ChatMessage(ChatMessage.Role.SYSTEM, "你是一个简洁的助手。"),
            ChatMessage(ChatMessage.Role.USER, "用一句话介绍你自己。"),
        )

        val reply = StringBuilder()
        runBlocking {
            provider.streamChat(messages).collect { delta ->
                print(delta)
                System.out.flush()
                reply.append(delta)
            }
        }
        println()

        assertTrue(reply.isNotEmpty(), "流式回复不应为空")
    }

    /** 先查环境变量，再从工作目录向上逐级找 local.properties。 */
    private fun resolveApiKey(): String {
        System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            val f = File(dir, "local.properties")
            if (f.exists()) {
                val key = Properties().apply { f.inputStream().use { load(it) } }
                    .getProperty("DEEPSEEK_API_KEY")
                if (!key.isNullOrBlank()) return key
            }
            dir = dir.parentFile
        }
        return ""
    }
}

