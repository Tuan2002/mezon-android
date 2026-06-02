package com.mezon.mobile.home.chat

import com.mezon.mobile.core.LayoutHelper
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MediaGroupLayout {

    private const val MAX_SIZE_WIDTH = 800
    private const val MAX_SIZE_HEIGHT = 814f
    private const val LAYOUT_TO_PIXEL = 1000f

    private const val SINGLE_ALBUM_MAX = 12

    private const val SPLIT_CHUNK_MAX = 10

    private const val FLAG_LEFT = 1
    private const val FLAG_RIGHT = 2
    private const val FLAG_TOP = 4
    private const val FLAG_BOTTOM = 8

    data class Params(
        val bubbleMaxWidthPx: Int,
        val minDisplaySidePx: Int,
        val isOut: Boolean,
    )

    data class Slot(
        val index: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val flags: Int = FLAG_LEFT or FLAG_RIGHT or FLAG_TOP or FLAG_BOTTOM,
    )

    val CORNER_TOP_LEFT = FLAG_TOP or FLAG_LEFT
    val CORNER_TOP_RIGHT = FLAG_TOP or FLAG_RIGHT
    val CORNER_BOTTOM_RIGHT = FLAG_BOTTOM or FLAG_RIGHT
    val CORNER_BOTTOM_LEFT = FLAG_BOTTOM or FLAG_LEFT

    data class Result(
        val slots: List<Slot>,
        val totalWidthPx: Int,
        val totalHeightPx: Int,
    )

    private class Position {
        var minX: Int = 0
        var maxX: Int = 0
        var minY: Int = 0
        var maxY: Int = 0
        var pw: Int = 0
        var ph: Float = 0f
        var aspectRatio: Float = 1f
        var left: Float = 0f
        var top: Float = 0f
        var spanSize: Int = 0
        var leftSpanOffset: Int = 0
        var flags: Int = 0
        var siblingHeights: FloatArray? = null

        fun set(minX: Int, maxX: Int, minY: Int, maxY: Int, w: Int, h: Float, flags: Int) {
            this.minX = minX
            this.maxX = maxX
            this.minY = minY
            this.maxY = maxY
            this.pw = w
            this.spanSize = w
            this.ph = h
            this.flags = flags
        }
    }

    private class LayoutAttempt(
        val lineCounts: IntArray,
        val heights: FloatArray,
    )

    private data class LayoutConstants(
        val minWidth: Int,
        val minHeight: Int,
        val paddingsWidth: Int,
        val minH: Float,
    )

    private fun normHeight(pixelHeight: Float): Float {
        return pixelHeight.roundToInt().toFloat() / MAX_SIZE_HEIGHT
    }

    private fun roundPx(value: Float): Int = value.roundToInt()

    private fun layoutConstants(minDisplaySidePx: Int): LayoutConstants {
        val scale = minDisplaySidePx / MAX_SIZE_WIDTH.toFloat()
        return LayoutConstants(
            minWidth = (LayoutHelper.dp(120) / scale).roundToInt(),
            minHeight = LayoutHelper.dp(120),
            paddingsWidth = (LayoutHelper.dp(40) / scale).roundToInt(),
            minH = LayoutHelper.dp(100) / MAX_SIZE_HEIGHT,
        )
    }

    fun calculate(aspectRatios: List<Float>, params: Params): Result {
        if (aspectRatios.isEmpty()) {
            return Result(emptyList(), 0, 0)
        }
        if (aspectRatios.size <= SINGLE_ALBUM_MAX) {
            return groupedLayout(aspectRatios, params, baseIndex = 0)
        }
        return splitGroups(aspectRatios, params)
    }

    private fun groupedLayout(aspectRatios: List<Float>, params: Params, baseIndex: Int): Result {
        if (aspectRatios.size == 1) {
            val maxW = params.bubbleMaxWidthPx
            val h = (maxW / aspectRatios[0].coerceAtLeast(0.01f)).toInt()
                .coerceIn(LayoutHelper.dp(100), maxW + LayoutHelper.dp(100))
            return Result(
                listOf(Slot(baseIndex, 0f, 0f, maxW.toFloat(), h.toFloat())),
                maxW,
                h,
            )
        }

        val constants = layoutConstants(params.minDisplaySidePx)
        val positions = buildPositions(aspectRatios, constants, params.isOut)
        computeLeftTop(positions)
        return toPixelLayout(positions, params.bubbleMaxWidthPx, baseIndex)
    }

    private fun splitGroups(aspectRatios: List<Float>, params: Params): Result {
        val n = aspectRatios.size
        val numChunks = ceil(n / SPLIT_CHUNK_MAX.toFloat()).toInt().coerceAtLeast(1)
        val base = n / numChunks
        val remainder = n % numChunks
        val albumGap = LayoutHelper.dp(2).toFloat()

        val slots = ArrayList<Slot>(n)
        var yOffset = 0f
        var totalWidth = 0
        var start = 0
        for (chunk in 0 until numChunks) {
            val size = base + if (chunk < remainder) 1 else 0
            if (size <= 0) continue
            val sub = aspectRatios.subList(start, start + size)
            val result = groupedLayout(sub, params, baseIndex = start)
            for (slot in result.slots) {
                slots.add(slot.copy(y = slot.y + yOffset))
            }
            yOffset += result.totalHeightPx + albumGap
            totalWidth = max(totalWidth, result.totalWidthPx)
            start += size
        }

        val totalHeight = (yOffset - albumGap).roundToInt().coerceAtLeast(1)
        return Result(slots, totalWidth.coerceAtLeast(1), totalHeight)
    }

    private fun buildPositions(
        aspectRatios: List<Float>,
        constants: LayoutConstants,
        isOut: Boolean,
    ): List<Position> {
        val count = aspectRatios.size
        val positions = ArrayList<Position>(count)
        val proportions = StringBuilder()
        var averageAspectRatio = 0f
        var forceCalc = false

        for (ratio in aspectRatios) {
            val safeRatio = ratio.coerceAtLeast(0.01f)
            val position = Position()
            position.aspectRatio = safeRatio
            when {
                safeRatio > 1.2f -> proportions.append('w')
                safeRatio < 0.8f -> proportions.append('n')
                else -> proportions.append('q')
            }
            averageAspectRatio += safeRatio
            if (safeRatio > 2.0f) forceCalc = true
            positions.add(position)
        }
        averageAspectRatio /= count

        val maxAspectRatio = MAX_SIZE_WIDTH / MAX_SIZE_HEIGHT

        if (!forceCalc && count in 2..4) {
            layoutSmallGroup(
                count,
                proportions.toString(),
                averageAspectRatio,
                maxAspectRatio,
                constants,
                isOut,
                positions,
            )
        } else {
            layoutLargeGroup(
                aspectRatios,
                averageAspectRatio,
                constants.minWidth,
                constants.minH,
                isOut,
                positions,
            )
        }
        return positions
    }

    private fun layoutSmallGroup(
        count: Int,
        proportions: String,
        averageAspectRatio: Float,
        maxAspectRatio: Float,
        constants: LayoutConstants,
        isOut: Boolean,
        positions: List<Position>,
    ) {
        when (count) {
            2 -> layoutTwo(proportions, averageAspectRatio, maxAspectRatio, constants.minWidth, constants.minH, positions)
            3 -> layoutThree(proportions, constants, isOut, positions)
            4 -> layoutFour(proportions, constants, isOut, positions)
        }
    }

    private fun layoutTwo(
        proportions: String,
        averageAspectRatio: Float,
        maxAspectRatio: Float,
        minWidth: Int,
        minH: Float,
        positions: List<Position>,
    ) {
        val position1 = positions[0]
        val position2 = positions[1]
        if (proportions == "ww" && averageAspectRatio > 1.4f * maxAspectRatio &&
            position1.aspectRatio - position2.aspectRatio < 0.2f
        ) {
            val height = normHeight(
                min(
                    MAX_SIZE_WIDTH / position1.aspectRatio,
                    min(MAX_SIZE_WIDTH / position2.aspectRatio, MAX_SIZE_HEIGHT / 2f),
                ),
            )
            position1.set(0, 0, 0, 0, MAX_SIZE_WIDTH, height, FLAG_LEFT or FLAG_RIGHT or FLAG_TOP)
            position2.set(0, 0, 1, 1, MAX_SIZE_WIDTH, height, FLAG_LEFT or FLAG_RIGHT or FLAG_BOTTOM)
        } else if (proportions == "ww" || proportions == "qq") {
            val width = MAX_SIZE_WIDTH / 2
            val height = normHeight(
                min(
                    width / position1.aspectRatio,
                    min(width / position2.aspectRatio, MAX_SIZE_HEIGHT),
                ),
            )
            position1.set(0, 0, 0, 0, width, height, FLAG_LEFT or FLAG_BOTTOM or FLAG_TOP)
            position2.set(1, 1, 0, 0, width, height, FLAG_RIGHT or FLAG_BOTTOM or FLAG_TOP)
        } else {
            var secondWidth = max(
                (0.4f * MAX_SIZE_WIDTH).toInt(),
                roundPx(
                    MAX_SIZE_WIDTH / position1.aspectRatio /
                        (1f / position1.aspectRatio + 1f / position2.aspectRatio),
                ),
            )
            var firstWidth = MAX_SIZE_WIDTH - secondWidth
            if (firstWidth < minWidth) {
                val diff = minWidth - firstWidth
                firstWidth = minWidth
                secondWidth -= diff
            }
            val height = normHeight(
                min(
                    MAX_SIZE_HEIGHT,
                    min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio),
                ),
            )
            position1.set(0, 0, 0, 0, firstWidth, height, FLAG_LEFT or FLAG_BOTTOM or FLAG_TOP)
            position2.set(1, 1, 0, 0, secondWidth, height, FLAG_RIGHT or FLAG_BOTTOM or FLAG_TOP)
        }
    }

    private fun layoutThree(
        proportions: String,
        constants: LayoutConstants,
        isOut: Boolean,
        positions: List<Position>,
    ) {
        val position1 = positions[0]
        val position2 = positions[1]
        val position3 = positions[2]
        if (proportions.firstOrNull() == 'n') {
            val thirdHeight = min(
                MAX_SIZE_HEIGHT * 0.5f,
                roundPx(position2.aspectRatio * MAX_SIZE_WIDTH / (position3.aspectRatio + position2.aspectRatio)).toFloat(),
            )
            val secondHeight = MAX_SIZE_HEIGHT - thirdHeight
            val rightWidth = max(
                constants.minWidth,
                min(
                    (MAX_SIZE_WIDTH * 0.5f).roundToInt(),
                    roundPx(min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio)),
                ),
            )
            val leftWidth = roundPx(
                min(MAX_SIZE_HEIGHT * position1.aspectRatio + constants.paddingsWidth, (MAX_SIZE_WIDTH - rightWidth).toFloat()),
            )
            position1.set(0, 0, 0, 1, leftWidth, 1.0f, FLAG_LEFT or FLAG_BOTTOM or FLAG_TOP)
            position2.set(1, 1, 0, 0, rightWidth, secondHeight / MAX_SIZE_HEIGHT, FLAG_RIGHT or FLAG_TOP)
            position3.set(1, 1, 1, 1, rightWidth, thirdHeight / MAX_SIZE_HEIGHT, FLAG_RIGHT or FLAG_BOTTOM)
            position3.spanSize = MAX_SIZE_WIDTH
            position1.siblingHeights = floatArrayOf(thirdHeight / MAX_SIZE_HEIGHT, secondHeight / MAX_SIZE_HEIGHT)
            if (isOut) {
                position1.spanSize = MAX_SIZE_WIDTH - rightWidth
            } else {
                position2.spanSize = MAX_SIZE_WIDTH - leftWidth
                position3.leftSpanOffset = leftWidth
            }
        } else {
            val firstHeight = normHeight(min(MAX_SIZE_WIDTH / position1.aspectRatio, MAX_SIZE_HEIGHT * 0.66f))
            position1.set(0, 1, 0, 0, MAX_SIZE_WIDTH, firstHeight, FLAG_LEFT or FLAG_RIGHT or FLAG_TOP)
            val width = MAX_SIZE_WIDTH / 2
            var secondHeight = min(
                MAX_SIZE_HEIGHT - firstHeight,
                roundPx(min(width / position2.aspectRatio, width / position3.aspectRatio)).toFloat(),
            ) / MAX_SIZE_HEIGHT
            if (secondHeight < constants.minH) secondHeight = constants.minH
            position2.set(0, 0, 1, 1, width, secondHeight, FLAG_LEFT or FLAG_BOTTOM)
            position3.set(1, 1, 1, 1, width, secondHeight, FLAG_RIGHT or FLAG_BOTTOM)
        }
    }

    private fun layoutFour(
        proportions: String,
        constants: LayoutConstants,
        isOut: Boolean,
        positions: List<Position>,
    ) {
        val position1 = positions[0]
        val position2 = positions[1]
        val position3 = positions[2]
        val position4 = positions[3]
        if (proportions.firstOrNull() == 'w') {
            val h0 = normHeight(min(MAX_SIZE_WIDTH / position1.aspectRatio, MAX_SIZE_HEIGHT * 0.66f))
            position1.set(0, 2, 0, 0, MAX_SIZE_WIDTH, h0, FLAG_LEFT or FLAG_RIGHT or FLAG_TOP)
            var h = roundPx(MAX_SIZE_WIDTH / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio))
            var w0 = max(constants.minWidth, min((MAX_SIZE_WIDTH * 0.4f).roundToInt(), roundPx(h * position2.aspectRatio)))
            var w2 = max(max(constants.minWidth, (MAX_SIZE_WIDTH * 0.33f).roundToInt()), roundPx(h * position4.aspectRatio))
            var w1 = MAX_SIZE_WIDTH - w0 - w2
            val minMiddle = LayoutHelper.dp(58)
            if (w1 < minMiddle) {
                val diff = minMiddle - w1
                w1 = minMiddle
                w0 -= diff / 2
                w2 -= diff - diff / 2
            }
            h = min(MAX_SIZE_HEIGHT - h0 * MAX_SIZE_HEIGHT, h.toFloat()).roundToInt()
            var hNorm = h / MAX_SIZE_HEIGHT
            if (hNorm < constants.minH) hNorm = constants.minH
            position2.set(0, 0, 1, 1, w0, hNorm, FLAG_LEFT or FLAG_BOTTOM)
            position3.set(1, 1, 1, 1, w1, hNorm, FLAG_BOTTOM)
            position4.set(2, 2, 1, 1, w2, hNorm, FLAG_RIGHT or FLAG_BOTTOM)
        } else {
            val w = max(
                constants.minWidth,
                roundPx(MAX_SIZE_HEIGHT / (1f / position2.aspectRatio + 1f / position3.aspectRatio + 1f / position4.aspectRatio)),
            )
            val h0 = min(0.33f, max(constants.minHeight.toFloat(), w / position2.aspectRatio) / MAX_SIZE_HEIGHT)
            val h1 = min(0.33f, max(constants.minHeight.toFloat(), w / position3.aspectRatio) / MAX_SIZE_HEIGHT)
            val h2 = 1.0f - h0 - h1
            val w0 = roundPx(min(MAX_SIZE_HEIGHT * position1.aspectRatio + constants.paddingsWidth, (MAX_SIZE_WIDTH - w).toFloat()))
            position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, FLAG_LEFT or FLAG_TOP or FLAG_BOTTOM)
            position2.set(1, 1, 0, 0, w, h0, FLAG_RIGHT or FLAG_TOP)
            position3.set(1, 1, 1, 1, w, h1, FLAG_RIGHT)
            position3.spanSize = MAX_SIZE_WIDTH
            position4.set(1, 1, 2, 2, w, h2, FLAG_RIGHT or FLAG_BOTTOM)
            position4.spanSize = MAX_SIZE_WIDTH
            position1.siblingHeights = floatArrayOf(h0, h1, h2)
            if (isOut) {
                position1.spanSize = MAX_SIZE_WIDTH - w
            } else {
                position2.spanSize = MAX_SIZE_WIDTH - w0
                position3.leftSpanOffset = w0
                position4.leftSpanOffset = w0
            }
        }
    }

    private fun layoutLargeGroup(
        aspectRatios: List<Float>,
        averageAspectRatio: Float,
        minWidth: Int,
        minH: Float,
        isOut: Boolean,
        positions: List<Position>,
    ) {
        val croppedRatios = FloatArray(positions.size) { index ->
            val raw = positions[index].aspectRatio
            val cropped = if (averageAspectRatio > 1.1f) max(1.0f, raw) else min(1.0f, raw)
            cropped.coerceIn(0.66667f, 1.7f)
        }

        val attempts = ArrayList<LayoutAttempt>()
        for (firstLine in 1 until croppedRatios.size) {
            val secondLine = croppedRatios.size - firstLine
            if (firstLine > 3 || secondLine > 3) continue
            attempts.add(
                LayoutAttempt(
                    intArrayOf(firstLine, secondLine),
                    floatArrayOf(
                        multiHeight(croppedRatios, 0, firstLine),
                        multiHeight(croppedRatios, firstLine, croppedRatios.size),
                    ),
                ),
            )
        }
        for (firstLine in 1 until croppedRatios.size - 1) {
            for (secondLine in 1 until croppedRatios.size - firstLine) {
                val thirdLine = croppedRatios.size - firstLine - secondLine
                if (firstLine > 3 || secondLine > (if (averageAspectRatio < 0.85f) 4 else 3) || thirdLine > 3) continue
                attempts.add(
                    LayoutAttempt(
                        intArrayOf(firstLine, secondLine, thirdLine),
                        floatArrayOf(
                            multiHeight(croppedRatios, 0, firstLine),
                            multiHeight(croppedRatios, firstLine, firstLine + secondLine),
                            multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.size),
                        ),
                    ),
                )
            }
        }
        for (firstLine in 1 until croppedRatios.size - 2) {
            for (secondLine in 1 until croppedRatios.size - firstLine) {
                for (thirdLine in 1 until croppedRatios.size - firstLine - secondLine) {
                    val fourthLine = croppedRatios.size - firstLine - secondLine - thirdLine
                    if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) continue
                    attempts.add(
                        LayoutAttempt(
                            intArrayOf(firstLine, secondLine, thirdLine, fourthLine),
                            floatArrayOf(
                                multiHeight(croppedRatios, 0, firstLine),
                                multiHeight(croppedRatios, firstLine, firstLine + secondLine),
                                multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine),
                                multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.size),
                            ),
                        ),
                    )
                }
            }
        }

        var optimal: LayoutAttempt? = null
        var optimalDiff = 0f
        val targetHeight = MAX_SIZE_WIDTH / 3f * 4f
        for (attempt in attempts) {
            var height = 0f
            var minLineHeight = Float.MAX_VALUE
            for (lineHeight in attempt.heights) {
                height += lineHeight
                if (lineHeight < minLineHeight) minLineHeight = lineHeight
            }
            var diff = kotlin.math.abs(height - targetHeight)
            if (attempt.lineCounts.size > 1) {
                if (attempt.lineCounts[0] > attempt.lineCounts[1] ||
                    (attempt.lineCounts.size > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) ||
                    (attempt.lineCounts.size > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])
                ) {
                    diff *= 1.2f
                }
            }
            if (minLineHeight < minWidth) diff *= 1.5f
            if (optimal == null || diff < optimalDiff) {
                optimal = attempt
                optimalDiff = diff
            }
        }
        val chosen = optimal ?: return

        var index = 0
        for (line in chosen.lineCounts.indices) {
            val lineCount = chosen.lineCounts[line]
            val lineHeight = chosen.heights[line]
            var spanLeft = MAX_SIZE_WIDTH
            var posToFix: Position? = null
            for (k in 0 until lineCount) {
                val ratio = croppedRatios[index]
                val width = (ratio * lineHeight).toInt()
                spanLeft -= width
                val pos = positions[index]
                var flags = 0
                if (line == 0) flags = flags or FLAG_TOP
                if (line == chosen.lineCounts.lastIndex) flags = flags or FLAG_BOTTOM
                if (k == 0) {
                    flags = flags or FLAG_LEFT
                    if (isOut) posToFix = pos
                }
                if (k == lineCount - 1) {
                    flags = flags or FLAG_RIGHT
                    if (!isOut) posToFix = pos
                }
                pos.set(k, k, line, line, width, max(minH, lineHeight / MAX_SIZE_HEIGHT), flags)
                index++
            }
            posToFix?.let {
                it.pw += spanLeft
                it.spanSize += spanLeft
            }
        }
    }

    private fun multiHeight(array: FloatArray, start: Int, end: Int): Float {
        var sum = 0f
        for (i in start until end) sum += array[i]
        return MAX_SIZE_WIDTH / sum
    }

    private fun computeLeftTop(positions: List<Position>) {
        for (pos in positions) {
            pos.left = getLeft(pos, positions, pos.minY, pos.maxY, pos.minX)
        }
        for (pos in positions) {
            pos.top = getTop(pos, positions, pos.minY)
        }
    }

    private fun getLeft(except: Position, positions: List<Position>, minY: Int, maxY: Int, minX: Int): Float {
        val sums = FloatArray(maxY - minY + 1)
        for (pos in positions) {
            if (pos === except || pos.maxX >= minX) continue
            val end = min(pos.maxY, maxY) - minY
            for (y in max(pos.minY - minY, 0)..end) {
                sums[y] += pos.pw.toFloat()
            }
        }
        return sums.maxOrNull() ?: 0f
    }

    private fun getTop(except: Position, positions: List<Position>, minY: Int): Float {
        val maxX = positions.maxOf { it.maxX }
        val sums = FloatArray(maxX + 1)
        for (pos in positions) {
            if (pos === except || pos.maxY >= minY) continue
            for (x in pos.minX..pos.maxX) {
                sums[x] += pos.ph
            }
        }
        return sums.maxOrNull() ?: 0f
    }

    private fun rowWidthUnits(positions: List<Position>): Int {
        val lineWidths = IntArray(10)
        for (pos in positions) {
            for (y in pos.minY..pos.maxY) {
                lineWidths[y] += pos.pw
            }
        }
        return lineWidths.maxOrNull() ?: MAX_SIZE_WIDTH
    }

    private fun columnHeightFraction(positions: List<Position>): Float {
        val lineHeights = FloatArray(10)
        for (pos in positions) {
            for (x in pos.minX..pos.maxX) {
                lineHeights[x] += pos.ph
            }
        }
        return lineHeights.maxOrNull() ?: 1f
    }

    private fun toPixelLayout(positions: List<Position>, albumWidthPx: Int, baseIndex: Int): Result {
        val layoutWidth = rowWidthUnits(positions).coerceAtLeast(1)
        val layoutHeight = columnHeightFraction(positions).coerceAtLeast(0.01f)
        val scale = albumWidthPx / layoutWidth.toFloat()
        val gap = LayoutHelper.dp(2).toFloat()

        val slots = ArrayList<Slot>(positions.size)
        for (index in positions.indices) {
            val pos = positions[index]
            var l = pos.left * scale
            var t = pos.top * MAX_SIZE_HEIGHT * scale
            var w = pos.pw * scale
            var h = pos.ph * MAX_SIZE_HEIGHT * scale

            if ((pos.flags and FLAG_LEFT) == 0) {
                l += gap
                w -= gap
            }
            if ((pos.flags and FLAG_TOP) == 0) {
                t += gap
                h -= gap
            }
            if ((pos.flags and FLAG_RIGHT) == 0) {
                w -= gap
            }
            if ((pos.flags and FLAG_BOTTOM) == 0) {
                h -= gap
            }

            slots.add(
                Slot(
                    baseIndex + index,
                    l,
                    t,
                    w.coerceAtLeast(1f),
                    h.coerceAtLeast(1f),
                    pos.flags,
                ),
            )
        }

        val totalWidthPx = albumWidthPx.coerceAtLeast(1)
        val totalHeightPx = (layoutHeight * MAX_SIZE_HEIGHT * scale)
            .roundToInt()
            .coerceAtLeast(1)

        return Result(slots, totalWidthPx, totalHeightPx)
    }
}
