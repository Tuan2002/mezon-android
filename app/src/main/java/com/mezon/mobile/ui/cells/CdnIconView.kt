package com.mezon.mobile.ui.cells

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.home.chat.MezonImageLoader

class CdnIconView(context: Context, private val theme: ThemeColors) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bitmap: Bitmap? = null
    private var sizeDp = 24
    private var isCircular = false
    private var currentUrl: String? = null
    private var cancellable: MezonImageLoader.Cancellable? = null
    private var attachedToWindow = false

    private var cachedShader: BitmapShader? = null
    private var cachedShaderBitmap: Bitmap? = null
    private var cachedShaderWidth = 0
    private val shaderMatrix = Matrix()
    private val srcRect = android.graphics.Rect()
    private val dstRect = android.graphics.Rect()

    fun setSizeDp(dp: Int) {
        sizeDp = dp
        requestLayout()
    }

    fun setCircular(circular: Boolean) {
        isCircular = circular
        invalidate()
    }

    fun setImageUrl(url: String?) {
        if (url == currentUrl) return
        currentUrl = url
        cancellable?.cancel()
        cancellable = null
        bitmap = null
        cachedShader = null
        cachedShaderBitmap = null
        if (url.isNullOrEmpty()) {
            invalidate()
            return
        }
        if (!attachedToWindow) return
        loadImage(url)
    }

    private fun loadImage(url: String) {
        val px = LayoutHelper.dp(sizeDp)
        cancellable = MezonImageLoader.getInstance(context).load(
            url, px, px,
            onSuccess = { bmp ->
                bitmap = bmp
                cachedShader = null
                cachedShaderBitmap = null
                invalidate()
            },
            onError = {
                bitmap = null
                cachedShader = null
                cachedShaderBitmap = null
                invalidate()
            }
        )
    }

    fun setBitmap(bmp: Bitmap?) {
        bitmap = bmp
        cachedShader = null
        cachedShaderBitmap = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow = true
        val url = currentUrl
        if (url != null && bitmap == null && cancellable == null) {
            loadImage(url)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attachedToWindow = false
        cancellable?.cancel()
        cancellable = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = LayoutHelper.dp(sizeDp)
        setMeasuredDimension(size, size)
    }

    override fun hasOverlappingRendering(): Boolean = false

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            if (isCircular) {
                if (cachedShader == null || cachedShaderBitmap !== bmp || cachedShaderWidth != width) {
                    cachedShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    cachedShaderBitmap = bmp
                    cachedShaderWidth = width
                    val scale = width.toFloat() / bmp.width
                    shaderMatrix.setScale(scale, scale)
                    cachedShader!!.setLocalMatrix(shaderMatrix)
                }
                paint.shader = cachedShader
                canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
                paint.shader = null
            } else {
                srcRect.set(0, 0, bmp.width, bmp.height)
                dstRect.set(0, 0, width, height)
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            }
        } else {
            placeholderPaint.color = theme.surfaceVariant
            if (isCircular) {
                canvas.drawCircle(width / 2f, height / 2f, width / 2f, placeholderPaint)
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), placeholderPaint)
            }
        }
    }

    fun clearShaderCache() {
        cachedShader = null
        cachedShaderBitmap = null
    }
}
