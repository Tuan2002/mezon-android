package com.mezon.mobile.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parse and format timestamps: Unix epoch (seconds / millis) and ISO-8601 strings.
 *
 * ISO parsing uses [SimpleDateFormat] with a small ordered list of patterns (UTC / offsets / date-only).
 * Formatting with a custom pattern builds a short-lived formatter (thread-safe by isolation).
 */
object DateTimeUtil {

    object Patterns {
        const val ISO_INSTANT_MS_Z = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        const val ISO_INSTANT_SEC_Z = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        const val ISO_LOCAL_MS_TZ = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        const val ISO_LOCAL_SEC_TZ = "yyyy-MM-dd'T'HH:mm:ssXXX"
        const val ISO_DATE = "yyyy-MM-dd"
        const val DAY_MONTH_YEAR_COMMA_TIME = "dd/MM/yyyy, HH:mm"
        const val DAY_MONTH_YEAR_TIME = "dd/MM/yyyy HH:mm"
        const val TIME_HOUR_MINUTE = "HH:mm"
    }

    private val utc: TimeZone get() = TimeZone.getTimeZone("UTC")
    private val isoParseLock = Any()

    private val isoParsersUtcMsZ: SimpleDateFormat by lazy {
        SimpleDateFormat(Patterns.ISO_INSTANT_MS_Z, Locale.US).apply { timeZone = utc }
    }
    private val isoParsersUtcSecZ: SimpleDateFormat by lazy {
        SimpleDateFormat(Patterns.ISO_INSTANT_SEC_Z, Locale.US).apply { timeZone = utc }
    }
    private val isoParsersUtcMsNoZ: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply { timeZone = utc }
    }
    private val isoParsersUtcSecNoZ: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = utc }
    }
    private val isoParsersOffsetMs: SimpleDateFormat by lazy {
        SimpleDateFormat(Patterns.ISO_LOCAL_MS_TZ, Locale.US)
    }
    private val isoParsersOffsetSec: SimpleDateFormat by lazy {
        SimpleDateFormat(Patterns.ISO_LOCAL_SEC_TZ, Locale.US)
    }
    private val isoParsersDateOnly: SimpleDateFormat by lazy {
        SimpleDateFormat(Patterns.ISO_DATE, Locale.US).apply { timeZone = utc }
    }
    private val isoParsersSpaceSep: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = utc }
    }

    const val SECONDS_UPPER_BOUND: Long = 10_000_000_000L

    fun epochToMillis(epoch: Long): Long =
        if (kotlin.math.abs(epoch) < SECONDS_UPPER_BOUND) epoch * 1000 else epoch

    fun millisToEpochSeconds(millis: Long): Long = millis / 1000L

    fun parseIso8601(iso: String): Long? {
        val s = iso.trim()
        if (s.isEmpty()) return null
        synchronized(isoParseLock) {
            val chain = listOf(
                isoParsersOffsetMs,
                isoParsersOffsetSec,
                isoParsersUtcMsZ,
                isoParsersUtcSecZ,
                isoParsersUtcMsNoZ,
                isoParsersUtcSecNoZ,
                isoParsersSpaceSep,
                isoParsersDateOnly,
            )
            for (fmt in chain) {
                try {
                    fmt.parse(s)?.time?.let { return it }
                } catch (_: ParseException) {
                }
            }
        }
        return null
    }

    fun parseFlexibleToMillis(value: String): Long? {
        val s = value.trim()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return epochToMillis(it) }
        return parseIso8601(s)
    }

    fun format(
        millis: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val sdf = SimpleDateFormat(pattern, locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatUtc(
        millis: Long,
        pattern: String,
        locale: Locale = Locale.US,
    ): String = format(millis, pattern, locale, utc)

    fun formatEpochSeconds(
        epochSeconds: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = format(epochToMillis(epochSeconds), pattern, locale, timeZone)

    fun formatEpochSeconds(
        epochSeconds: Int,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = formatEpochSeconds(epochSeconds.toLong(), pattern, locale, timeZone)

    fun formatEpochSecondsUtc(
        epochSeconds: Long,
        pattern: String,
        locale: Locale = Locale.US,
    ): String = formatUtc(epochToMillis(epochSeconds), pattern, locale)

    fun formatEpochSecondsUtc(
        epochSeconds: Int,
        pattern: String,
        locale: Locale = Locale.US,
    ): String = formatEpochSecondsUtc(epochSeconds.toLong(), pattern, locale)

    fun toIso8601UtcMillis(millis: Long): String = formatUtc(millis, Patterns.ISO_INSTANT_MS_Z)

    fun toIso8601UtcSeconds(millis: Long): String = formatUtc(millis, Patterns.ISO_INSTANT_SEC_Z)
}
