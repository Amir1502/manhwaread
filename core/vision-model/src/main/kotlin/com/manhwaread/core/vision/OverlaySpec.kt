package com.manhwaread.core.vision

// OverlaySpec — векторная инструкция отрисовки перевода (КЛЮЧЕВОЕ РЕШЕНИЕ ПРОДУКТА:
// перевод НЕ запекается в растр). Рендер-слой читалки рисует spec поверх оригинала,
// поэтому зум остаётся резким, а правка текста/шрифта не требует повторного перевода.

/** Выравнивание строк оверлея относительно оси бабла. */
enum class OverlayAlign { START, CENTER, END }

/** Одна строка перевода: текст, начало базовой линии и измеренная ширина. */
data class OverlayLine(
    val text: String,
    val baselineStart: PointF,
    val widthPx: Float,
)

/** Стиль отрисовки оверлея, не влияющий на раскладку. */
data class OverlayStyle(
    val fontFamily: String? = null,
    val colorArgb: Int = OverlaySpec.DEFAULT_COLOR,
    val align: OverlayAlign = OverlayAlign.CENTER,
)

/** Полный векторный слой перевода одного бабла. */
data class OverlaySpec(
    val bubbleId: String,
    val pageIndex: Int,
    val lines: List<OverlayLine>,
    val sizePx: Float,
    val lineSpacingMult: Float,
    val letterSpacing: Float,
    val scaleX: Float,
    val fontFamily: String? = null,
    val colorArgb: Int = DEFAULT_COLOR,
    val align: OverlayAlign = OverlayAlign.CENTER,
) {
    companion object {
        val DEFAULT_COLOR: Int = 0xFF101010.toInt()
    }
}

/**
 * Строит [OverlaySpec] из результата [fit]: строки центрируются блоком по высоте
 * маски и выравниваются по [OverlayStyle.align]. [baselineStart] — начало базовой
 * линии строки (низ строки ≈ y + lineHeight).
 */
fun buildOverlay(
    bubbleId: String,
    mask: Mask,
    result: FitResult,
    measurer: TextMeasurer,
    style: OverlayStyle = OverlayStyle(),
    config: FitConfig = FitConfig(),
): OverlaySpec {
    val lineH = measurer.lineHeight(result.sizePx, result.lineSpacingMult)
    val totalHeight = lineH * result.lines.size
    val availHeight = (mask.bounds.height() - 2 * config.paddingPx).coerceAtLeast(0f)
    var y = mask.bounds.top + config.paddingPx + (availHeight - totalHeight).coerceAtLeast(0f) / 2f
    val lines = result.lines.map { text ->
        val width = measurer.measureLine(text, result.sizePx, result.letterSpacing, result.scaleX)
        val x = when (style.align) {
            OverlayAlign.CENTER -> mask.bounds.centerX() - width / 2f
            OverlayAlign.START -> mask.bounds.left + config.paddingPx
            OverlayAlign.END -> mask.bounds.right - config.paddingPx - width
        }
        val line = OverlayLine(text = text, baselineStart = PointF(x, y + lineH), widthPx = width)
        y += lineH
        line
    }
    return OverlaySpec(
        bubbleId = bubbleId,
        pageIndex = mask.pageIndex,
        lines = lines,
        sizePx = result.sizePx,
        lineSpacingMult = result.lineSpacingMult,
        letterSpacing = result.letterSpacing,
        scaleX = result.scaleX,
        fontFamily = style.fontFamily,
        colorArgb = style.colorArgb,
        align = style.align,
    )
}
