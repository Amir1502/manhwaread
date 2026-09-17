package com.manhwaread.core.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextFitterTest {
    private val measurer = ApproximateTextMeasurer()
    private val mask200 = RectMask(0, RectF(0f, 0f, 200f, 200f))

    // Конфиг без деградаций (кроме переноса): детерминированные размеры.
    private val plainConfig = FitConfig(
        minSizePx = 8f,
        maxSizePx = 64f,
        stepPx = 1f,
        paddingPx = 0f,
        lineSpacingStart = 1f,
        lineSpacingMin = 1f,
        letterSpacingMin = 0f,
        scaleXMin = 1f,
        maxCharsPerLine = 40,
    )

    private fun widthOf(result: FitResult, line: String): Float =
        measurer.measureLine(line, result.sizePx, result.letterSpacing, result.scaleX)

    @Test
    fun `empty text yields empty result`() {
        val result = fit("", mask200, measurer)
        assertTrue(result.lines.isEmpty())
        assertFalse(result.overflow)
        assertTrue(result.degradations.isEmpty())
        assertEquals(14f, result.sizePx)
    }

    @Test
    fun `short text fits at max size without degradations`() {
        val result = fit("Hi", mask200, measurer)
        assertEquals(listOf("Hi"), result.lines)
        assertEquals(128f, result.sizePx)
        assertFalse(result.overflow)
        assertTrue(result.degradations.isEmpty())
    }

    @Test
    fun `words join greedily while width allows`() {
        val result = fit("aa bb cc", mask200, measurer, plainConfig)
        assertEquals(listOf("aa bb", "cc"), result.lines)
        assertEquals(64f, result.sizePx)
        assertFalse(result.overflow)
        assertTrue(result.degradations.isEmpty())
    }

    @Test
    fun `longer text shrinks size and preserves all words`() {
        val words = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
        val result = fit(words.joinToString(" "), mask200, measurer)
        assertFalse(result.overflow)
        assertTrue(result.sizePx < 128f)
        assertTrue(result.sizePx >= 14f)
        assertTrue(result.lines.size >= 2)
        assertTrue(result.lines.all { it.length <= 40 })
        assertEquals(words, result.lines.joinToString(" ").split(" "))
    }

    @Test
    fun `single long word triggers syllable wrap`() {
        val word = "a".repeat(30)
        val result = fit(word, mask200, measurer, plainConfig)
        assertFalse(result.overflow)
        assertTrue(result.degradations.contains(Degradation.SYLLABLE_WRAP))
        assertEquals(word, result.lines.joinToString(""))
        assertTrue(result.lines.size >= 2)
        assertTrue(result.lines.all { widthOf(result, it) <= 200f })
    }

    @Test
    fun `height pressure applies line spacing degradation first`() {
        val result = fit("aaaa bbbb", mask200, measurer)
        assertEquals(listOf("aaaa", "bbbb"), result.lines)
        assertTrue(result.sizePx in 85f..88f)
        assertEquals(setOf(Degradation.LINE_SPACING), result.degradations)
        assertEquals(0.9f, result.lineSpacingMult)
        assertEquals(1f, result.scaleX)
    }

    @Test
    fun `width pressure adds letter spacing degradation`() {
        val mask = RectMask(0, RectF(0f, 0f, 200f, 58f))
        val result = fit("aaaa bbbb", mask, measurer)
        assertEquals(listOf("aaaa bbbb"), result.lines)
        assertTrue(result.sizePx in 41f..43f)
        assertTrue(result.degradations.contains(Degradation.LETTER_SPACING))
        assertFalse(result.degradations.contains(Degradation.SCALE_X))
        assertEquals(-0.02f, result.letterSpacing)
    }

    @Test
    fun `tight mask reaches scale x degradation`() {
        val mask = RectMask(0, RectF(0f, 0f, 170f, 60f))
        val result = fit("aaaa bbbb", mask, measurer)
        assertEquals(listOf("aaaa bbbb"), result.lines)
        assertTrue(result.sizePx in 43f..45f)
        assertTrue(result.degradations.contains(Degradation.LINE_SPACING))
        assertTrue(result.degradations.contains(Degradation.LETTER_SPACING))
        assertTrue(result.degradations.contains(Degradation.SCALE_X))
        assertEquals(0.75f, result.scaleX)
    }

    @Test
    fun `impossible text is clipped with overflow flag`() {
        val mask = RectMask(0, RectF(0f, 0f, 40f, 20f))
        val result = fit("много длинных слов которые не влезут никогда", mask, measurer)
        assertTrue(result.overflow)
        assertTrue(result.degradations.contains(Degradation.CLIPPED))
        assertEquals(14f, result.sizePx)
        assertEquals(1, result.lines.size)
    }

    @Test
    fun `max chars per line is respected`() {
        val config = plainConfig.copy(maxCharsPerLine = 5)
        val result = fit("aaa bbb ccc ddd", mask200, measurer, config)
        assertEquals(listOf("aaa", "bbb", "ccc", "ddd"), result.lines)
        assertTrue(result.lines.all { it.length <= 5 })
        assertFalse(result.overflow)
    }

    @Test
    fun `ellipse mask narrows top and bottom lines`() {
        val mask = EllipseMask(0, RectF(0f, 0f, 200f, 200f))
        val result = fit("мир труд май люди земля солнце небо ветер", mask, measurer, plainConfig)
        assertFalse(result.overflow)
        assertTrue(result.lines.size >= 2)
        val lineH = measurer.lineHeight(result.sizePx, result.lineSpacingMult)
        result.lines.forEachIndexed { index, line ->
            val centerY = lineH * (index + 0.5f)
            assertTrue(widthOf(result, line) <= mask.widthAt(centerY) + 0.001f)
        }
    }

    @Test
    fun `padding shrinks available width`() {
        val config = plainConfig.copy(paddingPx = 10f)
        val result = fit("широкоформатноеслово", mask200, measurer, config)
        assertFalse(result.overflow)
        assertTrue(result.lines.all { widthOf(result, it) <= 180f + 0.001f })
    }

    @Test
    fun `fit is deterministic`() {
        val text = "детерминированная раскладка текста в бабл"
        val first = fit(text, mask200, measurer)
        val second = fit(text, mask200, measurer)
        assertEquals(first, second)
    }

    @Test
    fun `line breaks tokenize as whitespace`() {
        val config = plainConfig.copy(minSizePx = 8f, maxSizePx = 8f)
        val result = fit("aaa\nbbb", mask200, measurer, config)
        assertEquals(listOf("aaa bbb"), result.lines)
    }

    @Test
    fun `degenerate zero mask clips without crash`() {
        val mask = RectMask(0, RectF(0f, 0f, 0f, 0f))
        val result = fit("текст", mask, measurer)
        assertTrue(result.overflow)
        assertEquals(1, result.lines.size)
        assertEquals(14f, result.sizePx)
    }
}
