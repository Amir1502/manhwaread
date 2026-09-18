package com.manhwaread.feature.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

// Векторный слой перевода поверх страницы: рисует спроецированные строки
// OverlaySpec. Перевод НЕ запекается в растр — слой мгновенно включается
// и выключается, остаётся резким на любом зуме (ключевое решение продукта).
class OverlayLayerView(context: Context) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    // Подложка рисуется заливкой без обводки; общий Path переиспользуется,
    // чтобы не мусорить в onDraw.
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val polygonPath = Path()
    private var lines: List<ProjectedOverlayLine> = emptyList()
    private var backgrounds: List<ProjectedOverlayBackground> = emptyList()
    private var overlayVisible = true

    fun setOverlayLines(newLines: List<ProjectedOverlayLine>) {
        if (lines == newLines) {
            return
        }
        lines = newLines
        invalidate()
    }

    fun setBackgrounds(newBackgrounds: List<ProjectedOverlayBackground>) {
        if (backgrounds == newBackgrounds) {
            return
        }
        backgrounds = newBackgrounds
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
        // Сначала маскирующие подложки (закрывают оригинал), затем строки перевода.
        for (background in backgrounds) {
            drawBackground(canvas, background)
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

    private fun drawBackground(canvas: Canvas, background: ProjectedOverlayBackground) {
        backgroundPaint.color = background.colorArgb
        when (background.shape) {
            BubbleMaskShape.POLYGON -> drawPolygon(canvas, background)
            BubbleMaskShape.RECT -> canvas.drawRoundRect(
                background.left,
                background.top,
                background.right,
                background.bottom,
                RECT_CORNER_RADIUS,
                RECT_CORNER_RADIUS,
                backgroundPaint,
            )
            BubbleMaskShape.ELLIPSE -> canvas.drawOval(
                background.left,
                background.top,
                background.right,
                background.bottom,
                backgroundPaint,
            )
        }
    }

    private fun drawPolygon(canvas: Canvas, background: ProjectedOverlayBackground) {
        polygonPath.reset()
        background.polygon.forEachIndexed { index, point ->
            if (index == 0) {
                polygonPath.moveTo(point.x, point.y)
            } else {
                polygonPath.lineTo(point.x, point.y)
            }
        }
        polygonPath.close()
        canvas.drawPath(polygonPath, backgroundPaint)
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

        // Малый радиус скругления прямоугольной подложки (экранные px).
        const val RECT_CORNER_RADIUS = 6f
    }
}
