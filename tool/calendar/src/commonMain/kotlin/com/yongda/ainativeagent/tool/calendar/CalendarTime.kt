package com.yongda.ainativeagent.tool.calendar

/**
 * 纯 Kotlin 的日历时间处理：解析模型给出的「带时区偏移」时间字符串到 epoch 毫秒（UTC），全天日期处理、
 * 区间校验、以及 epoch 毫秒 → UTC ISO-8601 回显。不依赖任何平台 API，可在 commonTest 直接单测。
 *
 * 采用 Howard Hinnant 的 civil ⇆ days 算法做无依赖的日期换算，覆盖闰年与负纪元。
 */
object CalendarTime {

    const val MILLIS_PER_DAY: Long = 86_400_000L

    private val OFFSET_DATE_TIME = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})[Tt ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?" +
            "(Z|z|[+-]\\d{2}:?\\d{2}|[+-]\\d{2})$",
    )

    private val LOCAL_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    /**
     * 解析带时区偏移的日期时间到 epoch 毫秒（UTC）。示例：
     *   2026-09-16T14:30:00+08:00 · 2026-09-16T14:30:00Z · 2026-09-16 14:30+0800 · 2026-09-16T14:30:00.500-05:00
     * 必须带偏移（Z 或 ±HH[:]MM 或 ±HH），避免时区歧义；无法解析或字段越界返回 null。
     */
    fun parseOffsetDateTimeToEpochMillis(raw: String): Long? {
        val m = OFFSET_DATE_TIME.matchEntire(raw.trim()) ?: return null
        val (yStr, moStr, dStr, hStr, minStr, sStr, fracStr, offStr) = m.destructured
        val year = yStr.toInt()
        val month = moStr.toInt()
        val day = dStr.toInt()
        val hour = hStr.toInt()
        val minute = minStr.toInt()
        val second = if (sStr.isEmpty()) 0 else sStr.toInt()
        if (!isValidDate(year, month, day)) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null

        val offsetSeconds = parseOffsetSeconds(offStr) ?: return null
        val fracMillis = fractionToMillis(fracStr)

        val days = daysFromCivil(year, month, day)
        val localSeconds = days * 86_400L + hour * 3_600L + minute * 60L + second
        val epochSeconds = localSeconds - offsetSeconds
        return epochSeconds * 1_000L + fracMillis
    }

    /** 解析 YYYY-MM-DD 为「距 1970-01-01 的天数」。越界/非法返回 null。 */
    fun parseLocalDateToEpochDay(raw: String): Long? {
        val m = LOCAL_DATE.matchEntire(raw.trim()) ?: return null
        val (yStr, moStr, dStr) = m.destructured
        val year = yStr.toInt()
        val month = moStr.toInt()
        val day = dStr.toInt()
        if (!isValidDate(year, month, day)) return null
        return daysFromCivil(year, month, day)
    }

    /** epoch 毫秒 → UTC ISO-8601（秒精度，带 Z）。用于把查询结果稳定回显给模型。 */
    fun formatUtcIso(epochMillis: Long): String {
        val totalSeconds = epochMillis.floorDiv(1_000L)
        val days = totalSeconds.floorDiv(86_400L)
        val secondOfDay = totalSeconds.mod(86_400L).toInt()
        val (y, mo, d) = civilFromDays(days)
        val hh = secondOfDay / 3_600
        val mm = (secondOfDay % 3_600) / 60
        val ss = secondOfDay % 60
        return "${pad4(y)}-${pad2(mo)}-${pad2(d)}T${pad2(hh)}:${pad2(mm)}:${pad2(ss)}Z"
    }

    /** epoch 天数 → YYYY-MM-DD（全天事件回显）。 */
    fun formatLocalDate(epochDay: Long): String {
        val (y, mo, d) = civilFromDays(epochDay)
        return "${pad4(y)}-${pad2(mo)}-${pad2(d)}"
    }

    private val RFC2445_DURATION = Regex("^[+-]?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

    /** 解析 RFC2445 时长（P[n]W[n]DT[n]H[n]M[n]S）为毫秒；解析失败或空返回 null。 */
    fun parseDurationMillis(raw: String?): Long? {
        val s = raw?.trim()?.uppercase() ?: return null
        if (s == "P" || s == "PT") return null
        val m = RFC2445_DURATION.matchEntire(s) ?: return null
        val (w, d, h, min, sec) = m.destructured
        if (listOf(w, d, h, min, sec).all { it.isEmpty() }) return null
        fun n(v: String) = v.toLongOrNull() ?: 0L
        val totalSeconds = n(w) * 604_800 + n(d) * 86_400 + n(h) * 3_600 + n(min) * 60 + n(sec)
        return totalSeconds * 1_000
    }

    /**
     * 生成合法的 RFC2445 时长字符串：
     *  - 全天事件用「天」表达（如 `P1D`），对齐 CalendarProvider 对全天重复事件的要求。
     *  - 定时事件用「秒」表达（如 `PT3600S`），注意必须带 `T`（`P3600S` 非法）。
     * 时长不足一天/一秒时兜底为 `P1D` / `PT0S`，避免产出空时长。
     */
    fun rfc2445Duration(startMillis: Long, endMillis: Long, allDay: Boolean): String {
        val spanMillis = (endMillis - startMillis).coerceAtLeast(0L)
        return if (allDay) {
            val days = (spanMillis / MILLIS_PER_DAY).coerceAtLeast(1L)
            "P${days}D"
        } else {
            val seconds = spanMillis / 1_000L
            "PT${seconds}S"
        }
    }

    /**
     * 为重复日程写操作决定 DURATION：时间未变且原 DURATION 合法时原样保留（不破坏原有周/天等表达）；
     * 否则按 [rfc2445Duration] 生成正确的新值。
     */
    fun resolveRecurringDuration(
        existingDuration: String?,
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        timesChanged: Boolean,
    ): String {
        if (!timesChanged && parseDurationMillis(existingDuration) != null) {
            return existingDuration!!.trim()
        }
        return rfc2445Duration(startMillis, endMillis, allDay)
    }

    private fun parseOffsetSeconds(offset: String): Long? {
        if (offset.equals("Z", ignoreCase = true)) return 0L
        val sign = if (offset[0] == '-') -1 else 1
        val digits = offset.substring(1).replace(":", "")
        val oh: Int
        val om: Int
        when (digits.length) {
            2 -> { oh = digits.toInt(); om = 0 }
            4 -> { oh = digits.substring(0, 2).toInt(); om = digits.substring(2).toInt() }
            else -> return null
        }
        if (oh !in 0..18 || om !in 0..59) return null
        return sign * (oh * 3_600L + om * 60L)
    }

    private fun fractionToMillis(frac: String): Long {
        if (frac.isEmpty()) return 0L
        val padded = (frac + "000").substring(0, 3)
        return padded.toLong()
    }

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year < 1 || year > 9999) return false
        if (month !in 1..12) return false
        return day in 1..daysInMonth(year, month)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** 距 1970-01-01 的天数（proleptic Gregorian）。 */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = (y - era * 400).toLong()
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146_097 + doe - 719_468
    }

    /** 天数 → (year, month, day)。 */
    fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    private fun pad2(v: Int): String = if (v < 10) "0$v" else v.toString()

    private fun pad4(v: Int): String = when {
        v < 10 -> "000$v"
        v < 100 -> "00$v"
        v < 1_000 -> "0$v"
        else -> v.toString()
    }
}
