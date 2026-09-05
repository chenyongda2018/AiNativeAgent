package com.yongda.ainativeagent

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform