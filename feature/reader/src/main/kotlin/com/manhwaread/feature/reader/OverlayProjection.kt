package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.contains

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
