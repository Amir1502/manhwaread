package com.manhwaread.core.vision

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Маска внутренней области бабла в координатах страницы.
 * [fit] использует её для расчёта доступной ширины строки и высоты блока.
 */
interface Mask {
    val pageIndex: Int
    val bounds: RectF

    /** Ширина доступной области на горизонтали [y]; 0, если y вне маски. */
    fun widthAt(y: Float): Float

    /** Принадлежит ли точка внутренней области маски. */
    fun contains(x: Float, y: Float): Boolean
}

/** Прямоугольная маска (нарративные боксы, панели SFX). */
class RectMask(
    override val pageIndex: Int,
    override val bounds: RectF,
) : Mask {
    override fun widthAt(y: Float): Float =
        if (y >= bounds.top && y <= bounds.bottom) bounds.width() else 0f

    override fun contains(x: Float, y: Float): Boolean = bounds.contains(x, y)
}

/** Эллиптическая маска (классические овальные баблы речи). */
class EllipseMask(
    override val pageIndex: Int,
    override val bounds: RectF,
) : Mask {
    override fun widthAt(y: Float): Float {
        val ry = bounds.height() / 2f
        if (ry <= 0f) return 0f
        val dy = (y - bounds.centerY()) / ry
        if (abs(dy) >= 1f) return 0f
        // Хорда эллипса на высоте y: w * sqrt(1 - dy^2).
        return bounds.width() * sqrt(1f - dy * dy)
    }

    override fun contains(x: Float, y: Float): Boolean {
        val rx = bounds.width() / 2f
        val ry = bounds.height() / 2f
        if (rx <= 0f || ry <= 0f) return false
        val dx = (x - bounds.centerX()) / rx
        val dy = (y - bounds.centerY()) / ry
        return dx * dx + dy * dy <= 1f
    }
}

/** Полигональная маска (произвольный контур бабла из сегментации). */
class PolygonMask(
    override val pageIndex: Int,
    val polygon: List<PointF>,
) : Mask {
    init {
        require(polygon.size >= MIN_POLYGON_POINTS) {
            "polygon must have at least $MIN_POLYGON_POINTS points, got ${polygon.size}"
        }
    }

    override val bounds: RectF = polygon.polygonBounds()

    override fun widthAt(y: Float): Float {
        // Сканирующая строка: собираем пересечения со всеми рёбрами.
        val crossings = mutableListOf<Float>()
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val t = crossingParameter(a, b, y)
            if (t != null) crossings += a.x + t * (b.x - a.x)
        }
        val min = crossings.minOrNull() ?: return 0f
        val max = crossings.maxOrNull() ?: return 0f
        return (max - min).coerceAtLeast(0f)
    }

    override fun contains(x: Float, y: Float): Boolean = polygon.polygonContains(x, y)

    // Параметр пересечения ребра a→b с горизонталью y; null, если ребро не пересекает.
    private fun crossingParameter(a: PointF, b: PointF, y: Float): Float? {
        if ((a.y > y) == (b.y > y)) return null
        val dy = b.y - a.y
        if (dy == 0f) return null
        return (y - a.y) / dy
    }

    private companion object {
        const val MIN_POLYGON_POINTS = 3
    }
}
