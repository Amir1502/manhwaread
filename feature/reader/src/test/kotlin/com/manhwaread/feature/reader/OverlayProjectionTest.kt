package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OverlayProjectionTest {
    private fun spec() = OverlaySpec(
        bubbleId = "b1",
        pageIndex = 0,
        lines = listOf(
            OverlayLine(text = "Привет", baselineStart = PointF(x = 10f, y = 30f), widthPx = 60f),
            OverlayLine(text = "мир", baselineStart = PointF(x = 12f, y = 55f), widthPx = 30f),
        ),
        sizePx = 20f,
        lineSpacingMult = 1f,
        letterSpacing = 0.5f,
        scaleX = 0.9f,
        colorArgb = BLACK,
    )

    private fun bubble(id: String, bounds: RectF) =
        BubbleHitArea(bubbleId = id, pageIndex = 0, bounds = bounds, originalText = "원문", translatedText = "перевод")

    @Test
    fun `project scales positions sizes and spacing`() {
        val transform = ViewportTransform(scale = 2f, offsetX = 5f, offsetY = -7f)
        val lines = spec().project(transform)
        assertEquals(2, lines.size)
        val first = lines[0]
        assertEquals(25f, first.x, EPS)
        assertEquals(53f, first.baselineY, EPS)
        assertEquals(40f, first.sizePx, EPS)
        assertEquals(1f, first.letterSpacingPx, EPS)
        // Горизонтальное сжатие — параметр шрифта, зумом не масштабируется.
        assertEquals(0.9f, first.scaleX, EPS)
        assertEquals("b1", first.bubbleId)
        assertEquals("Привет", first.text)
        assertEquals(BLACK, first.colorArgb)
    }

    @Test
    fun `project scales every line`() {
        val transform = ViewportTransform(scale = 3f, offsetX = 0f, offsetY = 0f)
        val lines = spec().project(transform)
        assertEquals(36f, lines[1].x, EPS)
        assertEquals(165f, lines[1].baselineY, EPS)
        assertEquals(60f, lines[1].sizePx, EPS)
    }

    @Test
    fun `hit test finds bubble under tap`() {
        val transform = ViewportTransform(scale = 2f, offsetX = 0f, offsetY = 0f)
        val hit = hitTestBubble(100f, 50f, transform, listOf(bubble("b1", RectF(0f, 0f, 100f, 50f))))
        assertEquals("b1", hit?.bubbleId)
    }

    @Test
    fun `hit test misses outside all bubbles`() {
        val transform = ViewportTransform(scale = 2f, offsetX = 0f, offsetY = 0f)
        val hit = hitTestBubble(500f, 500f, transform, listOf(bubble("b1", RectF(0f, 0f, 100f, 50f))))
        assertNull(hit)
    }

    @Test
    fun `hit test picks topmost by list order`() {
        val transform = ViewportTransform(scale = 1f, offsetX = 0f, offsetY = 0f)
        val bubbles = listOf(
            bubble("bottom", RectF(0f, 0f, 100f, 100f)),
            bubble("top", RectF(50f, 50f, 150f, 150f)),
        )
        assertEquals("top", hitTestBubble(75f, 75f, transform, bubbles)?.bubbleId)
    }

    @Test
    fun `hit test accounts for pan offset`() {
        val transform = ViewportTransform(scale = 1f, offsetX = -40f, offsetY = -20f)
        // Экранные (60, 30) — это (100, 50) изображения: внутри прямоугольника.
        val hit = hitTestBubble(60f, 30f, transform, listOf(bubble("b1", RectF(90f, 40f, 110f, 60f))))
        assertEquals("b1", hit?.bubbleId)
    }

    private companion object {
        const val EPS = 0.0001f
        val BLACK = 0xFF000000.toInt()
    }
}
