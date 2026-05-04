package com.mezon.mobile.ui.cells

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors

class BadgeDrawable(private val theme: ThemeColors) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LayoutHelper.sp(11f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.WHITE
    }

    private val rect = RectF()
    private var text = ""
    private var count = 0

    fun setCount(value: Int) {
        count = value
        text = if (value > 99) "99+" else value.toString()
        invalidateSelf()
    }

    fun getCount(): Int = count

    override fun draw(canvas: Canvas) {
        if (count <= 0) return
        val b = bounds
        bgPaint.color = theme.badgeRed

        val textWidth = textPaint.measureText(text)
        val w = (textWidth + BADGE_PAD_H * 2).coerceAtLeast(BADGE_HEIGHT)

        val left = b.right - w
        val top = b.top.toFloat()
        rect.set(left, top, b.right.toFloat(), top + BADGE_HEIGHT)

        canvas.drawRoundRect(rect, BADGE_RADIUS, BADGE_RADIUS, bgPaint)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }

    override fun getIntrinsicWidth(): Int {
        if (count <= 0) return 0
        val textWidth = textPaint.measureText(text)
        return (textWidth + BADGE_PAD_H * 2).coerceAtLeast(BADGE_HEIGHT).toInt()
    }

    override fun getIntrinsicHeight(): Int = if (count <= 0) 0 else BADGE_HEIGHT_INT

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        bgPaint.colorFilter = filter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private val BADGE_HEIGHT_INT = LayoutHelper.dp(18)
        private val BADGE_HEIGHT = BADGE_HEIGHT_INT.toFloat()
        private val BADGE_PAD_H = LayoutHelper.dp(6).toFloat()
        private val BADGE_RADIUS = BADGE_HEIGHT / 2f
    }
}
