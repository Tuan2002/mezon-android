package com.mezon.mobile.home.clans

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.mezon.mobile.R
import com.mezon.mobile.core.AndroidUtilities
import com.mezon.mobile.core.BottomSheet
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors

class ChannelMuteBottomSheet(
    context: android.content.Context,
    private val channel: ClanChannelEntity,
    private val onDurationSelected: (muteTimeSeconds: Int, active: Int) -> Unit,
) : BottomSheet(context) {

    private val themeColors = ThemeColors.instance

    init {
        containerHeight = (AndroidUtilities.displaySize.y * 0.58f).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val titleRes = if (channel.isThread) {
            R.string.channel_mute_sheet_title_thread
        } else {
            R.string.channel_mute_sheet_title_channel
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, LayoutHelper.dp(20))
        }

        header.addView(
            TextView(context).apply {
                text = context.getString(titleRes)
                setTextColor(themeColors.textStrong)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
            },
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        )

        val subtitlePrefix = if (channel.isThread) "" else "#"
        header.addView(
            TextView(context).apply {
                text = "$subtitlePrefix${channel.channelLabel.ifBlank { "…" }}"
                setTextColor(themeColors.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = LayoutHelper.dp(4)
            }
        )

        fun buildDurationRow(label: String, onClick: () -> Unit): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(LayoutHelper.dp(14), LayoutHelper.dp(14), LayoutHelper.dp(14), LayoutHelper.dp(14))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(themeColors.onSurface and 0x1AFFFFFF),
                    android.graphics.drawable.ColorDrawable(themeColors.surfaceVariant),
                    android.graphics.drawable.ColorDrawable(0xFFFFFFFF.toInt())
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dismiss()
                    onClick()
                }
                addView(
                    TextView(context).apply {
                        text = label
                        setTextColor(themeColors.textStrong)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        typeface = Typeface.DEFAULT_BOLD
                    },
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f)
                )
                contentDescription = label
            }
        }

        fun addRow(parent: LinearLayout, row: LinearLayout) {
            parent.addView(
                row,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(8)
                }
            )
        }

        val durations = listOf(
            context.getString(R.string.channel_mute_duration_15m) to CHANNEL_MUTE_DURATION_15M,
            context.getString(R.string.channel_mute_duration_1h) to CHANNEL_MUTE_DURATION_1H,
            context.getString(R.string.channel_mute_duration_3h) to CHANNEL_MUTE_DURATION_3H,
            context.getString(R.string.channel_mute_duration_8h) to CHANNEL_MUTE_DURATION_8H,
            context.getString(R.string.channel_mute_duration_24h) to CHANNEL_MUTE_DURATION_24H,
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColors.background)
            setPadding(LayoutHelper.dp(20), LayoutHelper.dp(4), LayoutHelper.dp(20), LayoutHelper.dp(20))
            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }

        for ((label, seconds) in durations) {
            addRow(root, buildDurationRow(label) {
                onDurationSelected(seconds, 0)
            })
        }

        addRow(
            root,
            buildDurationRow(context.getString(R.string.channel_mute_duration_until_manual)) {
                onDurationSelected(0, CHANNEL_MUTE_ACTIVE_INFINITY)
            }
        )

        setCustomView(root)
        super.onCreate(savedInstanceState)
    }
}
