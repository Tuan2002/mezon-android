package com.mezon.mobile.ui.cells

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.SharedConfig
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.home.chat.AttachmentPickerItem
import com.mezon.mobile.home.chat.ThumbnailCache

class PhotoAttachPhotoCell(context: Context, private val theme: ThemeColors) : FrameLayout(context),
    ThumbnailCache.Callback {

    private val imageView: ImageView
    private val checkOverlay: CheckOverlayView
    private var item: AttachmentPickerItem? = null
    private var checkNumber = -1
    private var isChecked = false
    private var pendingTask: Runnable? = null
    private var currentAlpha = 1f

    var onCheckClickListener: ((PhotoAttachPhotoCell) -> Unit)? = null

    init {
        setWillNotDraw(false)
        isClickable = true

        imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

        checkOverlay = CheckOverlayView(context, theme)
        addView(checkOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

        setOnClickListener { onCheckClickListener?.invoke(this) }
    }

    fun setPhotoEntry(entry: AttachmentPickerItem) {
        if (item?.id == entry.id) return
        item = entry

        ThumbnailCache.cancel(pendingTask)
        pendingTask = null

        checkOverlay.setVideoDuration(if (entry.isVideo) entry.duration else -1)

        val cached = ThumbnailCache.get(entry.id)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            currentAlpha = 1f
            return
        }

        imageView.setImageDrawable(null)
        currentAlpha = 1f

        pendingTask = ThumbnailCache.load(context.contentResolver, entry, this)
    }

    override fun onThumbnailLoaded(id: Long, bitmap: Bitmap) {
        if (item?.id != id) return
        pendingTask = null

        imageView.setImageBitmap(bitmap)
        if (SharedConfig.animationsEnabled()) {
            currentAlpha = 0f
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (imageView.drawable == null || currentAlpha < 1f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), placeholderPaint)
        }

        if (currentAlpha < 1f) {
            currentAlpha = (currentAlpha + ALPHA_STEP).coerceAtMost(1f)
            imageView.alpha = currentAlpha
            if (currentAlpha < 1f) invalidate()
        } else {
            imageView.alpha = 1f
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThumbnailCache.cancel(pendingTask)
        pendingTask = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val entry = item ?: return
        if (imageView.drawable != null) return
        val cached = ThumbnailCache.get(entry.id)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            currentAlpha = 1f
        }
    }

    fun getItem(): AttachmentPickerItem? = item

    fun setChecked(num: Int, checked: Boolean, animated: Boolean) {
        isChecked = checked
        checkNumber = num
        checkOverlay.setChecked(num, checked)
        val newScale = if (checked) 0.787f else 1f
        if (animated && SharedConfig.animationsEnabled()) {
            imageView.animate().scaleX(newScale).scaleY(newScale).setDuration(200).start()
        } else {
            imageView.scaleX = newScale
            imageView.scaleY = newScale
        }
    }

    fun isItemChecked(): Boolean = isChecked

    private class CheckOverlayView(context: Context, private val theme: ThemeColors) : View(context) {

        companion object {
            private val CHECK_SIZE = LayoutHelper.dp(22f).toFloat()
            private val CHECK_MARGIN = LayoutHelper.dp(6f).toFloat()
            private val DURATION_PAD_H = LayoutHelper.dp(6f).toFloat()
            private val DURATION_PAD_V = LayoutHelper.dp(2f).toFloat()
            private val DURATION_MARGIN = LayoutHelper.dp(4f).toFloat()
            private val DURATION_RADIUS = LayoutHelper.dp(4f).toFloat()
        }

        private var checked = false
        private var checkNum = -1
        private var videoDuration = -1

        private val checkBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val checkTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = LayoutHelper.sp(12f)
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val overlayPaint = Paint().apply {
            color = 0x33000000
        }
        private val durationBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x7F000000
        }
        private val durationTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = LayoutHelper.sp(12f)
            color = Color.WHITE
        }
        private val uncheckBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = LayoutHelper.dp(1.5f).toFloat()
            color = Color.WHITE
        }
        private val tmpRect = RectF()

        fun setChecked(num: Int, value: Boolean) {
            checked = value
            checkNum = num
            invalidate()
        }

        fun setVideoDuration(seconds: Int) {
            videoDuration = seconds
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (checked) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            }

            val cx = width - CHECK_MARGIN - CHECK_SIZE / 2
            val cy = CHECK_MARGIN + CHECK_SIZE / 2

            if (checked) {
                checkBgPaint.color = theme.primary
                canvas.drawCircle(cx, cy, CHECK_SIZE / 2, checkBgPaint)
                if (checkNum >= 0) {
                    val text = (checkNum + 1).toString()
                    val ty = cy - (checkTextPaint.descent() + checkTextPaint.ascent()) / 2
                    canvas.drawText(text, cx, ty, checkTextPaint)
                }
            } else {
                canvas.drawCircle(cx, cy, CHECK_SIZE / 2 - uncheckBorderPaint.strokeWidth / 2, uncheckBorderPaint)
            }

            if (videoDuration >= 0) {
                val durationText = formatDuration(videoDuration)
                val textWidth = durationTextPaint.measureText(durationText)
                val bgHeight = durationTextPaint.textSize + DURATION_PAD_V * 2
                val bgWidth = textWidth + DURATION_PAD_H * 2

                tmpRect.set(
                    DURATION_MARGIN,
                    height - DURATION_MARGIN - bgHeight,
                    DURATION_MARGIN + bgWidth,
                    height - DURATION_MARGIN
                )
                canvas.drawRoundRect(tmpRect, DURATION_RADIUS, DURATION_RADIUS, durationBgPaint)

                val textX = tmpRect.left + DURATION_PAD_H
                val textY = tmpRect.bottom - DURATION_PAD_V - durationTextPaint.descent()
                canvas.drawText(durationText, textX, textY, durationTextPaint)
            }
        }

        private fun formatDuration(seconds: Int): String {
            val m = seconds / 60
            val s = seconds % 60
            return "%d:%02d".format(m, s)
        }
    }

    companion object {
        private const val ALPHA_STEP = 0.12f
        private val placeholderPaint = Paint().apply {
            color = 0xFF1A1A1A.toInt()
        }
    }
}
