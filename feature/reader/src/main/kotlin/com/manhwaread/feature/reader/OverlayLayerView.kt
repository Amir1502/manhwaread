package com.manhwaread.feature.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

// Векторный слой перевода поверх страницы: рисует спроецированные строки
// OverlaySpec. Перевод НЕ запекается в растр — слой мгновенно включается
// и выключается, остаётся резким на любом зуме (ключевое решение продукта).
class OverlayLayerView(context: Context) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var lines: List<ProjectedOverlayLine> = emptyList()
    private var overlayVisible = true

    fun setOverlayLines(newLines: List<ProjectedOverlayLine>) {
        if (lines == newLines) {
            return
        }
        lines = newLines
        invalidate()
    }

    fun setOverlayVisible(visible: Boolean) {
        if (overlayVisible == visible) {
            return
        }
        overlayVisible = visible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!overlayVisible) {
            return
        }
        for (line in lines) {
            textPaint.color = line.colorArgb
            textPaint.textSize = line.sizePx
            // Paint.letterSpacing измеряется в em; спек хранит абсолютные px — переводим по кеглю.
            textPaint.letterSpacing = if (line.sizePx > 0f) line.letterSpacingPx / line.sizePx else 0f
            textPaint.textScaleX = line.scaleX * SCALE_X_PERCENT
            textPaint.typeface = typefaceFor(line.fontFamily)
            canvas.drawText(line.text, line.x, line.baselineY, textPaint)
        }
    }

    private fun typefaceFor(fontFamily: String?): Typeface =
        if (fontFamily == null) {
            Typeface.DEFAULT
        } else {
            Typeface.create(fontFamily, Typeface.NORMAL)
        }

    private companion object {
        // Paint.textScaleX измеряется в процентах: 1.0 соответствует 100.
        const val SCALE_X_PERCENT = 100f
    }
}
