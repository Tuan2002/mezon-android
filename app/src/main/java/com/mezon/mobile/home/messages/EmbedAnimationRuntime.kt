package com.mezon.mobile.home.messages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Choreographer
import android.view.View
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.home.chat.ChatMessageCell
import com.mezon.mobile.home.chat.MezonImageLoader
import com.mezon.mobile.home.chat.ShimmerEffect
import com.mezon.mobile.util.EmbedAnimationSpec
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val ESTIMATED_PLACEHOLDER_HEIGHT = LayoutHelper.dp(133f) + LayoutHelper.dp(16)

private enum class AnimPhase { SPINNING, LANDING, DONE }

private data class SpriteFrame(val name: String, val x: Int, val y: Int, val w: Int, val h: Int)

private data class CellAnimator(
    val repeatCount: Int,
    val finalIndex: Float,
    var phase: AnimPhase = AnimPhase.SPINNING,
    var progress: Float = 0f,
    var currentLoop: Int = 0,
    var phaseStartNs: Long = 0L,
    var phaseDurationNs: Long = 500_000_000L,
)

internal class EmbedAnimationRuntime(
    private val parent: View,
    private val spec: EmbedAnimationSpec,
    private val httpClient: OkHttpClient = EmbedAnimationHttp.client(),
) {
    private data class AtlasFrame(val x: Int, val y: Int, val w: Int, val h: Int)
    private data class CellLayout(val dstW: Float, val dstH: Float, val frameKey: String)

    private var jsonCall: Call? = null
    private var bitmapLoad: MezonImageLoader.Cancellable? = null
    @Volatile private var loadFailed: Boolean = false

    private val framesByKey = mutableMapOf<String, AtlasFrame>()
    private var orderedFrames: List<SpriteFrame> = emptyList()
    private var animationBase = 0f
    private var inputRangeX = floatArrayOf(0f)
    private var outputRangeX = floatArrayOf(0f)

    private var atlasMetaW = 1
    private var atlasMetaH = 1
    @Volatile private var atlas: Bitmap? = null

    private var memoContentW = -1
    private var memoLayouts: List<CellLayout>? = null

    private var cellAnimators: List<CellAnimator> = emptyList()
    private var sharedPhase = AnimPhase.SPINNING
    private var sharedProgress = 0f
    private var sharedLoop = 0
    private var sharedPhaseStartNs = 0L
    private var sharedPhaseDurationNs = 500_000_000L
    private var sharedFinalIndex = 0f
    private var tickerRunning = false

    var onAnimationFinished: (() -> Unit)? = null

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val drawSrcRect = Rect()
    private val drawDstRectF = RectF()
    private val clipRect = RectF()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!tickerRunning) return
            if (!isParentVisible()) {
                stopTicker()
                return
            }
            advanceAnimations(frameTimeNanos)
            invalidateIfAlive()
            if (!shouldAnimate()) {
                stopTicker()
                notifyAnimationFinished()
                return
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun isAnimating(): Boolean = shouldAnimate()

    fun dispose() {
        onAnimationFinished = null
        stopTicker()
        jsonCall?.cancel()
        jsonCall = null
        bitmapLoad?.cancel()
        bitmapLoad = null
        atlas = null
        memoLayouts = null
        memoContentW = -1
        orderedFrames = emptyList()
        cellAnimators = emptyList()
        loadFailed = false
        synchronized(framesByKey) {
            framesByKey.clear()
        }
    }

    fun placeholderHeightPx(): Int = BOX_BIG_PX + CELL_GAP_Y

    private fun invalidateLayouts() {
        memoContentW = -1
        memoLayouts = null
    }

    private fun cellRepeatCount(index: Int): Int =
        if (spec.repeat != null) spec.repeat + index else 0

    private fun hasSharedCells(): Boolean =
        spec.pool.indices.any { cellRepeatCount(it) == 0 }

    private fun prototypeFrame(): AtlasFrame? {
        synchronized(framesByKey) {
            if (framesByKey.isEmpty()) return null
            for (lane in spec.pool) {
                for (k in frameKeysToProbe(lane)) {
                    framesByKey[k]?.let { return it }
                }
            }
            return framesByKey.values.firstOrNull()
        }
    }

    private fun frameKeysToProbe(lane: List<String>): List<String> {
        if (lane.isEmpty()) return emptyList()
        return when {
            spec.isStaticResult -> listOfNotNull(lane.firstOrNull()?.trim(), lane.lastOrNull()?.trim())
            else -> listOfNotNull(lane.firstOrNull()?.trim())
        }.filter { it.isNotEmpty() && it != "null" }.distinct()
    }

    private fun frameKeyForDraw(lane: List<String>): String {
        val first = lane.firstOrNull()?.trim().orEmpty()
        val last = lane.lastOrNull()?.trim().orEmpty()
        return when {
            spec.isStaticResult -> last.ifEmpty { first }
            else -> first.ifEmpty { last }
        }
    }

    private fun computeLayouts(contentWidthPx: Int): List<CellLayout>? {
        if (contentWidthPx <= 0 || spec.pool.isEmpty()) return null
        val proto = prototypeFrame() ?: return null

        synchronized(framesByKey) {
            if (framesByKey.isEmpty()) return null
            val widthItem = proto.w.coerceAtLeast(1)
            val heightItem = proto.h.coerceAtLeast(1)

            val n = spec.pool.size
            val gap = CELL_GAP_X
            val wideEnough = contentWidthPx > BOX_BIG_PX * n + gap * max(0, n - 1)

            val boxPx = if (wideEnough) BOX_BIG_PX else BOX_SMALL_PX
            val denom = min(widthItem, heightItem).coerceAtLeast(1)
            val ratio = boxPx.toFloat() / denom

            val dstWbig = heightItem * ratio
            val dstHbig = widthItem * ratio

            return spec.pool.map { lane ->
                CellLayout(dstWbig, dstHbig, frameKeyForDraw(lane))
            }
        }
    }

    private fun layoutsFor(contentWidthPx: Int): List<CellLayout>? {
        if (memoContentW == contentWidthPx && memoLayouts != null) return memoLayouts
        val computed = computeLayouts(contentWidthPx) ?: return null
        memoLayouts = computed
        memoContentW = contentWidthPx
        return memoLayouts
    }

    fun blockHeightPx(contentWidthPx: Int): Int {
        val lays = layoutsFor(contentWidthPx) ?: return placeholderHeightPx()
        val rowH = ceil(lays.maxOfOrNull { it.dstH }?.toDouble() ?: BOX_BIG_PX.toDouble()).toInt()
            .coerceAtLeast(MIN_BLOCK_ROW_H)
        return rowH + CELL_GAP_Y * 2
    }

    fun startLoading(context: android.content.Context) {
        if (loadFailed) return
        synchronized(framesByKey) {
            if (framesByKey.isNotEmpty()) return
        }
        if (jsonCall != null) return
        val urlJson = spec.urlPosition.trim().ifEmpty {
            loadFailed = true
            return
        }
        val req = Request.Builder().url(urlJson).build()
        val call = httpClient.newCall(req)
        jsonCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call != jsonCall) return
                jsonCall = null
                loadFailed = true
                parent.post { invalidateIfAlive() }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (call != jsonCall) return
                    jsonCall = null
                    val ok = ingestAtlasBody(response.body?.string())
                    if (!ok) loadFailed = true
                    parent.post {
                        invalidateIfAlive()
                        if (ok) {
                            invalidateLayouts()
                            startBitmapLoad(context.applicationContext)
                        }
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun ingestAtlasBody(bodyStr: String?): Boolean {
        if (bodyStr.isNullOrBlank()) return false
        return try {
            val obj = JSONObject(bodyStr)
            val meta = obj.optJSONObject("meta") ?: return false
            val size = meta.optJSONObject("size") ?: return false
            val mw = size.optDouble("w", size.optDouble("width", 0.0)).toInt().coerceAtLeast(1)
            val mh = size.optDouble("h", size.optDouble("height", 0.0)).toInt().coerceAtLeast(1)
            val frames = mutableListOf<SpriteFrame>()
            synchronized(framesByKey) {
                atlasMetaW = mw
                atlasMetaH = mh
                framesByKey.clear()
                val fo = obj.optJSONObject("frames") ?: return false
                val it = fo.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    val node = fo.optJSONObject(key) ?: continue
                    val fr = node.optJSONObject("frame") ?: continue
                    val x = fr.optDouble("x", 0.0).toInt().coerceAtLeast(0)
                    val y = fr.optDouble("y", 0.0).toInt().coerceAtLeast(0)
                    val w = fr.optDouble("w", fr.optDouble("width", 0.0)).toInt().coerceAtLeast(1)
                    val h = fr.optDouble("h", fr.optDouble("height", 0.0)).toInt().coerceAtLeast(1)
                    framesByKey[key] = AtlasFrame(x, y, w, h)
                    frames.add(SpriteFrame(key, x, y, w, h))
                }
                if (framesByKey.isEmpty()) return false
                orderedFrames = if (spec.repeat != null) {
                    frames.sortedWith(compareBy({ it.x }, { it.y }))
                } else {
                    frames.toList()
                }
                val ranges = computeInputOutputRanges(orderedFrames)
                animationBase = ranges.first
                inputRangeX = ranges.second
                outputRangeX = ranges.third
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun computeInputOutputRanges(frames: List<SpriteFrame>): Triple<Float, FloatArray, FloatArray> {
        if (frames.isEmpty()) return Triple(0f, floatArrayOf(0f), floatArrayOf(0f))
        var base = 0f
        val input = mutableListOf(base)
        val output = mutableListOf(-frames[0].x.toFloat())
        for (i in 1 until frames.size) {
            if (frames[i].x != frames[i - 1].x) {
                base += 0.5f
                input.add(base)
                output.add(-frames[i - 1].x.toFloat())
                base += 0.5f
                input.add(base)
                output.add(-frames[i].x.toFloat())
            } else {
                base += 1f
                input.add(base)
                output.add(-frames[i].x.toFloat())
            }
        }
        return Triple(base, input.toFloatArray(), output.toFloatArray())
    }

    private fun startBitmapLoad(appContext: android.content.Context) {
        val mw: Int
        val mh: Int
        synchronized(framesByKey) {
            mw = atlasMetaW
            mh = atlasMetaH
        }
        val reqW = min(mw, 4096).coerceAtLeast(1)
        val reqH = min(mh, 4096).coerceAtLeast(1)
        val urlBmp = spec.urlImage.trim().ifEmpty {
            loadFailed = true
            return
        }
        bitmapLoad?.cancel()
        bitmapLoad = MezonImageLoader.getInstance(appContext).load(
            url = urlBmp,
            reqWidth = reqW,
            reqHeight = reqH,
            onSuccess = { bmp ->
                synchronized(framesByKey) {
                    if (!bmp.isRecycled) atlas = bmp
                }
                initAnimators()
                invalidateLayouts()
                parent.post {
                    invalidateIfAlive()
                    parent.requestLayout()
                }
            },
            onError = {
                loadFailed = true
                parent.post { invalidateIfAlive() }
            },
        )
    }

    private fun initAnimators() {
        if (orderedFrames.isEmpty()) return
        cellAnimators = spec.pool.mapIndexed { index, lane ->
            val repeatCount = cellRepeatCount(index)
            val finalKey = lane.lastOrNull()?.trim().orEmpty()
                .ifEmpty { lane.firstOrNull()?.trim().orEmpty() }
            val idx = orderedFrames.indexOfFirst { it.name == finalKey }
            val finalIndex = if (idx >= 0) idx.toFloat() else (orderedFrames.size - 1).toFloat()
            CellAnimator(repeatCount = repeatCount, finalIndex = finalIndex)
        }
        if (spec.isStaticResult) {
            for (anim in cellAnimators) {
                anim.phase = AnimPhase.DONE
                anim.progress = anim.finalIndex
            }
            sharedProgress = sharedFinalIndex
            sharedPhase = AnimPhase.DONE
            return
        }
        val now = System.nanoTime()
        for (anim in cellAnimators) {
            if (anim.repeatCount > 0) {
                startSpinPhase(anim, now)
            }
        }
        if (hasSharedCells()) {
            sharedFinalIndex = cellAnimators.firstOrNull { it.repeatCount == 0 }?.finalIndex
                ?: (orderedFrames.size - 1).toFloat()
            startSharedSpin(now)
        }
        ensureTickerRunning()
    }

    private fun spinDurationNs(repeatCount: Int): Long =
        if (repeatCount > 0) 500_000_000L
        else max(500_000_000L, orderedFrames.size * 30L * 1_000_000L)

    private fun startSpinPhase(anim: CellAnimator, nowNs: Long = System.nanoTime()) {
        anim.phase = AnimPhase.SPINNING
        anim.progress = 0f
        anim.phaseStartNs = nowNs
        anim.phaseDurationNs = spinDurationNs(anim.repeatCount)
    }

    private fun startLandingPhase(anim: CellAnimator, nowNs: Long = System.nanoTime()) {
        anim.phase = AnimPhase.LANDING
        anim.progress = 0f
        anim.phaseStartNs = nowNs
        val ratio = if (animationBase > 0f) anim.finalIndex / animationBase else 1f
        anim.phaseDurationNs = (500f * ratio).toLong().coerceAtLeast(1L) * 1_000_000L
    }

    private fun startSharedSpin(nowNs: Long = System.nanoTime()) {
        sharedPhase = AnimPhase.SPINNING
        sharedProgress = 0f
        sharedLoop = 0
        sharedPhaseStartNs = nowNs
        sharedPhaseDurationNs = spinDurationNs(0)
    }

    private fun startSharedLanding(nowNs: Long = System.nanoTime()) {
        sharedPhase = AnimPhase.LANDING
        sharedProgress = 0f
        sharedPhaseStartNs = nowNs
        val ratio = if (animationBase > 0f) sharedFinalIndex / animationBase else 1f
        sharedPhaseDurationNs = (500f * ratio).toLong().coerceAtLeast(1L) * 1_000_000L
    }

    private fun phaseProgress(nowNs: Long, startNs: Long, durationNs: Long): Float {
        if (durationNs <= 0L) return 1f
        return ((nowNs - startNs).toFloat() / durationNs.toFloat()).coerceIn(0f, 1f)
    }

    private fun advanceAnimations(@Suppress("UNUSED_PARAMETER") frameTimeNanos: Long) {
        val now = System.nanoTime()
        for (anim in cellAnimators) {
            if (anim.repeatCount <= 0) continue
            when (anim.phase) {
                AnimPhase.SPINNING -> {
                    val t = phaseProgress(now, anim.phaseStartNs, anim.phaseDurationNs)
                    anim.progress = t * animationBase
                    if (t >= 1f) {
                        anim.currentLoop++
                        if (anim.currentLoop < anim.repeatCount) {
                            startSpinPhase(anim, now)
                        } else {
                            startLandingPhase(anim, now)
                        }
                    }
                }
                AnimPhase.LANDING -> {
                    val t = phaseProgress(now, anim.phaseStartNs, anim.phaseDurationNs)
                    anim.progress = t * anim.finalIndex
                    if (t >= 1f) {
                        anim.phase = AnimPhase.DONE
                        anim.progress = anim.finalIndex
                    }
                }
                AnimPhase.DONE -> Unit
            }
        }

        if (!hasSharedCells()) return
        when (sharedPhase) {
            AnimPhase.SPINNING -> {
                val t = phaseProgress(now, sharedPhaseStartNs, sharedPhaseDurationNs)
                sharedProgress = t * animationBase
                if (t >= 1f) {
                    sharedLoop++
                    startSharedSpin(now)
                }
            }
            AnimPhase.LANDING -> {
                val t = phaseProgress(now, sharedPhaseStartNs, sharedPhaseDurationNs)
                sharedProgress = t * sharedFinalIndex
                if (t >= 1f) {
                    sharedPhase = AnimPhase.DONE
                    sharedProgress = sharedFinalIndex
                }
            }
            AnimPhase.DONE -> Unit
        }
    }

    private fun shouldAnimate(): Boolean {
        if (spec.isStaticResult || orderedFrames.isEmpty() || atlas == null) return false
        if (hasSharedCells() && sharedPhase != AnimPhase.DONE) return true
        return cellAnimators.any { it.repeatCount > 0 && it.phase != AnimPhase.DONE }
    }

    private fun isParentVisible(): Boolean =
        parent.isAttachedToWindow && (parent as? ChatMessageCell)?.visibleOnScreen != false

    private fun ensureTickerRunning() {
        if (tickerRunning || !shouldAnimate()) return
        if (!isParentVisible()) return
        tickerRunning = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopTicker() {
        if (!tickerRunning) return
        tickerRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun notifyAnimationFinished() {
        val callback = onAnimationFinished
        onAnimationFinished = null
        callback?.invoke()
    }

    private fun invalidateIfAlive() {
        if (parent.isAttachedToWindow) parent.invalidate()
    }

    private fun interpolate(rangeIn: FloatArray, rangeOut: FloatArray, value: Float): Float {
        if (rangeIn.isEmpty()) return 0f
        if (value <= rangeIn[0]) return rangeOut[0]
        if (value >= rangeIn[rangeIn.size - 1]) return rangeOut[rangeOut.size - 1]
        for (i in 0 until rangeIn.size - 1) {
            val in0 = rangeIn[i]
            val in1 = rangeIn[i + 1]
            if (value in in0..in1) {
                val out0 = rangeOut[i]
                val out1 = rangeOut[i + 1]
                val span = (in1 - in0).takeIf { it != 0f } ?: 1f
                val frac = (value - in0) / span
                return out0 + (out1 - out0) * frac
            }
        }
        return rangeOut[rangeOut.size - 1]
    }

    private fun translateYForProgress(progress: Float): Float {
        if (orderedFrames.isEmpty()) return 0f
        val indices = FloatArray(orderedFrames.size) { it.toFloat() }
        val ys = FloatArray(orderedFrames.size) { -orderedFrames[it].y.toFloat() }
        return interpolate(indices, ys, progress)
    }

    private fun opacityForFrame(frameIndex: Int, progress: Float): Float {
        val lo = frameIndex - 0.8f
        val hi = frameIndex + 0.8f
        return when {
            progress < lo || progress > hi -> 0f
            progress <= frameIndex -> {
                val span = (frameIndex - lo).takeIf { it != 0f } ?: 0.8f
                ((progress - lo) / span) * 2f
            }
            else -> {
                val span = (hi - frameIndex).takeIf { it != 0f } ?: 0.8f
                ((hi - progress) / span) * 2f
            }
        }.coerceIn(0f, 2f)
    }

    private fun drawTranslateCell(
        canvas: Canvas,
        bmp: Bitmap,
        left: Float,
        top: Float,
        dw: Float,
        dh: Float,
        progress: Float,
    ) {
        val proto = orderedFrames.firstOrNull() ?: return
        val scaleX = dw / proto.w.coerceAtLeast(1)
        val scaleY = dh / proto.h.coerceAtLeast(1)
        val tx = interpolate(inputRangeX, outputRangeX, progress) * scaleX
        val ty = translateYForProgress(progress) * scaleY

        val save = canvas.save()
        clipRect.set(left, top, left + dw, top + dh)
        canvas.clipRect(clipRect)
        drawDstRectF.set(
            left + tx,
            top + ty,
            left + tx + atlasMetaW * scaleX,
            top + ty + atlasMetaH * scaleY,
        )
        drawSrcRect.set(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, drawSrcRect, drawDstRectF, bmpPaint)
        canvas.restoreToCount(save)
    }

    private fun drawOpacityCell(
        canvas: Canvas,
        bmp: Bitmap,
        left: Float,
        top: Float,
        dw: Float,
        dh: Float,
        progress: Float,
    ) {
        val save = canvas.save()
        clipRect.set(left, top, left + dw, top + dh)
        canvas.clipRect(clipRect)
        for (i in orderedFrames.indices) {
            val alpha = opacityForFrame(i, progress)
            if (alpha <= 0f) continue
            val frame = orderedFrames[i]
            val scaleX = dw / frame.w.coerceAtLeast(1)
            val scaleY = dh / frame.h.coerceAtLeast(1)
            bmpPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            drawSrcRect.set(
                frame.x.coerceIn(0, max(0, bmp.width - 1)),
                frame.y.coerceIn(0, max(0, bmp.height - 1)),
                (frame.x + frame.w).coerceAtMost(bmp.width),
                (frame.y + frame.h).coerceAtMost(bmp.height),
            )
            drawDstRectF.set(
                left,
                top,
                left + frame.w * scaleX,
                top + frame.h * scaleY,
            )
            canvas.drawBitmap(bmp, drawSrcRect, drawDstRectF, bmpPaint)
        }
        bmpPaint.alpha = 255
        canvas.restoreToCount(save)
    }

    private fun drawStaticCell(
        canvas: Canvas,
        bmp: Bitmap,
        left: Float,
        top: Float,
        dw: Float,
        dh: Float,
        frameKey: String,
    ) {
        val fr = synchronized(framesByKey) { framesByKey[frameKey] } ?: prototypeFrame() ?: return
        val iw = bmp.width
        val ih = bmp.height
        val sx = fr.x.coerceIn(0, max(0, iw - 1))
        val sy = fr.y.coerceIn(0, max(0, ih - 1))
        val sw = fr.w.coerceIn(1, max(1, iw - sx))
        val sh = fr.h.coerceIn(1, max(1, ih - sy))
        drawSrcRect.set(sx, sy, sx + sw, sy + sh)
        drawDstRectF.set(left, top, left + dw, top + dh)
        canvas.drawBitmap(bmp, drawSrcRect, drawDstRectF, bmpPaint)
    }

    fun draw(
        canvas: Canvas,
        originX: Float,
        originY: Float,
        contentWidthPx: Int,
        shimmer: ShimmerEffect,
        themeDarkEmbed: Boolean,
    ) {
        val cells = layoutsFor(contentWidthPx)
        val bmp = atlas?.takeIf { !it.isRecycled }

        if (cells == null || bmp == null) {
            drawPlaceholders(canvas, originX, originY, contentWidthPx, shimmer, themeDarkEmbed)
            return
        }

        if (shouldAnimate()) ensureTickerRunning()

        val rowWDesired = cells.sumOf { ceil(it.dstW.toDouble()).toInt() }.toFloat() +
            CELL_GAP_X.toFloat() * max(0, cells.size - 1)

        val scale = if (contentWidthPx > 0 && rowWDesired > contentWidthPx) {
            contentWidthPx / rowWDesired.coerceAtLeast(1f)
        } else {
            1f
        }

        val rowScaledH = cells.maxOf { ceil((it.dstH * scale).toDouble()).toInt() }
            .coerceAtLeast(MIN_SCALED_ROW_H)

        val gap = CELL_GAP_X * scale
        var xCursor = originX

        cells.forEachIndexed { index, cell ->
            val dw = cell.dstW * scale
            val dh = cell.dstH * scale
            val top = originY + (rowScaledH - dh) / 2f

            if (spec.isStaticResult) {
                drawStaticCell(canvas, bmp, xCursor, top, dw, dh, cell.frameKey)
            } else {
                val anim = cellAnimators.getOrNull(index)
                val repeatCount = cellRepeatCount(index)
                when {
                    anim != null && repeatCount > 0 -> drawTranslateCell(
                        canvas, bmp, xCursor, top, dw, dh, anim.progress,
                    )
                    repeatCount == 0 -> drawOpacityCell(
                        canvas, bmp, xCursor, top, dw, dh, sharedProgress,
                    )
                    anim != null && anim.phase == AnimPhase.DONE -> drawTranslateCell(
                        canvas, bmp, xCursor, top, dw, dh, anim.progress,
                    )
                    else -> drawStaticCell(canvas, bmp, xCursor, top, dw, dh, cell.frameKey)
                }
            }
            xCursor += dw + gap
        }
    }

    private fun drawPlaceholders(
        canvas: Canvas,
        originX: Float,
        originY: Float,
        contentWidthPx: Int,
        shimmer: ShimmerEffect,
        themeDarkEmbed: Boolean,
    ) {
        if (!loadFailed && isParentVisible()) {
            parent.postInvalidateDelayed(32)
        }

        val n = max(1, spec.pool.size)
        val gap = CELL_GAP_X.toFloat()
        val content = contentWidthPx.coerceAtLeast(1)
        val cw = ((content - gap * max(0, n - 1)) / n.toFloat()).coerceAtLeast(MIN_PLACEHOLDER_W.toFloat())

        repeat(n) { i ->
            val x = originX + i * (cw + gap).coerceAtLeast(4f)
            shimmer.draw(
                canvas,
                x,
                originY,
                x + cw,
                originY + PLACEHOLDER_H,
                PLACEHOLDER_RADIUS,
                themeDarkEmbed,
            )
        }
    }

    companion object {
        private val CELL_GAP_X = LayoutHelper.dp(6f)
        private val CELL_GAP_Y = LayoutHelper.dp(8f)
        private val BOX_BIG_PX = LayoutHelper.dp(133f)
        private val BOX_SMALL_PX = LayoutHelper.dp(80f)
        private val MIN_BLOCK_ROW_H = LayoutHelper.dp(40)
        private val MIN_SCALED_ROW_H = LayoutHelper.dp(36)
        private val MIN_PLACEHOLDER_W = LayoutHelper.dp(48f)
        private val PLACEHOLDER_H = LayoutHelper.dp(120f)
        private val PLACEHOLDER_RADIUS = LayoutHelper.dp(8).toFloat()
    }
}

internal fun EmbedAnimationSpec.estimatedPlaceholderHeightPx(): Int =
    ESTIMATED_PLACEHOLDER_HEIGHT
