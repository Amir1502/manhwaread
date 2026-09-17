package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.RectF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OcrMapperTest {
    @Test
    fun `recognized language maps by prefix`() {
        assertEquals(DetectedLang.JA, mapRecognizedLanguage("ja"))
        assertEquals(DetectedLang.KO, mapRecognizedLanguage("ko"))
        assertEquals(DetectedLang.ZH, mapRecognizedLanguage("zh-Hans"))
        assertEquals(DetectedLang.EN, mapRecognizedLanguage("en"))
        assertEquals(DetectedLang.JA, mapRecognizedLanguage("JA"))
        assertEquals(DetectedLang.UNKNOWN, mapRecognizedLanguage(null))
        assertEquals(DetectedLang.UNKNOWN, mapRecognizedLanguage("xx"))
    }

    @Test
    fun `element maps with trimmed text and normalized bounds`() {
        val mapped = mapOcrElement("  hello  ", 1, 2, 3, 4, 0.9f, DetectedLang.EN)
        assertEquals("hello", mapped?.text)
        assertEquals(RectF(1f, 2f, 3f, 4f), mapped?.bounds)
        assertEquals(0.9f, mapped?.confidence)
        assertEquals(DetectedLang.EN, mapped?.lang)
    }

    @Test
    fun `blank or null text maps to null`() {
        assertNull(mapOcrElement("   ", 1, 2, 3, 4, 0.9f, DetectedLang.EN))
        assertNull(mapOcrElement(null, 1, 2, 3, 4, 0.9f, DetectedLang.EN))
    }

    @Test
    fun `missing confidence falls back to default`() {
        val mapped = mapOcrElement("x", 0, 0, 10, 10, null, DetectedLang.KO)
        assertEquals(DEFAULT_OCR_CONFIDENCE, mapped?.confidence)
    }

    @Test
    fun `confidence is clamped to unit range`() {
        assertEquals(1f, mapOcrElement("x", 0, 0, 10, 10, 1.7f, DetectedLang.KO)?.confidence)
        assertEquals(0f, mapOcrElement("x", 0, 0, 10, 10, -0.5f, DetectedLang.KO)?.confidence)
    }

    @Test
    fun `inverted bounds are normalized`() {
        val mapped = mapOcrElement("x", 10, 20, 2, 4, 0.5f, DetectedLang.KO)
        assertEquals(RectF(2f, 4f, 10f, 20f), mapped?.bounds)
    }

    @Test
    fun `iou of identical rects is one and disjoint is zero`() {
        val rect = RectF(0f, 0f, 10f, 10f)
        assertEquals(1f, iou(rect, rect), EPS)
        assertEquals(0f, iou(rect, RectF(50f, 50f, 60f, 60f)), EPS)
    }

    @Test
    fun `iou of half-overlapping rects`() {
        // Пересечение 50, объединение 150 → 1/3.
        val value = iou(RectF(0f, 0f, 10f, 10f), RectF(5f, 0f, 15f, 10f))
        assertEquals(1f / 3f, value, EPS)
    }

    @Test
    fun `dedupe keeps higher confidence duplicate`() {
        val weak = OcrLine("a", RectF(0f, 0f, 10f, 10f), 0.4f, DetectedLang.JA)
        val strong = OcrLine("a", RectF(0f, 0f, 10f, 10f), 0.95f, DetectedLang.JA)
        val kept = dedupeOcrLines(listOf(weak, strong))
        assertEquals(1, kept.size)
        assertEquals(0.95f, kept[0].confidence)
    }

    @Test
    fun `dedupe keeps disjoint lines`() {
        val first = OcrLine("a", RectF(0f, 0f, 10f, 10f), 0.9f, DetectedLang.JA)
        val second = OcrLine("b", RectF(100f, 100f, 110f, 110f), 0.8f, DetectedLang.KO)
        val kept = dedupeOcrLines(listOf(first, second))
        assertEquals(2, kept.size)
    }

    @Test
    fun `iou with degenerate rect is zero`() {
        assertEquals(0f, iou(RectF(5f, 5f, 5f, 5f), RectF(0f, 0f, 10f, 10f)), EPS)
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
