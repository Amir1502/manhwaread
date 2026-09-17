package com.manhwaread.core.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatcherTest {
    private fun segment(id: String, length: Int) = TranslatableSegment(id, "x".repeat(length))

    @Test
    fun `empty input yields no batches`() {
        assertTrue(TranslationBatcher().batch(emptyList()).isEmpty())
    }

    @Test
    fun `small list stays in single batch`() {
        val segments = listOf(segment("s1", 10), segment("s2", 10))
        val batches = TranslationBatcher().batch(segments)
        assertEquals(1, batches.size)
        assertEquals(segments, batches.single())
    }

    @Test
    fun `exactly max segments stays in single batch`() {
        val segments = (1..3).map { segment("s$it", 5) }
        assertEquals(1, TranslationBatcher(maxSegmentsPerBatch = 3).batch(segments).size)
    }

    @Test
    fun `splits by segment count`() {
        val segments = (1..5).map { segment("s$it", 5) }
        val batches = TranslationBatcher(maxSegmentsPerBatch = 2).batch(segments)
        assertEquals(listOf(2, 2, 1), batches.map { it.size })
    }

    @Test
    fun `splits by char budget`() {
        val segments = listOf(segment("s1", 6), segment("s2", 6), segment("s3", 6))
        val batches = TranslationBatcher(maxSegmentsPerBatch = 10, maxCharsPerBatch = 10).batch(segments)
        assertEquals(listOf(1, 1, 1), batches.map { it.size })
    }

    @Test
    fun `oversized segment travels alone but is not lost`() {
        val segments = listOf(segment("s1", 5), segment("big", 50), segment("s3", 5))
        val batches = TranslationBatcher(maxSegmentsPerBatch = 10, maxCharsPerBatch = 10).batch(segments)
        assertEquals(listOf(1, 1, 1), batches.map { it.size })
        assertEquals("big", batches[1].single().id)
    }

    @Test
    fun `order and content preserved across batches`() {
        val segments = (1..7).map { segment("s$it", 4) }
        val flat = TranslationBatcher(maxSegmentsPerBatch = 2, maxCharsPerBatch = 9).batch(segments).flatten()
        assertEquals(segments, flat)
    }

    @Test
    fun `rejects non-positive segment limit`() {
        assertThrows(IllegalArgumentException::class.java) { TranslationBatcher(maxSegmentsPerBatch = 0) }
    }

    @Test
    fun `rejects non-positive char limit`() {
        assertThrows(IllegalArgumentException::class.java) { TranslationBatcher(maxCharsPerBatch = 0) }
    }
}
