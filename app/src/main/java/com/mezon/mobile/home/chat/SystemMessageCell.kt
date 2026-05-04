package com.mezon.mobile.home.chat

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mezon.mobile.BuildConfig
import com.mezon.mobile.R
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.util.MentionColors
import com.mezon.mobile.util.formatRelativeTime
import com.mezon.mobile.util.parseContentText
import com.mezon.mobile.util.parseContentToSpannable
import com.mezon.mobile.util.parseThreadInfoFromPlainText

class SystemMessageCell(context: Context, private val theme: ThemeColors) : LinearLayout(context) {

    interface Delegate {
        fun onOpenThread(threadChannelId: Long, threadTitle: String)
        fun onSeeAllThreads()
        fun onMentionClick(userId: String?, roleId: String?)
    }

    var delegate: Delegate? = null

    var mentionInteractiveGate: ((userId: String?, roleId: String?, segmentText: String) -> Boolean)? = null

    var messageEntity: MessageEntity? = null
        private set

    private val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val highlightTextView = SystemThreadHighlightTextView(context, theme)

    private val plainMessageTextView = SystemMessagePlainTextView(context, theme)

    private val timeTextView = TextView(context).apply {
        maxLines = 1
        includeFontPadding = false
        val paint = theme.chatTimePaint
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, paint.textSize)
        setTextColor(paint.color)
        typeface = paint.typeface
    }

    private val textColumn: LinearLayout

    private val highlightBridge = object : SystemThreadHighlightTextView.Listener {
        override fun onThreadTitleClick(threadChannelId: Long, threadTitle: String) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "thread_title_click channelId=$threadChannelId title=$threadTitle hasDelegate=${delegate != null}")
            }
            delegate?.onOpenThread(threadChannelId, threadTitle)
        }

        override fun onAllThreadsClick() {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "all_threads_click hasDelegate=${delegate != null}")
            }
            delegate?.onSeeAllThreads()
        }
    }

    var channelName: String = ""

    init {
        orientation = VERTICAL
        setPadding(PAD_H, PAD_V, PAD_H, PAD_V)

        plainMessageTextView.onMentionClick = { uid, rid ->
            delegate?.onMentionClick(uid, rid)
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        }

        row.addView(iconView, LayoutParams(ICON_SIZE_SMALL, ICON_SIZE_SMALL))

        textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f).apply {
                marginStart = ICON_GAP
            }
        }

        textColumn.addView(highlightTextView, LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        textColumn.addView(plainMessageTextView, LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        plainMessageTextView.visibility = View.GONE
        highlightTextView.visibility = View.GONE

        textColumn.addView(
            timeTextView,
            LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = GAP_V
            }
        )

        row.addView(textColumn)
        addView(row)
    }

    fun update(mask: Int, newMsg: MessageEntity? = null): Boolean {
        val msg = newMsg ?: messageEntity ?: return false
        if (newMsg != null) messageEntity = newMsg
        if (mask != 0) return false

        bindIcon(msg, resolveIcon(msg))
        bindBody(msg)
        return true
    }

    private fun bindIcon(msg: MessageEntity, d: Drawable?) {
        val lpIcon = iconView.layoutParams as LayoutParams
        val side = iconSizeFor(msg.code)
        lpIcon.width = side
        lpIcon.height = side
        iconView.layoutParams = lpIcon

        val lpCol = textColumn.layoutParams as LayoutParams
        if (d == null) {
            iconView.visibility = View.GONE
            iconView.setImageDrawable(null)
            lpCol.marginStart = 0
        } else {
            iconView.visibility = View.VISIBLE
            iconView.setImageDrawable(d)
            lpCol.marginStart = ICON_GAP
        }
        textColumn.layoutParams = lpCol
    }

    private fun bindBody(msg: MessageEntity) {
        val mentionColors = MentionColors(
            theme.textLink,
            theme.midnightBlue,
            theme.textRoleLink,
            theme.darkMossGreen
        )

        val textStr = parseContentText(msg.content)
        val threadInfo = if (msg.code == MessageEntity.CODE_CREATE_THREAD) {
            parseThreadInfoFromPlainText(textStr)
        } else {
            null
        }

        val timeStr = formatRelativeTime(msg.timestampSeconds)

        if (threadInfo != null) {
            highlightTextView.visibility = View.VISIBLE
            plainMessageTextView.visibility = View.GONE
            timeTextView.visibility = View.VISIBLE
            highlightTextView.setThreadCreatedHighlight(
                highlightBridge,
                threadInfo.label,
                threadInfo.channelId,
                mentionColors
            )
            timeTextView.text = timeStr
        } else {
            highlightTextView.visibility = View.GONE
            plainMessageTextView.visibility = View.VISIBLE
            timeTextView.visibility = View.GONE

            val bodyCore: CharSequence = when {
                msg.code == MessageEntity.CODE_CREATE_THREAD && textStr.isBlank() -> systemFallbackText(msg)
                textStr.isBlank() -> systemFallbackText(msg)
                else -> parseContentToSpannable(
                    msg.content,
                    theme.primary,
                    plainMessageTextView,
                    mentionColors,
                    theme,
                    systemPlainHost = plainMessageTextView,
                    systemMentionGate = mentionInteractiveGate
                )
            }
            plainMessageTextView.text = appendInlineTime(bodyCore, timeStr)
        }
    }

    private fun appendInlineTime(body: CharSequence, timeStr: String): CharSequence {
        if (timeStr.isEmpty()) return body
        val sb = SpannableStringBuilder(body)
        val gap = "  "
        sb.append(gap)
        val t0 = sb.length
        sb.append(timeStr)
        val rel = theme.chatTimePaint.textSize / theme.systemMessageTextPaint.textSize
        sb.setSpan(RelativeSizeSpan(rel), t0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(theme.chatTimePaint.color), t0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun resolveIcon(msg: MessageEntity): Drawable? {
        val icon = when (msg.code) {
            MessageEntity.CODE_FIRST_MESSAGE -> MezonIcon.auditLog
            MessageEntity.CODE_WELCOME -> MezonIcon.auditLog
            MessageEntity.CODE_CREATE_THREAD -> MezonIcon.channelText
            MessageEntity.CODE_CREATE_PIN -> MezonIcon.pinIcon
            MessageEntity.CODE_AUDIT_LOG -> MezonIcon.auditLog
            MessageEntity.CODE_UPCOMING_EVENT -> MezonIcon.auditLog
            else -> null
        } ?: return null

        val d = icon.getDrawable(context).mutate()
        val tint = when (msg.code) {
            MessageEntity.CODE_FIRST_MESSAGE -> theme.success
            MessageEntity.CODE_WELCOME -> theme.success
            MessageEntity.CODE_AUDIT_LOG -> theme.blurple
            MessageEntity.CODE_UPCOMING_EVENT -> theme.error
            else -> theme.onSurfaceVariant
        }
        d.setTint(tint)
        return d
    }

    private fun systemFallbackText(msg: MessageEntity): String = when (msg.code) {
        MessageEntity.CODE_FIRST_MESSAGE -> if (channelName.isNotEmpty()) "Welcome to #$channelName" else "Welcome!"
        MessageEntity.CODE_WELCOME -> "Welcome!"
        MessageEntity.CODE_CREATE_THREAD -> context.getString(R.string.system_msg_started_thread_lead) + " …"
        MessageEntity.CODE_CREATE_PIN -> "pinned a message"
        MessageEntity.CODE_AUDIT_LOG -> "audit log"
        MessageEntity.CODE_UPCOMING_EVENT -> "upcoming event"
        else -> ""
    }

    companion object {
        private const val TAG = "SystemMessageCell"
        private val ICON_SIZE_SMALL = LayoutHelper.dp(20)
        private val ICON_GAP = LayoutHelper.dp(8)
        private val PAD_H = LayoutHelper.dp(16)
        private val PAD_V = LayoutHelper.dp(8)
        private val GAP_V = LayoutHelper.dp(2)

        private fun iconSizeFor(code: Int): Int = when (code) {
            MessageEntity.CODE_FIRST_MESSAGE,
            MessageEntity.CODE_WELCOME,
            MessageEntity.CODE_AUDIT_LOG,
            MessageEntity.CODE_UPCOMING_EVENT -> LayoutHelper.dp(24)
            else -> ICON_SIZE_SMALL
        }
    }
}
