package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

// Записывающий фейк провайдера: копит запросы, отвечает через handler.
private class RecordingProvider(
    private val handler: (TranslationRequest) -> DomainResult<TranslationResponse>,
) : TranslationProvider {
    override val id: String = "fake"
    override val displayName: String = "Fake Provider"
    val requests = mutableListOf<TranslationRequest>()

    override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = true

    override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> {
        requests += request
        return handler(request)
    }
}

// Эхо-ответ: валидный JSON с переводами всех id батча; [skip] исключает id (имитация Partial).
private fun echoResponse(request: TranslationRequest, skip: Set<String> = emptySet()): TranslationResponse {
    val items = request.segments
        .filterNot { it.id in skip }
        .joinToString(",") { """{"id":"${it.id}","text":"перевод ${it.id}"}""" }
    return TranslationResponse("[$items]")
}

private fun segments(vararg ids: String) = ids.map { TranslatableSegment(it, "текст $it") }

class BatchTranslatorTest {
    @Test
    fun `empty input short-circuits without provider call`() {
        val provider = RecordingProvider { error("provider must not be called") }
        val translator = BatchTranslator(provider)
        val result = runBlocking { translator.translateAll(emptyList(), DetectedLang.KO) }
        assertTrue(result is DomainResult.Success)
        assertTrue((result as DomainResult.Success).value.isEmpty())
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun `single batch success returns translated segments`() {
        val provider = RecordingProvider { DomainResult.success(echoResponse(it)) }
        val translator = BatchTranslator(provider)
        val result = runBlocking { translator.translateAll(segments("s1", "s2", "s3"), DetectedLang.KO) }
        val value = (result as DomainResult.Success).value
        assertEquals(listOf("s1", "s2", "s3"), value.map { it.id })
        assertEquals("перевод s1", value[0].text)
    }

    @Test
    fun `multiple batches aggregate in input order`() {
        val provider = RecordingProvider { DomainResult.success(echoResponse(it)) }
        val translator = BatchTranslator(provider, TranslationBatcher(maxSegmentsPerBatch = 2))
        val result = runBlocking { translator.translateAll(segments("s1", "s2", "s3", "s4", "s5"), DetectedLang.JA) }
        val value = (result as DomainResult.Success).value
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), value.map { it.id })
        assertEquals(3, provider.requests.size)
    }

    @Test
    fun `provider failure propagates`() {
        val provider = RecordingProvider { DomainResult.failure(AppError.Network(IOException("boom"))) }
        val translator = BatchTranslator(provider)
        val result = runBlocking { translator.translateAll(segments("s1"), DetectedLang.KO) }
        val failure = result as DomainResult.Failure
        assertTrue(failure.error is AppError.Network)
    }

    @Test
    fun `invalid response fails with ProviderBadResponse`() {
        val provider = RecordingProvider { DomainResult.success(TranslationResponse("мусор вместо json")) }
        val translator = BatchTranslator(provider)
        val result = runBlocking { translator.translateAll(segments("s1"), DetectedLang.KO) }
        val failure = result as DomainResult.Failure
        assertTrue(failure.error is AppError.ProviderBadResponse)
    }

    @Test
    fun `partial response keeps matched segments only`() {
        val provider = RecordingProvider { DomainResult.success(echoResponse(it, skip = setOf("s2"))) }
        val translator = BatchTranslator(provider)
        val result = runBlocking { translator.translateAll(segments("s1", "s2", "s3"), DetectedLang.KO) }
        val value = (result as DomainResult.Success).value
        assertEquals(listOf("s1", "s3"), value.map { it.id })
    }

    @Test
    fun `batch sizes follow batcher limits`() {
        val provider = RecordingProvider { DomainResult.success(echoResponse(it)) }
        val translator = BatchTranslator(provider, TranslationBatcher(maxSegmentsPerBatch = 2))
        runBlocking { translator.translateAll(segments("s1", "s2", "s3", "s4", "s5"), DetectedLang.KO) }
        assertEquals(listOf(2, 2, 1), provider.requests.map { it.segments.size })
    }

    @Test
    fun `request carries languages and context`() {
        val provider = RecordingProvider { DomainResult.success(echoResponse(it)) }
        val translator = BatchTranslator(provider)
        runBlocking {
            translator.translateAll(segments("s1"), DetectedLang.KO, contextHint = "Глава 1: восхождение")
        }
        val sent = provider.requests.single()
        assertEquals(DetectedLang.KO, sent.sourceLang)
        assertEquals(TARGET_LANG_RU, sent.targetLang)
        assertEquals("Глава 1: восхождение", sent.contextHint)
    }
}
