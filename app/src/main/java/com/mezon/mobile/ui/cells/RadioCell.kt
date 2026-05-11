package com.mezon.mobile.ui.cells

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import com.mezon.mobile.core.BaseCell
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors

class RadioCell(context: Context, private val theme: ThemeColors) : BaseCell(context) {

    /** When false, selected state is a dot only (Discord-style); default keeps legacy checkmark overlay. */
    var showSelectionCheckmark: Boolean = true

    private var checked = false
    private var progress = 0f
    private val checkPath = Path()
    private val outerRadius = LayoutHelper.dpf(10f)
    private val innerRadius = LayoutHelper.dpf(5f)
    private var animator: ValueAnimator? = null

    init {
        minimumWidth = LayoutHelper.dp(22)
        minimumHeight = LayoutHelper.dp(22)
    }

    private fun baseRadiusPx(): Float = minOf(width, height) / 2f

    fun isChecked(): Boolean = checked

    fun setChecked(value: Boolean, animated: Boolean = true) {
        if (checked == value) return
        checked = value
        animator?.cancel()
        if (animated) {
            animator = ValueAnimator.ofFloat(progress, if (value) 1f else 0f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = if (value) 1f else 0f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = if (!showSelectionCheckmark) LayoutHelper.dp(24) else LayoutHelper.dp(22)
        setMeasuredDimension(side, side)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.RadioButton"
        info.isCheckable = true
        info.isChecked = checked
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        if (!showSelectionCheckmark) {
            // Discord-style: off = hollow ring; on = filled primary + white center dot (not a checkbox).
            val strokeW = LayoutHelper.dpf(2f)
            val prevStyle = theme.radioPaint.style
            val prevStroke = theme.radioPaint.strokeWidth
            val maxR = baseRadiusPx()
            try {
                if (progress <= 0f) {
                    theme.radioPaint.style = Paint.Style.STROKE
                    theme.radioPaint.strokeWidth = strokeW
                    theme.radioPaint.color = theme.outline
                    val ringR = (maxR - strokeW / 2f).coerceAtLeast(strokeW)
                    canvas.drawCircle(cx, cy, ringR, theme.radioPaint)
                } else {
                    theme.radioPaint.style = Paint.Style.FILL
                    theme.radioPaint.strokeWidth = 0f
                    theme.radioPaint.color = theme.primary
                    canvas.drawCircle(cx, cy, maxR, theme.radioPaint)
                    theme.radioFillPaint.color = Color.WHITE
                    val dotR = (maxR * 0.36f) * progress
                    canvas.drawCircle(cx, cy, dotR, theme.radioFillPaint)
                }
            } finally {
                theme.radioPaint.style = prevStyle
                theme.radioPaint.strokeWidth = prevStroke
            }
            return
        }

        theme.radioPaint.color = if (progress > 0f) theme.primary else theme.outline
        canvas.drawCircle(cx, cy, outerRadius, theme.radioPaint)

        if (progress > 0f) {
            theme.radioFillPaint.color = theme.primary
            canvas.drawCircle(cx, cy, innerRadius * progress, theme.radioFillPaint)

            theme.checkPaint.alpha = (255 * progress).toInt()
            checkPath.reset()
            checkPath.moveTo(cx - LayoutHelper.dpf(3f), cy)
            checkPath.lineTo(cx - LayoutHelper.dpf(0.5f), cy + LayoutHelper.dpf(2.5f))
            checkPath.lineTo(cx + LayoutHelper.dpf(3.5f), cy - LayoutHelper.dpf(2.5f))
            canvas.drawPath(checkPath, theme.checkPaint)
            theme.checkPaint.alpha = 255
        }
    }
}
