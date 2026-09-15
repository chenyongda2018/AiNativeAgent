package com.yongda.ainativeagent.tool.core

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object ToolResults {

    fun failureJson(code: String, message: String): String = buildJsonObject {
        put("ok", false)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }.toString()

    fun failure(code: String, message: String): ToolExecutionResult =
        ToolExecutionResult(contentJson = failureJson(code, message), ok = false, errorCode = code)
}
