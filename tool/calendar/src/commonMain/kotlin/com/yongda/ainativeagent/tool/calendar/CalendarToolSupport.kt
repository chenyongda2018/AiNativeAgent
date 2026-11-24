package com.yongda.ainativeagent.tool.calendar

/** 提供「当前时间」的抽象（epoch 毫秒）。生产用系统时钟，测试可固定，避免依赖真实时间。 */
fun interface CurrentTime {
    fun nowEpochMillis(): Long
}
