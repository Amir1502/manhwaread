package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.contains

// Цвет маскирующей подложки, если цвет заполнения бабла не известен:
// белый — как бумага типового облачка реплики.
private const val DEFAULT_BACKGROUND_COLOR = 0xFFFFFFFF.toInt()

// Проекция векторного спека в экранные координаты: размеры и интервалы
// умножаются на scale — перевод остаётся резким на любом зуме
// (ключевое архитектурное решение продукта).
// ProjectedOverlayLine объявлена в ReaderModels.kt (общие модели читалки).
fun OverlaySpec.project(transform: ViewportTransform): List<ProjectedOverlayLine> {
    val spec = this
    return spec.lines.map { line ->
        ProjectedOverlayLine(
            bubbleId = spec.bubbleId,
            text = line.text,
            x = transform.toScreenX(line.baselineStart.x),
            baselineY = transform.toScreenY(line.baselineStart.y),
            sizePx = spec.sizePx * transform.scale,
            letterSpacingPx = spec.letterSpacing * transform.scale,
            scaleX = spec.scaleX,
            colorArgb = spec.colorArgb,
            fontFamily = spec.fontFamily,
        )
    }
}

// Проекция маскирующих подложек в экранные координаты: подложка нужна только
// переведённым баблам (у которых есть хотя бы один OverlaySpec), чтобы закрыть
// оригинальный текст фигурой бабла перед отрисовкой русского текста поверх.
fun projectBackgrounds(
    overlays: List<OverlaySpec>,
    bubbles: List<BubbleHitArea>,
    transform: ViewportTransform,
): List<ProjectedOverlayBackground> {
    val translatedIds = overlays.mapTo(mutableSetOf()) { spec -> spec.bubbleId }
    return bubbles
        .filter { bubble -> bubble.bubbleId in translatedIds }
        .map { bubble -> bubble.projectBackground(transform) }
}

private fun BubbleHitArea.projectBackground(transform: ViewportTransform): ProjectedOverlayBackground =
    ProjectedOverlayBackground(
        bubbleId = bubbleId,
        shape = shape,
        left = transform.toScreenX(bounds.left),
        top = transform.toScreenY(bounds.top),
        right = transform.toScreenX(bounds.right),
        bottom = transform.toScreenY(bounds.bottom),
        polygon = polygon.map { point ->
            PointF(x = transform.toScreenX(point.x), y = transform.toScreenY(point.y))
        },
        colorArgb = fillColorArgb ?: DEFAULT_BACKGROUND_COLOR,
    )

// Тап по баблу: экранная точка переводится в координаты изображения,
// победитель — последний по списку (z-order: верхние баблы позже).
fun hitTestBubble(
    tapScreenX: Float,
    tapScreenY: Float,
    transform: ViewportTransform,
    bubbles: List<BubbleHitArea>,
): BubbleHitArea? {
    val imageX = transform.toImageX(tapScreenX)
    val imageY = transform.toImageY(tapScreenY)
    return bubbles.lastOrNull { bubble -> bubble.bounds.contains(imageX, imageY) }
}
