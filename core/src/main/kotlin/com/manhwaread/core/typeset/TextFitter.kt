package com.manhwaread.core.typeset

import com.manhwaread.core.model.BubbleOverlay
import com.manhwaread.core.model.OverlayLine
import com.manhwaread.core.model.PointF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// Растер используется только для проверки покрытия; результатом подбора остаются векторные строки.
interface GlyphRasterizer {
    fun measure(text: String, style: FitStyle): Float
    fun metrics(style: FitStyle): FontMetrics
    fun rasterize(text: String, style: FitStyle): RasterizedInk
}

data class FontMetrics(val ascent: Float, val descent: Float) {
    init { require(ascent.isFinite() && descent.isFinite() && ascent < descent) }
}

data class RasterizedInk(val mask: PixelMask, val left: Int, val top: Int)

data class FitStyle(
    val fontId: String,
    val sizePx: Float,
    val letterSpacingEm: Float = 0f,
    val scaleX: Float = 1f,
    val strokeWidthPx: Float = 0f,
)

data class FitRequest(
    val bubbleId: String,
    val text: String,
    val mask: PixelMask,
    val origin: PointF,
    val fontId: String,
    val paddingPx: Int = 6,
    val minSizePx: Float = 7f,
    val maxSizePx: Float = 64f,
    val leftAligned: Boolean = false,
    val color: Int = -16777216,
    val strokeColor: Int? = null,
    val strokeWidthPx: Float = 0f,
) {
    init {
        require(bubbleId.isNotBlank() && fontId.isNotBlank())
        require(paddingPx >= 0)
        require(minSizePx.isFinite() && maxSizePx.isFinite() && minSizePx >= 1f && maxSizePx >= minSizePx && maxSizePx <= 512f)
        require(strokeWidthPx.isFinite() && strokeWidthPx >= 0f && strokeWidthPx <= 32f)
    }
}

class TextFitter(private val rasterizer: GlyphRasterizer) {
    fun fit(request: FitRequest): BubbleOverlay {
        val text = request.text.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return BubbleOverlay(request.bubbleId, emptyList(), overflow = false)
        val mask = request.mask.erode(request.paddingPx)
        val geometry = geometry(mask)
        if (geometry != null) {
            for (fallback in fallbacks) {
                val minimum = ceil(request.minSizePx * 2).toInt()
                val maximum = floor(request.maxSizePx * 2).toInt()
                if (minimum > maximum) continue
                var low = minimum
                var high = maximum
                var best: List<OverlayLine>? = null
                while (low <= high) {
                    val middle = (low + high) / 2
                    val lines = layout(request, text, mask, geometry, middle / 2f, fallback, checkInk = true)
                    if (lines != null) {
                        best = lines
                        low = middle + 1
                    } else high = middle - 1
                }
                // У нерегулярной маски и хинтинга шрифта попадание не обязано быть монотонным.
                // Проверка оставшихся размеров сохраняет гарантию максимального подходящего кегля.
                for (size in maximum downTo low) {
                    val lines = layout(request, text, mask, geometry, size / 2f, fallback, checkInk = true)
                    if (lines != null) return BubbleOverlay(request.bubbleId, lines, overflow = false)
                }
                if (best != null) return BubbleOverlay(request.bubbleId, best, overflow = false)
            }
        }
        val fallbackGeometry = geometry(request.mask) ?: Geometry(0, 0, request.mask.width, request.mask.height, request.mask.width / 2f, request.mask.height / 2f)
        val lines = layout(request, text, request.mask, fallbackGeometry, request.minSizePx, fallbacks.last(), checkInk = false)
            ?: error("Overflow layout must retain all text")
        return BubbleOverlay(request.bubbleId, lines, overflow = true)
    }

