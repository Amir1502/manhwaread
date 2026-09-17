package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.PointF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BubbleContoursTest {
    // Страница 1000×2000: площадь 2_000_000; minArea = 800, maxArea = 1_100_000.
    private val pageWidth = 1000
    private val pageHeight = 2000

    private fun rectContour(left: Float, top: Float, right: Float, bottom: Float): RawContour {
        val area = (right - left).toDouble() * (bottom - top).toDouble()
        return RawContour(
            points = listOf(
                PointF(left, top),
                PointF(right, top),
                PointF(right, bottom),
                PointF(left, bottom),
            ),
            area = area,
        )
    }

    @Test
    fun `tiny contour is rejected`() {
        val result = toBubbles(listOf(rectContour(10f, 10f, 30f, 30f)), pageWidth, pageHeight, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `page-sized contour is rejected`() {
        val result = toBubbles(listOf(rectContour(5f, 5f, 995f, 1995f)), pageWidth, pageHeight, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `thin sliver contour is rejected`() {
        val result = toBubbles(listOf(rectContour(100f, 100f, 110f, 600f)), pageWidth, pageHeight, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `degenerate contour with too few points is rejected`() {
        val triangle = RawContour(
            points = listOf(PointF(0f, 0f), PointF(300f, 0f), PointF(0f, 300f)),
            area = 45_000.0,
        )
        val result = toBubbles(listOf(triangle), pageWidth, pageHeight, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `valid contour becomes speech bubble with expected fields`() {
        val result = toBubbles(listOf(rectContour(100f, 100f, 300f, 400f)), pageWidth, pageHeight, 3)
        assertEquals(1, result.size)
        val bubble = result[0]
        assertEquals("p3b0", bubble.id)
        assertEquals(3, bubble.pageIndex)
        assertEquals(4, bubble.polygon.size)
        assertEquals(100f, bubble.bounds.left)
        assertEquals(100f, bubble.bounds.top)
        assertEquals(300f, bubble.bounds.right)
        assertEquals(400f, bubble.bounds.bottom)
        assertEquals(BubbleKind.SPEECH, bubble.kind)
        assertEquals(0, bubble.zOrder)
    }

    @Test
    fun `ids follow visual order top to bottom`() {
        val lower = rectContour(100f, 1500f, 400f, 1800f)
        val upper = rectContour(100f, 100f, 400f, 400f)
        // Намеренно подаём нижний контур первым — сортировка детерминирована.
        val result = toBubbles(listOf(lower, upper), pageWidth, pageHeight, 0)
        assertEquals(2, result.size)
        assertEquals("p0b0", result[0].id)
        assertEquals(100f, result[0].bounds.top)
        assertEquals("p0b1", result[1].id)
        assertEquals(1500f, result[1].bounds.top)
    }

    @Test
    fun `wide small contour is classified as narration box`() {
        val result = toBubbles(listOf(rectContour(100f, 100f, 500f, 160f)), pageWidth, pageHeight, 0)
        assertEquals(1, result.size)
        assertEquals(BubbleKind.NARRATION_BOX, result[0].kind)
    }

    @Test
    fun `scaleContour scales points and area non-uniformly`() {
        val scaled = scaleContour(rectContour(10f, 10f, 20f, 30f), factorX = 2f, factorY = 3f)
        assertEquals(40f, scaled.points[2].x)
        assertEquals(90f, scaled.points[2].y)
        // Исходная площадь 200, множители 2×3 → 1200.
        assertEquals(1200.0, scaled.area, AREA_EPS)
    }

    @Test
    fun `zero-height bounds are rejected`() {
        val flat = RawContour(
            points = listOf(PointF(0f, 5f), PointF(300f, 5f), PointF(600f, 5f), PointF(900f, 5f)),
            area = 60_000.0,
        )
        val result = toBubbles(listOf(flat), pageWidth, pageHeight, 0)
        assertTrue(result.isEmpty())
    }

    private companion object {
        const val AREA_EPS = 0.001
    }
}
