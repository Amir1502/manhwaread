package com.manhwaread.feature.downloads.vision

import android.graphics.Paint
import com.manhwaread.core.vision.TextMeasurer

// Измеритель текста на Android Paint (ФАЗА 15): метрики совпадают с
// рендер-слоем читалки, поэтому вёрстка оверлея стабильна на экране.
// letterSpacing — в em от кегля, scaleX — доля единицы (Paint.textScaleX — проценты).
class PaintTextMeasurer : TextMeasurer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun measureLine(text: String, sizePx: Float, letterSpacing: Float, scaleX: Float): Float {
        if (text.isEmpty()) return 0f
        applyMetrics(sizePx, letterSpacing, scaleX)
        return paint.measureText(text)
    }

    override fun lineHeight(sizePx: Float, lineSpacingMult: Float): Float {
        applyMetrics(sizePx, letterSpacing = 0f, scaleX = 1f)
        val metrics = paint.fontMetrics
        return (metrics.descent - metrics.ascent) * lineSpacingMult
    }

    private fun applyMetrics(sizePx: Float, letterSpacing: Float, scaleX: Float) {
        paint.textSize = sizePx
        paint.letterSpacing = letterSpacing
        paint.textScaleX = scaleX * SCALE_PERCENT_BASE
    }

    private companion object {
        const val SCALE_PERCENT_BASE = 100f
    }
}
