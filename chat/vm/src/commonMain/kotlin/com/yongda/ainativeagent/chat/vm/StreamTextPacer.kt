package com.yongda.ainativeagent.chat.vm

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_FRAME_INTERVAL = 16.milliseconds
private const val DEFAULT_CATCH_UP_FRAMES = 7
private const val DEFAULT_NORMAL_MAX_CODE_POINTS_PER_FRAME = 12
private const val DEFAULT_MAX_DRAIN_FRAMES = 30

/**
 * 将不规则的网络 chunk 转换成固定节奏的完整文本快照。
 *
 * 第一批内容立即显示；后续每帧根据积压量自适应追加字符，使大 chunk 不会整段跳出，
 * 同时把通常的显示延迟控制在约 [catchUpFrames] 帧。上游完成后仍按相同节奏排空缓冲区。
 */
internal fun Flow<String>.paceTextForUi(
    frameInterval: Duration = DEFAULT_FRAME_INTERVAL,
    catchUpFrames: Int = DEFAULT_CATCH_UP_FRAMES,
    normalMaxCodePointsPerFrame: Int = DEFAULT_NORMAL_MAX_CODE_POINTS_PER_FRAME,
    maxDrainFrames: Int = DEFAULT_MAX_DRAIN_FRAMES,
): Flow<String> = flow {
    require(!frameInterval.isNegative()) { "frameInterval must not be negative" }
    require(catchUpFrames > 0) { "catchUpFrames must be positive" }
    require(normalMaxCodePointsPerFrame > 0) { "normalMaxCodePointsPerFrame must be positive" }
    require(maxDrainFrames > 0) { "maxDrainFrames must be positive" }

    coroutineScope {
        val chunks = Channel<String>(Channel.BUFFERED)
        launch {
            try {
                this@paceTextForUi.collect { delta ->
                    if (delta.isNotEmpty()) chunks.send(delta)
                }
                chunks.close()
            } catch (error: Throwable) {
                chunks.close(error)
                throw error
            }
        }

        val received = StringBuilder()
        val visible = StringBuilder()
        var readIndex = 0
        var sourceCompleted = false
        var codePointBudget = 0

        while (!sourceCompleted || readIndex < received.length) {
            // 没有积压时挂起等待，避免空转；第一批内容到达后不额外等待一帧。
            if (readIndex >= received.length && !sourceCompleted) {
                val result = chunks.receiveCatching()
                result.exceptionOrNull()?.let { throw it }
                val chunk = result.getOrNull()
                if (chunk == null) sourceCompleted = true else received.append(chunk)
            }

            // 合并这一帧前已经到达的所有 chunk，网络频率不会直接驱动 Compose。
            while (!sourceCompleted) {
                val result = chunks.tryReceive()
                result.exceptionOrNull()?.let { throw it }
                val chunk = result.getOrNull()
                when {
                    chunk != null -> received.append(chunk)
                    result.isClosed -> sourceCompleted = true
                    else -> break
                }
            }

            val pendingCodeUnits = received.length - readIndex
            if (pendingCodeUnits > 0) {
                codePointBudget = maxOf(
                    codePointBudget,
                    (1 + (pendingCodeUnits - 1) / catchUpFrames)
                        .coerceAtMost(normalMaxCodePointsPerFrame),
                    1 + (pendingCodeUnits - 1) / maxDrainFrames,
                )
                val endIndex = received.advanceCodePoints(
                    startIndex = readIndex,
                    count = codePointBudget,
                    allowTrailingHighSurrogate = sourceCompleted,
                )
                if (endIndex > readIndex) {
                    visible.append(received.substring(readIndex, endIndex))
                    readIndex = endIndex
                    emit(visible.toString())
                }
            }

            if (readIndex >= received.length) {
                codePointBudget = 0
            }

            // 偶尔压缩已经消费的前缀，避免长回答让接收缓冲无限保留。
            if (readIndex >= 4_096 && readIndex >= received.length / 2) {
                received.deleteRange(0, readIndex)
                readIndex = 0
            }

            if (!sourceCompleted || readIndex < received.length) {
                delay(frameInterval)
            }
        }
    }
}

/** 前进指定数量的 code point，避免把 emoji 的 UTF-16 代理对拆到两帧。 */
private fun CharSequence.advanceCodePoints(
    startIndex: Int,
    count: Int,
    allowTrailingHighSurrogate: Boolean,
): Int {
    var index = startIndex
    var consumed = 0
    while (index < length && consumed < count) {
        val first = this[index]
        if (first.isHighSurrogate()) {
            if (index + 1 < length && this[index + 1].isLowSurrogate()) {
                index += 2
            } else if (allowTrailingHighSurrogate) {
                index++
            } else {
                break
            }
        } else {
            index++
        }
        consumed++
    }
    return index
}
