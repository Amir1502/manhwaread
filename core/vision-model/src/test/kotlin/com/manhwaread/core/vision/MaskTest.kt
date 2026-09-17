package com.manhwaread.core.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class MaskTest {
    private val rect = RectMask(0, RectF(0f, 0f, 100f, 50f))
    private val ellipse = EllipseMask(1, RectF(0f, 0f, 200f, 100f))
    private val squarePolygon = PolygonMask(
        pageIndex = 2,
        polygon = listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)),
    )

    @Test
    fun `rect mask width inside and outside`() {
        assertEquals(100f, rect.widthAt(25f))
        assertEquals(100f, rect.widthAt(0f))
        assertEquals(100f, rect.widthAt(50f))
        assertEquals(0f, rect.widthAt(-0.5f))
        assertEquals(0f, rect.widthAt(50.5f))
    }

    @Test
    fun `masks expose pageIndex and bounds`() {
        assertEquals(0, rect.pageIndex)
        assertEquals(1, ellipse.pageIndex)
        assertEquals(2, squarePolygon.pageIndex)
        assertEquals(RectF(0f, 0f, 100f, 50f), rect.bounds)
        assertEquals(RectF(0f, 0f, 200f, 100f), ellipse.bounds)
    }

    @Test
    fun `ellipse mask width at center equals full width`() {
        assertEquals(200f, ellipse.widthAt(50f), 0.001f)
    }

    @Test
    fun `ellipse mask width shrinks toward poles`() {
        // Хорда эллипса на y=25: dy=-0.5 → 200*sqrt(0.75).
        assertEquals(200f * sqrt(0.75f), ellipse.widthAt(25f), 0.01f)
        assertEquals(200f * sqrt(0.75f), ellipse.widthAt(75f), 0.01f)
    }

    @Test
    fun `ellipse mask width zero at poles and outside`() {
        assertEquals(0f, ellipse.widthAt(0f))
        assertEquals(0f, ellipse.widthAt(100f))
        assertEquals(0f, ellipse.widthAt(120f))
    }

    @Test
    fun `ellipse mask contains center and rejects corner`() {
        assertTrue(ellipse.contains(100f, 50f))
        assertTrue(ellipse.contains(100f, 10f))
        assertFalse(ellipse.contains(5f, 5f))
        assertFalse(ellipse.contains(195f, 95f))
    }

    @Test
    fun `polygon mask bounds`() {
        assertEquals(RectF(0f, 0f, 10f, 10f), squarePolygon.bounds)
    }

    @Test
    fun `polygon mask width at scanline`() {
        assertEquals(10f, squarePolygon.widthAt(5f), 0.001f)
        assertEquals(10f, squarePolygon.widthAt(0.5f), 0.001f)
    }

    @Test
    fun `polygon mask width outside is zero`() {
        assertEquals(0f, squarePolygon.widthAt(15f))
        assertEquals(0f, squarePolygon.widthAt(-1f))
    }

    @Test
    fun `polygon mask contains`() {
        assertTrue(squarePolygon.contains(5f, 5f))
        assertFalse(squarePolygon.contains(15f, 5f))
    }

    @Test
    fun `polygon mask requires at least three points`() {
        assertThrows(IllegalArgumentException::class.java) {
            PolygonMask(0, listOf(PointF(0f, 0f), PointF(1f, 1f)))
        }
    }
}
