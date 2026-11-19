package com.nexus.iptv.feature.epg.parser

import java.util.Calendar
import java.util.TimeZone

object XmltvDateTimeParser {

    fun parseXmltvTimestamp(rawTimestamp: String?, userTimeshiftMinutes: Int = 0): Long? {
        if (rawTimestamp.isNullOrBlank()) return null
        val trimmed = rawTimestamp.trim()
        if (trimmed.length < 12) return null

        return try {
            val year = parseDigits(trimmed, 0, 4)
            val month = parseDigits(trimmed, 4, 6) - 1
            val day = parseDigits(trimmed, 6, 8)
            val hour = parseDigits(trimmed, 8, 10)
            val minute = parseDigits(trimmed, 10, 12)
            val second = if (trimmed.length >= 14 && trimmed[12].isDigit() && trimmed[13].isDigit()) {
                parseDigits(trimmed, 12, 14)
            } else 0

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(year, month, day, hour, minute, second)
                set(Calendar.MILLISECOND, 0)
            }

            var utcMillis = calendar.timeInMillis

            val tzPart = trimmed.substring(minOf(14, trimmed.length)).trim()
            if (tzPart.isNotEmpty() && (tzPart.startsWith("+") || tzPart.startsWith("-"))) {
                val sign = if (tzPart[0] == '+') 1 else -1
                val cleanTz = tzPart.substring(1).replace(":", "").trim()
                if (cleanTz.length >= 4) {
                    val tzHours = parseDigits(cleanTz, 0, 2)
                    val tzMinutes = parseDigits(cleanTz, 2, 4)
                    val tzOffsetMs = sign * ((tzHours * 60L + tzMinutes) * 60_000L)
                    utcMillis -= tzOffsetMs
                }
            }

            utcMillis + (userTimeshiftMinutes * 60_000L)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDigits(source: String, start: Int, end: Int): Int {
        var result = 0
        for (i in start until end) {
            val c = source[i]
            if (c !in '0'..'9') throw NumberFormatException("Invalid digit: $c")
            result = result * 10 + (c - '0')
        }
        return result
    }
}
