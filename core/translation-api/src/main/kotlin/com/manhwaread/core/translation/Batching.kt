package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.common.fold
import com.manhwaread.core.vision.DetectedLang

/**
 * Разбивает сегменты на батчи по двум лимитам: число сегментов и суммарный
 * объём символов (защита от превышения контекстного окна LLM).
 * Сегмент длиннее лимита едет в батче один — данные не теряются.
 */
class TranslationBatcher(
    private val maxSegmentsPerBatch: Int = DEFAULT_MAX_SEGMENTS,
    private val maxCharsPerBatch: Int = DEFAULT_MAX_CHARS,
) {
    init {
        require(maxSegmentsPerBatch >= 1) { "maxSegmentsPerBatch must be >= 1" }
        require(maxCharsPerBatch >= 1) { "maxCharsPerBatch must be >= 1" }
    }

    fun batch(segments: List<TranslatableSegment>): List<List<TranslatableSegment>> {
        if (segments.isEmpty()) return emptyList()
        val batches = mutableListOf<List<TranslatableSegment>>()
        var current = mutableListOf<TranslatableSegment>()
        var currentChars = 0
        for (segment in segments) {
            val fits = current.size < maxSegmentsPerBatch && currentChars + segment.text.length <= maxCharsPerBatch
            val overflows = current.isNotEmpty() && !fits
            if (overflows) {
                batches += current
                current = mutableListOf()
                currentChars = 0
            }
            current += segment
            currentChars += segment.text.length
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private companion object {
        const val DEFAULT_MAX_SEGMENTS = 20
        const val DEFAULT_MAX_CHARS = 4_000
    }
}

/**
 * Последовательный перевод батчей через провайдера с валидацией каждого ответа.
 * Partial-ответ даёт совпавшие сегменты (missing помечаются отсутствием в результате);
 * Invalid-ответ или сбой провайдера прерывают весь перевод.
 */
class BatchTranslator(
    private val provider: TranslationProvider,
    private val batcher: TranslationBatcher = TranslationBatcher(),
) {
    suspend fun translateAll(
        segments: List<TranslatableSegment>,
        sourceLang: DetectedLang,
        targetLang: String = TARGET_LANG_RU,
        contextHint: String? = null,
    ): DomainResult<List<TranslatedSegment>> {
        if (segments.isEmpty()) return DomainResult.success(emptyList())
        val translated = mutableListOf<TranslatedSegment>()
        for (batch in batcher.batch(segments)) {
            val result = translateBatch(batch, sourceLang, targetLang, contextHint)
            // K2 не сужает sealed-иерархию после is-проверки — уходим через getOrNull.
            val value = result.getOrNull() ?: return result
            translated += value
        }
        // Порядок результата совпадает с порядком входных сегментов; неполные переводы отбрасываются.
        val byId = translated.associateBy { it.id }
        return DomainResult.success(segments.mapNotNull { byId[it.id] })
    }

    private suspend fun translateBatch(
        batch: List<TranslatableSegment>,
        sourceLang: DetectedLang,
        targetLang: String,
        contextHint: String?,
    ): DomainResult<List<TranslatedSegment>> {
        val response = provider.translate(TranslationRequest(batch, sourceLang, targetLang, contextHint))
        return response.fold(
            onSuccess = { translation -> toSegments(translation, batch) },
            onFailure = { error -> DomainResult.failure(error) },
        )
    }

    // Валидирует сырой ответ батча; Invalid отображается в ProviderBadResponse.
    private fun toSegments(
        translation: TranslationResponse,
        batch: List<TranslatableSegment>,
    ): DomainResult<List<TranslatedSegment>> =
        when (val validation = validate(translation.rawJson, batch.map { it.id })) {
            is ValidationResult.Invalid -> DomainResult.failure(AppError.ProviderBadResponse(validation.reason))
            is ValidationResult.Valid -> DomainResult.success(validation.segments)
            is ValidationResult.Partial -> DomainResult.success(validation.segments)
        }
}
