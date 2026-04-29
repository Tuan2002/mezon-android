package com.mezon.mobile.home.chat.channelinfo

import java.util.Calendar
import java.util.Locale

data class ChannelDocumentItem(
    val stableId: String,
    val filename: String,
    val filetype: String,
    val url: String,
    val uploader: Long,
    val createTimeSeconds: Int,
    val messageId: Long
)

object ChannelDocumentItemUtil {
    fun parseItemDate(item: ChannelDocumentItem): Calendar {
        val c = Calendar.getInstance()
        val sec = item.createTimeSeconds
        if (sec > 0) {
            c.timeInMillis = sec.toLong() * 1000L
        }
        return c
    }
}

data class YearDayGroup<T>(
    val year: String,
    val dayTs: Long,
    val items: List<T>,
    val isFirstOfYear: Boolean
)

fun <T> groupByYearDay(items: List<T>, getDate: (T) -> Calendar): List<YearDayGroup<T>> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedByDescending { getDate(it).timeInMillis }
    val map = LinkedHashMap<String, LinkedHashMap<Long, ArrayList<T>>>()
    for (it in sorted) {
        val cal = getDate(it)
        val year = "${cal.get(Calendar.YEAR)}"
        val dayKey = startOfDayMillis(cal)
        map.getOrPut(year) { LinkedHashMap() }.getOrPut(dayKey) { ArrayList() }.add(it)
    }
    val result = ArrayList<YearDayGroup<T>>()
    val years = map.keys.sortedByDescending { it.toIntOrNull() ?: 0 }
    for (year in years) {
        val daysMap = map[year] ?: continue
        val dayKeys = daysMap.keys.sortedDescending()
        dayKeys.forEachIndexed { idx, dayTs ->
            val dayItems = daysMap[dayTs] ?: return@forEachIndexed
            result.add(
                YearDayGroup(
                    year = year,
                    dayTs = dayTs,
                    items = dayItems,
                    isFirstOfYear = idx == 0
                )
            )
        }
    }
    return result
}

fun startOfDayMillis(cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun formatDateHeader(dayCal: Calendar, isVietnamese: Boolean): String {
    val day = (dayCal.get(Calendar.DAY_OF_MONTH)).toString().padStart(2, '0')
    val monthNum = (dayCal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val year = dayCal.get(Calendar.YEAR).toString()
    if (isVietnamese) {
        return "$day tháng $monthNum, $year"
    }
    val monthName = dayCal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US) ?: ""
    return "$monthName $day, $year"
}

fun normalizeSearchQuery(s: String): String = s.replace(Regex("\\s+"), "").lowercase(Locale.getDefault())
