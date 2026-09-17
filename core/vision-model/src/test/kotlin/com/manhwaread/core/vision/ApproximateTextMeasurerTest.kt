package com.manhwaread.core.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApproximateTextMeasurerTest {
    private val measurer = ApproximateTextMeasurer()

    @Test
    fun `empty line measures zero`() {
        assertEquals(0f, measurer.measureLine("", 10f, 0f, 1f))
    }

    @Test
    fun `width scales with length and size`() {
        assertEquals(20f, measurer.measureLine("abcd", 10f, 0f, 1f), 0.001f)
        assertEquals(40f, measurer.measureLine("abcd", 20f, 0f, 1f), 0.001f)
        assertEquals(10f, measurer.measureLine("ab", 10f, 0f, 1f), 0.001f)
    }

    @Test
    fun `letterSpacing adds per gap`() {
        assertEquals(23f, measurer.measureLine("abcd", 10f, 0.1f, 1f), 0.001f)
    }

    @Test
    fun `scaleX compresses width`() {
        assertEquals(10f, measurer.measureLine("abcd", 10f, 0f, 0.5f), 0.001f)
    }

    @Test
    fun `lineHeight multiplies spacing`() {
        assertEquals(12f, measurer.lineHeight(10f, 1f), 0.001f)
        assertEquals(10.8f, measurer.lineHeight(10f, 0.9f), 0.001f)
    }
}
