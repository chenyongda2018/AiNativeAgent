package com.yongda.ainativeagent.llm.net

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * 逐行读取 SSE（Server-Sent Events）响应流，对每个 `data:` 负载回调一次。
 *
 * OpenAI-compatible 流式协议约定：每行形如 `data: {json}`，以 `data: [DONE]` 结束。
 * 空行与非 data 行（注释 / event 字段）忽略。回调抛出的异常会向上传播；
 * 收集协程被取消时读取随之中断，实现「中断生成」。
 */
suspend fun HttpResponse.collectSseData(onData: suspend (String) -> Unit) {
    bodyAsChannel().collectSseData(onData)
}

suspend fun ByteReadChannel.collectSseData(onData: suspend (String) -> Unit) {
    val dataLines = mutableListOf<String>()

    suspend fun dispatch(): Boolean {
        if (dataLines.isEmpty()) return false
        val data = dataLines.joinToString("\n")
        dataLines.clear()
        if (data == "[DONE]") return true
        onData(data)
        return false
    }

    while (true) {
        val line = readUTF8Line()
        if (line == null) {
            dispatch()
            break
        }
        if (line.isEmpty()) {
            if (dispatch()) break
            continue
        }
        if (line.startsWith(":")) continue

        val separator = line.indexOf(':')
        val field = if (separator < 0) line else line.substring(0, separator)
        if (field != "data") continue

        var value = if (separator < 0) "" else line.substring(separator + 1)
        if (value.startsWith(' ')) value = value.substring(1)
        dataLines += value
    }
}
