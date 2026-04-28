package com.mezon.mobile.home.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mezon.mobile.R
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors

internal class PasteImagePasteTooltipContent(
    context: Context,
    themeColors: ThemeColors,
    onPasteClick: () -> Unit
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false

        val bubbleWrap = FrameLayout(context).apply {
            val gd = GradientDrawable()
            gd.cornerRadius = LayoutHelper.dp(8f).toFloat()
            gd.setColor(themeColors.secondaryLight)
            background = gd
            setOnClickListener { onPasteClick() }
        }
        val label = TextView(context).apply {
            val padH = LayoutHelper.dp(12f)
            val padV = LayoutHelper.dp(8f)
            setPadding(padH, padV, padH, padV)
            minHeight = LayoutHelper.dp(36f)
            gravity = Gravity.CENTER
            setText(R.string.message_paste_option)
            setTextColor(themeColors.onSurface)
            textSize = 14f
        }
        bubbleWrap.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        addView(
            bubbleWrap,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val arrow = PasteTooltipArrowView(context, themeColors.secondaryLight)
        addView(
            arrow,
            LinearLayout.LayoutParams(LayoutHelper.dp(12), LayoutHelper.dp(6)).apply {
                topMargin = -1
            }
        )
    }

    private class PasteTooltipArrowView(
        context: Context,
        arrowColor: Int
    ) : View(context) {
        private val w = LayoutHelper.dp(12)
        private val h = LayoutHelper.dp(6)
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = arrowColor
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            val bw = measuredWidth.toFloat()
            val bh = measuredHeight.toFloat()
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(bw, 0f)
            path.lineTo(bw / 2f, bh)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
