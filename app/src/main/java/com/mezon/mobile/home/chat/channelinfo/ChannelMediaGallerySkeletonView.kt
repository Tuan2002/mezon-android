package com.mezon.mobile.home.chat.channelinfo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.ui.theme.ThemeMode
import kotlin.math.min
import kotlin.math.sin

class ChannelMediaGallerySkeletonView(
    context: Context,
    private val theme: ThemeColors,
    private val cellSizePx: () -> Int,
) : View(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pulsing = false
    private val rectF = RectF()
    private val bgPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val pulsePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    private val shimmerTick =
        object : Runnable {
            override fun run() {
                if (!pulsing || !isAttachedToWindow) return
                invalidate()
                mainHandler.postDelayed(this, 32L)
            }
        }

    private val isDark: Boolean
        get() = theme.resolvedMode != ThemeMode.LIGHT

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulsing = true
        mainHandler.postDelayed(shimmerTick, 32L)
    }

    override fun onDetachedFromWindow() {
        pulsing = false
        mainHandler.removeCallbacks(shimmerTick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        if (w <= 0) return

        val baseArgb =
            if (isDark) {
                theme.surfaceVariant
            } else {
                theme.secondaryLight
            }
        bgPaint.color = (baseArgb and 0x00FFFFFF) or (0xE4000000.toInt())

        val padH = LayoutHelper.dp(8f)
        val margin = LayoutHelper.dp(4f)
        val gap = LayoutHelper.dp(8f)
        val radius = LayoutHelper.dp(8f).toFloat()
        val padTop = LayoutHelper.dp(8f)
        val reservedX = padH * 2 + margin * 2 + gap * 2
        val cellFit = ((w - reservedX) / COLS).coerceAtLeast(1)
        val rnStyleCell =
            ((w.toFloat() / COLS) - LayoutHelper.dp(16f)).toInt()
                .coerceAtLeast(LayoutHelper.dp(48f))
        val cell =
            min(
                cellSizePx().coerceAtLeast(LayoutHelper.dp(48f)),
                min(cellFit, rnStyleCell)
            )

        val time = System.currentTimeMillis() % 2000L
        val fraction = time / 2000f
        val alphaWave = (sin(fraction * Math.PI * 2).toFloat() * 0.5f + 0.5f)
        val pulseAlpha = (alphaWave * 40f).toInt().coerceIn(0, 255)

        pulsePaint.color =
            if (isDark) {
                0x00FFFFFF or (pulseAlpha shl 24)
            } else {
                0x00000000 or (pulseAlpha shl 24)
            }

        var y = padTop.toFloat()
        repeat(ROWS) {
            var x = (padH + margin).toFloat()
            repeat(COLS) {
                rectF.set(x, y, x + cell, y + cell)
                canvas.drawRoundRect(rectF, radius, radius, bgPaint)
                canvas.drawRoundRect(rectF, radius, radius, pulsePaint)
                x += cell + gap
            }
            y += cell + gap
        }
    }

    companion object {
        private const val COLS = 3
        private const val ROWS = 4
    }
}
