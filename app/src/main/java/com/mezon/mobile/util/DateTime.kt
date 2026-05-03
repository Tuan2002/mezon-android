package com.mezon.mobile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val isoLikePatterns = arrayOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ssX",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss",
)

fun formatDayMonthYear(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(millis))

fun parseDateTimeMillis(raw: String): Long? {
    if (raw.isBlank()) return null
    raw.trim().toLongOrNull()?.let { n ->
        val ms = when {
            n > 946684800000L -> n
            n > 946684800L -> n * 1000
            else -> n * 1000
        }
        return ms
    }
    return parseIsoDateMillis(raw)
}

private fun parseIsoDateMillis(raw: String): Long? {
    for (p in isoLikePatterns) {
        runCatching {
            val sdf = SimpleDateFormat(p, Locale.US)
            return sdf.parse(raw)?.time
        }
    }
    return null
}
