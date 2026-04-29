package com.mezon.mobile.home.chat.channelinfo

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors

class ChannelFileSectionHeaderView(
    context: Context,
    private val theme: ThemeColors
) : FrameLayout(context) {

    private val yearText = TextView(context)
    private val dayText = TextView(context)

    init {
        setPadding(LayoutHelper.dp(10f), LayoutHelper.dp(10f), LayoutHelper.dp(10f), LayoutHelper.dp(10f))
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        yearText.setTextColor(theme.textStrong)
        yearText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        yearText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        dayText.setTextColor(theme.onSurface)
        dayText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        dayText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        column.addView(yearText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        column.addView(dayText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
    }

    fun bind(year: String, dayTitle: String, showYearLine: Boolean) {
        if (showYearLine) {
            yearText.visibility = VISIBLE
            yearText.text = year
            (yearText.layoutParams as LinearLayout.LayoutParams).bottomMargin = LayoutHelper.dp(4f)
        } else {
            yearText.visibility = GONE
            (yearText.layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
        }
        dayText.text = dayTitle
    }
}
