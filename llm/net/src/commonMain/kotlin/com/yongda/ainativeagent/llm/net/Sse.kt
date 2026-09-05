package com.yongda.ainativeagent.llm.net

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line

/**
 * 逐行读取 SSE（Server-Sent Events）响应流，对每个 `data:` 负载回调一次。
 *
 * OpenAI-compatible 流式协议约定：每行形如 `data: {json}`，以 `data: [DONE]` 结束。
 * 空行与非 data 行（注释 / event 字段）忽略。回调抛出的异常会向上传播；
 * 收集协程被取消时读取随之中断，实现「中断生成」。
 */
suspend fun HttpResponse.collectSseData(onData: suspend (String) -> Unit) {
    val channel = bodyAsChannel()
    while (true) {
        val line = channel.readUTF8Line() ?: break
        if (line.isBlank() || !line.startsWith("data:")) continue
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") break
        onData(data)
    }
}