    private fun layout(
        request: FitRequest,
        text: String,
        mask: PixelMask,
        geometry: Geometry,
        size: Float,
        fallback: Fallback,
        checkInk: Boolean,
    ): List<OverlayLine>? {
        val style = FitStyle(request.fontId, size, fallback.letterSpacing, fallback.scaleX, request.strokeWidthPx)
        val available = (geometry.right - geometry.left).toFloat() - if (request.leftAligned) 8f else 0f
        if (available <= 0 && checkInk) return null
        val lines = wrap(text, available.coerceAtLeast(1f), style, fallback.hyphenate, allowOverflow = !checkInk) ?: return null
        val metrics = rasterizer.metrics(style)
        val lineHeight = metrics.descent - metrics.ascent
        val advance = lineHeight * fallback.lineSpacing
        val totalHeight = lineHeight + (lines.size - 1) * advance
        val firstBaseline = geometry.centerY - totalHeight / 2f - metrics.ascent
        val result = ArrayList<OverlayLine>(lines.size)
        for ((index, line) in lines.withIndex()) {
            val width = rasterizer.measure(line, style)
            val x = (if (request.leftAligned) geometry.left + 8f else geometry.centerX - width / 2f).roundToInt()
            val baseline = (firstBaseline + index * advance).roundToInt()
            if (checkInk) {
                val ink = rasterizer.rasterize(line, style)
                if (!mask.containsInk(ink.mask, x + ink.left, baseline + ink.top)) return null
            }
            result += OverlayLine(
                text = line,
                baseline = PointF(request.origin.x + x, request.origin.y + baseline),
                fontId = style.fontId,
                fontSizePx = size,
                color = request.color,
                strokeColor = request.strokeColor,
                strokeWidthPx = request.strokeWidthPx,
                letterSpacingEm = style.letterSpacingEm,
                textScaleX = style.scaleX,
                rotationDegrees = 0f,
            )
        }
        return result
    }

    private fun wrap(text: String, width: Float, style: FitStyle, hyphenate: Boolean, allowOverflow: Boolean): List<String>? {
        val words = text.split(' ')
        val expanded = mutableListOf<String>()
        for (word in words) {
            if (rasterizer.measure(word, style) <= width) {
                expanded += word
                continue
            }
            var remainder = word
            while (hyphenate && rasterizer.measure(remainder, style) > width) {
                val split = RussianHyphenator.positions(remainder).lastOrNull {
                    rasterizer.measure(remainder.substring(0, it) + "-", style) <= width
                } ?: break
                expanded += remainder.substring(0, split) + "-"
                remainder = remainder.substring(split)
            }
            if (rasterizer.measure(remainder, style) > width && !allowOverflow) return null
            expanded += remainder
        }
        val lines = mutableListOf<String>()
        var current = ""
        for (word in expanded) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && rasterizer.measure(candidate, style) > width) {
                lines += current
                current = word
            } else current = candidate
            if (word.endsWith('-')) {
                lines += current
                current = ""
            }
        }
        if (current.isNotEmpty()) lines += current
        if (lines.size >= 2) {
            val last = lines.lastIndex
            val previousWords = lines[last - 1].split(' ')
            if (previousWords.size > 1 && (lines[last].length == 1 || rasterizer.measure(lines[last], style) < width * 0.3f)) {
                val candidate = previousWords.last() + " " + lines[last]
                if (!previousWords.last().endsWith('-') && rasterizer.measure(candidate, style) <= width) {
                    lines[last - 1] = previousWords.dropLast(1).joinToString(" ")
                    lines[last] = candidate
                }
            }
        }
        return lines
    }

    private fun geometry(mask: PixelMask): Geometry? {
        var left = mask.width
        var top = mask.height
        var right = 0
        var bottom = 0
        var sumX = 0.0
        var sumY = 0.0
        var count = 0L
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            if (!mask[x, y]) continue
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x + 1)
            bottom = maxOf(bottom, y + 1)
            sumX += x + 0.5
            sumY += y + 0.5
            count++
        }
        if (count == 0L) return null
        return Geometry(left, top, right, bottom, (sumX / count).toFloat(), (sumY / count).toFloat())
    }

    private data class Geometry(val left: Int, val top: Int, val right: Int, val bottom: Int, val centerX: Float, val centerY: Float)
    private data class Fallback(val lineSpacing: Float, val hyphenate: Boolean, val letterSpacing: Float, val scaleX: Float)

    companion object {
        private val fallbacks = listOf(
            Fallback(1f, false, 0f, 1f),
            Fallback(0.9f, false, 0f, 1f),
            Fallback(0.9f, true, 0f, 1f),
            Fallback(0.9f, true, -0.02f, 1f),
        ) + listOf(0.95f, 0.9f, 0.85f, 0.8f, 0.75f).map { Fallback(0.9f, true, -0.02f, it) }
    }
}

// Консервативные слоговые границы: без одиночной буквы, отрыва знаков и частей без гласных.
object RussianHyphenator {
    private val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
    private val attached = "ьъйЬЪЙ"
    fun positions(word: String): List<Int> {
        if (word.length < 4 || word.any { it !in 'а'..'я' && it !in 'А'..'Я' && it != 'ё' && it != 'Ё' }) return emptyList()
        return (2..word.length - 2).filter { index ->
            word[index] !in attached && word.substring(0, index).any { it in vowels } &&
                word.substring(index).any { it in vowels } &&
                (word[index - 1] in vowels || (word[index] !in vowels && word[index - 1] !in attached))
        }
    }
}
