package com.manhwaread.core.vision

import kotlin.math.abs

/**
 * Своя геометрия (закреплённый контракт ФАЗЫ 5): НЕ android.graphics.*,
 * поэтому модуль собирается и тестируется без Android SDK.
 */
data class PointF(val x: Float, val y: Float)

/** Прямоугольник в координатах страницы (пиксели исходного растра). */
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

fun RectF.width(): Float = right - left

fun RectF.height(): Float = bottom - top

fun RectF.centerX(): Float = (left + right) / 2f

fun RectF.centerY(): Float = (top + bottom) / 2f

fun RectF.contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

fun RectF.contains(point: PointF): Boolean = contains(point.x, point.y)

/** Пересечение: строгое (касание гранью пересечением не считается). */
fun RectF.intersects(other: RectF): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

/** Прямоугольник пересечения или null, если прямоугольники не пересекаются. */
fun RectF.intersect(other: RectF): RectF? =
    if (!intersects(other)) {
        null
    } else {
        RectF(
            maxOf(left, other.left),
            maxOf(top, other.top),
            minOf(right, other.right),
            minOf(bottom, other.bottom),
        )
    }

fun RectF.area(): Float = width() * height()

/** Ограничивающий прямоугольник полигона. */
fun List<PointF>.polygonBounds(): RectF {
    require(isNotEmpty()) { "polygon must have at least one point" }
    val xs = map { it.x }
    val ys = map { it.y }
    return RectF(xs.min(), ys.min(), xs.max(), ys.max())
}

/** Площадь простого полигона (формула шнурования), знак отбрасывается. */
fun List<PointF>.polygonArea(): Float {
    if (size < TRIANGLE_POINTS) return 0f
    var sum = 0f
    for (i in indices) {
        val a = this[i]
        val b = this[(i + 1) % size]
        sum += a.x * b.y - b.x * a.y
    }
    return abs(sum) / 2f
}

/** Принадлежность точки простому полигону (алгоритм трассировки луча). */
fun List<PointF>.polygonContains(x: Float, y: Float): Boolean {
    if (size < TRIANGLE_POINTS) return false
    var inside = false
    var j = lastIndex
    for (i in indices) {
        val a = this[i]
        val b = this[j]
        val crosses = (a.y > y) != (b.y > y)
        if (crosses && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private const val TRIANGLE_POINTS = 3
