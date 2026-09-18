package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.InMemorySegmentStore
import com.manhwaread.core.pipeline.SegmentTranslator
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SegmentPersistingTranslatorTest {
    private class FakeDelegate(
        val outcome: DomainResult<List<TranslatedSegment>>,
    ) : SegmentTranslator {
        var calls = 0
            private set

        override suspend fun translate(segments: List<TextSegment>): DomainResult<List<TranslatedSegment>> {
            calls++
            return outcome
        }
    }

    private val segments = listOf(segment("s1", "안녕"), segment("s2", "TODAVÍA"))

    private fun segment(id: String, text: String) = TextSegment(
        id = id,
        bubbleId = "b1",
        pageIndex = 0,
        ocrText = text,
        ocrLang = DetectedLang.KO,
        ocrConfidence = 1f,
        readingOrder = 0,
    )

    @Test
    fun `success persists translated text back to store`() = runTest {
        val store = InMemorySegmentStore()
        val translator = SegmentPersistingTranslator(
            FakeDelegate(DomainResult.success(listOf(TranslatedSegment("s1", "Привет")))),
            store,
            CHAPTER_ID,
        )

        val result = translator.translate(segments)

        assertEquals(listOf(TranslatedSegment("s1", "Привет")), result.getOrNull())
        val saved = store.loadSegments(CHAPTER_ID)
        assertEquals(2, saved.size)
        assertEquals("Привет", saved.first { item -> item.id == "s1" }.translatedText)
        // Сегмент без перевода сохраняется как есть: стор держит полный набор.
        assertNull(saved.first { item -> item.id == "s2" }.translatedText)
    }

    @Test
    fun `failure passes through without saving`() = runTest {
        val store = InMemorySegmentStore()
        val translator = SegmentPersistingTranslator(
            FakeDelegate(DomainResult.failure(AppError.ProviderQuota)),
            store,
            CHAPTER_ID,
        )

        val result = translator.translate(segments)

        assertEquals(AppError.ProviderQuota, result.errorOrNull())
        assertEquals(0, store.loadSegments(CHAPTER_ID).size)
    }

    private companion object {
        const val CHAPTER_ID = 10L
    }
}
