package com.manhwaread.core.pipeline

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.translation.TranslationProvider
import com.manhwaread.core.translation.TranslationRequest
import com.manhwaread.core.translation.TranslationResponse
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderSegmentTranslatorTest {
    private class FakeProvider(
        private val responseFor: (TranslationRequest) -> DomainResult<TranslationResponse>,
    ) : TranslationProvider {
        val requests = mutableListOf<TranslationRequest>()
        override val id: String = "fake"
        override val displayName: String = "Fake"

        override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = true

        override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> {
            requests += request
            return responseFor(request)
        }
    }

    // Ответ в формате validate(): [{"id","text"}] с префиксом ru:.
    private fun successFor(request: TranslationRequest) = DomainResult.success(
        TranslationResponse(
            request.segments.joinToString(separator = ",", prefix = "[", postfix = "]") { segment ->
                """{"id":"${segment.id}","text":"ru:${segment.text}"}"""
            },
        ),
    )

    private fun segment(
        id: String,
        lang: DetectedLang = DetectedLang.KO,
        edited: Boolean = false,
        translated: String? = null,
    ) = TextSegment(
        id = id,
        bubbleId = "b$id",
        pageIndex = 0,
        ocrText = "text-$id",
        ocrLang = lang,
        ocrConfidence = 0.9f,
        readingOrder = 0,
        isEditedByUser = edited,
        translatedText = translated,
    )

    @Test
    fun `translates all segments through provider`() = runTest {
        val provider = FakeProvider(::successFor)
        val translator = ProviderSegmentTranslator(provider)
        val result = translator.translate(listOf(segment("s1"), segment("s2")))
        val value = result.getOrNull()
        assertEquals(listOf("s1", "s2"), value?.map { it.id })
        assertEquals("ru:text-s1", value?.first()?.text)
    }

    @Test
    fun `edited segments bypass provider`() = runTest {
        val provider = FakeProvider(::successFor)
        val translator = ProviderSegmentTranslator(provider)
        val segments = listOf(
            segment("s1"),
            segment("s2", edited = true, translated = "ручная правка"),
        )
        val result = translator.translate(segments)
        val value = result.getOrNull()
        assertEquals(setOf("s1", "s2"), value?.map { it.id }?.toSet())
        assertEquals("ручная правка", value?.first { it.id == "s2" }?.text)
        // Провайдер видит только неотредактированный сегмент.
        assertEquals(listOf("s1"), provider.requests.single().segments.map { it.id })
    }

    @Test
    fun `all edited means no provider call`() = runTest {
        val provider = FakeProvider(::successFor)
        val translator = ProviderSegmentTranslator(provider)
        val result = translator.translate(
            listOf(segment("s1", edited = true, translated = "a"), segment("s2", edited = true)),
        )
        val value = result.getOrNull()
        assertTrue(provider.requests.isEmpty())
        // Без сохранённого перевода edited-сегмент возвращает OCR-текст.
        assertEquals("a", value?.first { it.id == "s1" }?.text)
        assertEquals("text-s2", value?.first { it.id == "s2" }?.text)
    }

    @Test
    fun `dominant language wins batch language`() = runTest {
        val provider = FakeProvider(::successFor)
        val translator = ProviderSegmentTranslator(provider)
        translator.translate(
            listOf(
                segment("s1", lang = DetectedLang.KO),
                segment("s2", lang = DetectedLang.KO),
                segment("s3", lang = DetectedLang.JA),
            ),
        )
        assertEquals(DetectedLang.KO, provider.requests.single().sourceLang)
    }

    @Test
    fun `provider failure propagates`() = runTest {
        val provider = FakeProvider { DomainResult.failure(AppError.ProviderAuth) }
        val translator = ProviderSegmentTranslator(provider)
        val result = translator.translate(listOf(segment("s1")))
        assertTrue(result is DomainResult.Failure)
        assertEquals(AppError.ProviderAuth, (result as DomainResult.Failure).error)
    }

    @Test
    fun `empty input succeeds without provider call`() = runTest {
        val provider = FakeProvider(::successFor)
        val translator = ProviderSegmentTranslator(provider)
        val result = translator.translate(emptyList())
        assertEquals(emptyList<TranslatedSegment>(), result.getOrNull())
        assertTrue(provider.requests.isEmpty())
    }
}
