package com.manhwaread.core.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlaySpecTest {
    private val measurer = ApproximateTextMeasurer()
    private val mask = RectMask(3, RectF(0f, 0f, 200f, 200f))
    private val fitResult = FitResult(
        lines = listOf("Привет", "мир", "!"),
        sizePx = 20f,
        lineSpacingMult = 1f,
        letterSpacing = 0f,
        scaleX = 1f,
        overflow = false,
        degradations = emptySet(),
    )

    @Test
    fun `overlay copies layout parameters and identity`() {
        val spec = buildOverlay("b1", mask, fitResult, measurer)
        assertEquals("b1", spec.bubbleId)
        assertEquals(3, spec.pageIndex)
        assertEquals(20f, spec.sizePx)
        assertEquals(1f, spec.lineSpacingMult)
        assertEquals(0f, spec.letterSpacing)
        assertEquals(1f, spec.scaleX)
        assertEquals(listOf("Привет", "мир", "!"), spec.lines.map { it.text })
        assertEquals(OverlaySpec.DEFAULT_COLOR, spec.colorArgb)
    }

    @Test
    fun `center align centers lines horizontally`() {
        val spec = buildOverlay("b1", mask, fitResult, measurer)
        // "Привет": 6 символов * 10f = 60f → x = 100 - 30 = 70; базовая линия = 64 + 24 = 88.
        assertEquals(70f, spec.lines[0].baselineStart.x, 0.01f)
        assertEquals(88f, spec.lines[0].baselineStart.y, 0.01f)
        assertEquals(85f, spec.lines[1].baselineStart.x, 0.01f)
        assertEquals(112f, spec.lines[1].baselineStart.y, 0.01f)
        assertEquals(95f, spec.lines[2].baselineStart.x, 0.01f)
        assertEquals(136f, spec.lines[2].baselineStart.y, 0.01f)
        assertEquals(60f, spec.lines[0].widthPx, 0.01f)
    }

    @Test
    fun `start align pins lines to left padding`() {
        val spec = buildOverlay("b1", mask, fitResult, measurer, OverlayStyle(align = OverlayAlign.START))
        spec.lines.forEach { assertEquals(6f, it.baselineStart.x, 0.01f) }
        assertEquals(OverlayAlign.START, spec.align)
    }

    @Test
    fun `end align pins lines to right padding`() {
        val spec = buildOverlay("b1", mask, fitResult, measurer, OverlayStyle(align = OverlayAlign.END))
        spec.lines.forEach { assertEquals(194f, it.baselineStart.x + it.widthPx, 0.01f) }
    }

    @Test
    fun `style fields propagate to spec`() {
        val style = OverlayStyle(fontFamily = "sans-serif-medium", colorArgb = 0xFF112233.toInt())
        val spec = buildOverlay("b1", mask, fitResult, measurer, style)
        assertEquals("sans-serif-medium", spec.fontFamily)
        assertEquals(0xFF112233.toInt(), spec.colorArgb)
        assertEquals(OverlayAlign.CENTER, spec.align)
    }

    @Test
    fun `line widths consistent with measurer`() {
        val spec = buildOverlay("b1", mask, fitResult, measurer)
        spec.lines.forEach { line ->
            val expected = measurer.measureLine(line.text, spec.sizePx, spec.letterSpacing, spec.scaleX)
            assertEquals(expected, line.widthPx, 0.001f)
        }
    }

    @Test
    fun `empty fit result yields empty overlay`() {
        val empty = FitResult(emptyList(), 14f, 1f, 0f, 1f, overflow = false, degradations = emptySet())
        val spec = buildOverlay("b2", mask, empty, measurer)
        assertTrue(spec.lines.isEmpty())
        assertEquals("b2", spec.bubbleId)
    }

    @Test
    fun `overlay lines stay inside mask bounds`() {
        val bigMask = RectMask(0, RectF(50f, 40f, 350f, 260f))
        val result = fit("очень длинный текст который занимает много места в бабле", bigMask, measurer)
        val spec = buildOverlay("b3", bigMask, result, measurer)
        assertTrue(spec.lines.isNotEmpty())
        spec.lines.forEach { line ->
            assertTrue(line.baselineStart.x >= 50f - 0.01f)
            assertTrue(line.baselineStart.x + line.widthPx <= 350f + 0.01f)
            assertTrue(line.baselineStart.y >= 40f)
            assertTrue(line.baselineStart.y <= 260f + 0.01f)
        }
    }
}
