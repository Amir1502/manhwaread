package com.manhwaread.core.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryTest {
    private val rect = RectF(10f, 20f, 110f, 220f)

    @Test
    fun `rect dimensions and center`() {
        assertEquals(100f, rect.width())
        assertEquals(200f, rect.height())
        assertEquals(60f, rect.centerX())
        assertEquals(120f, rect.centerY())
    }

    @Test
    fun `contains point inside and outside`() {
        assertTrue(rect.contains(60f, 120f))
        assertTrue(rect.contains(10f, 20f))
        assertFalse(rect.contains(9.9f, 120f))
        assertFalse(rect.contains(60f, 221f))
    }

    @Test
    fun `contains point object`() {
        assertTrue(rect.contains(PointF(110f, 220f)))
        assertFalse(rect.contains(PointF(111f, 220f)))
    }

    @Test
    fun `intersects detects overlap`() {
        assertTrue(rect.intersects(RectF(100f, 200f, 200f, 300f)))
    }

    @Test
    fun `intersects rejects disjoint and touching`() {
        assertFalse(rect.intersects(RectF(200f, 300f, 300f, 400f)))
        assertFalse(RectF(0f, 0f, 10f, 10f).intersects(RectF(10f, 0f, 20f, 10f)))
    }

    @Test
    fun `intersect computes overlap rect`() {
        assertEquals(RectF(100f, 200f, 110f, 220f), rect.intersect(RectF(100f, 200f, 300f, 400f)))
    }

    @Test
    fun `intersect returns null for disjoint`() {
        assertNull(rect.intersect(RectF(500f, 500f, 600f, 600f)))
    }

    @Test
    fun `area of rect`() {
        assertEquals(20000f, rect.area())
    }

    @Test
    fun `polygonBounds of triangle`() {
        val triangle = listOf(PointF(5f, 1f), PointF(0f, 9f), PointF(10f, 4f))
        assertEquals(RectF(0f, 1f, 10f, 9f), triangle.polygonBounds())
    }

    @Test
    fun `polygonArea of square and triangle`() {
        val square = listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f))
        assertEquals(100f, square.polygonArea(), 0.001f)
        val triangle = listOf(PointF(0f, 0f), PointF(4f, 0f), PointF(0f, 3f))
        assertEquals(6f, triangle.polygonArea(), 0.001f)
        assertEquals(0f, listOf(PointF(0f, 0f), PointF(1f, 1f)).polygonArea())
    }

    @Test
    fun `polygonContains inside convex polygon`() {
        val square = listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f))
        assertTrue(square.polygonContains(5f, 5f))
        assertTrue(square.polygonContains(1f, 9f))
    }

    @Test
    fun `polygonContains outside polygon`() {
        val square = listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f))
        assertFalse(square.polygonContains(15f, 5f))
        assertFalse(square.polygonContains(-1f, 5f))
    }

    @Test
    fun `polygonContains respects concave notch`() {
        // L-образный полигон: точка (7,7) попадает в вырез.
        val shape = listOf(
            PointF(0f, 0f),
            PointF(10f, 0f),
            PointF(10f, 5f),
            PointF(5f, 5f),
            PointF(5f, 10f),
            PointF(0f, 10f),
        )
        assertTrue(shape.polygonContains(2f, 2f))
        assertTrue(shape.polygonContains(7f, 2f))
        assertTrue(shape.polygonContains(2f, 7f))
        assertFalse(shape.polygonContains(7f, 7f))
    }
}
