package com.yongda.ainativeagent.llm.net

import kotlin.test.Test
import kotlin.test.assertTrue

class LlmHttpClientIosTest {

    @Test
    fun testCreateAndCloseClientRunsConfigureCallback() {
        var callbackExecuted = false
        val client = createLlmHttpClient {
            callbackExecuted = true
        }
        try {
            assertTrue(callbackExecuted, "Expected configure callback to be executed")
        } finally {
            client.close()
        }
    }
}
