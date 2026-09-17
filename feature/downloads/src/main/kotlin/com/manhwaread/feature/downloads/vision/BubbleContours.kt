package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF

// Сырой контур детектора: точки в пикселях страницы и площадь контура.
data class RawContour(val points: List<PointF>, val area: Double)

// Параметры фильтрации контуров: калиброваны под манхву (светлые баблы
// на насыщенном фоне) и мангу (чёрно-белые страницы).
data class BubbleFilterConfig(
    // Минимальная и максимальная площадь контура в долях площади страницы.
    val minAreaFraction: Float = 0.0004f,
    val maxAreaFraction: Float = 0.55f,
    // Границы отношения ширины к высоте: отсекают тонкие полосы и текст.
    val minAspectRatio: Float = 0.25f,
    val maxAspectRatio: Float = 8f,
    // Вырожденные контуры (линия, точка).
    val minPoints: Int = 4,
    // Классификация NARRATION_BOX: широкий и небольшой контур.
    val narrationAspectRatio: Float = 3f,
    val narrationMaxAreaFraction: Float = 0.04f,
)

// Ограничивающий прямоугольник набора точек; для пустого списка — нулевой.
fun contourBounds(points: List<PointF>): RectF {
    if (points.isEmpty()) {
        return RectF(0f, 0f, 0f, 0f)
    }
    var left = points[0].x
    var top = points[0].y
    var right = points[0].x
    var bottom = points[0].y
    for (point in points) {
        left = minOf(left, point.x)
        top = minOf(top, point.y)
        right = maxOf(right, point.x)
        bottom = maxOf(bottom, point.y)
    }
    return RectF(left, top, right, bottom)
}

// Масштабирование контура из системы координат анализа в координаты страницы
// (детектор может работать на уменьшенной копии изображения).
fun scaleContour(raw: RawContour, factorX: Float, factorY: Float): RawContour =
    RawContour(
        points = raw.points.map { point -> PointF(point.x * factorX, point.y * factorY) },
        area = raw.area * factorX.toDouble() * factorY.toDouble(),
    )

// Классификация бабла: длинный узкий блок малой площади — бокс narration.
fun classifyBubbleKind(
    bounds: RectF,
    area: Double,
    pageWidth: Int,
    pageHeight: Int,
    config: BubbleFilterConfig,
): BubbleKind {
    val height = bounds.bottom - bounds.top
    if (height <= 0f) {
        return BubbleKind.SPEECH
    }
    val aspect = (bounds.right - bounds.left) / height
    val pageArea = pageWidth.toFloat() * pageHeight.toFloat()
    val isNarration = aspect >= config.narrationAspectRatio && area <= config.narrationMaxAreaFraction * pageArea
    return if (isNarration) BubbleKind.NARRATION_BOX else BubbleKind.SPEECH
}

// Отсеян ли контур по форме: площадь и пропорции относительно страницы.
fun isRejectedContour(
    bounds: RectF,
    area: Double,
    pageWidth: Int,
    pageHeight: Int,
    config: BubbleFilterConfig,
): Boolean {
    val pageArea = pageWidth.toFloat() * pageHeight.toFloat()
    if (area < config.minAreaFraction * pageArea || area > config.maxAreaFraction * pageArea) {
        return true
    }
    val height = bounds.bottom - bounds.top
    if (height <= 0f || bounds.right - bounds.left <= 0f) {
        return true
    }
    val aspect = (bounds.right - bounds.left) / height
    return aspect < config.minAspectRatio || aspect > config.maxAspectRatio
}

// Контуры → Bubble: фильтрация, детерминированная сортировка (сверху вниз,
// слева направо — порядок нумерации id совпадает с визуальным), классификация.
fun toBubbles(
    raw: List<RawContour>,
    pageWidth: Int,
    pageHeight: Int,
    pageIndex: Int,
    config: BubbleFilterConfig = BubbleFilterConfig(),
): List<Bubble> =
    raw.asSequence()
        .filter { contour -> contour.points.size >= config.minPoints }
        .map { contour -> contour to contourBounds(contour.points) }
        .filter { (contour, bounds) ->
            !isRejectedContour(bounds, contour.area, pageWidth, pageHeight, config)
        }
        .sortedWith(compareBy({ pair -> pair.second.top }, { pair -> pair.second.left }))
        .mapIndexed { index, pair ->
            val (contour, bounds) = pair
            Bubble(
                id = "p${pageIndex}b$index",
                pageIndex = pageIndex,
                polygon = contour.points,
                bounds = bounds,
                kind = classifyBubbleKind(bounds, contour.area, pageWidth, pageHeight, config),
                zOrder = index,
            )
        }
        .toList()
