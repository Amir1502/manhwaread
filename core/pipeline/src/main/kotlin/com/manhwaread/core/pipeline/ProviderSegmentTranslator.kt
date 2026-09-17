package com.manhwaread.core.pipeline

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.translation.BatchTranslator
import com.manhwaread.core.translation.TranslatableSegment
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.translation.TranslationBatcher
import com.manhwaread.core.translation.TranslationProvider
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.TextSegment

// Стадия TRANSLATING: TextSegment-ы главы → батчи провайдера → переводы.
// Сегменты с пользовательской правкой (isEditedByUser) не отправляются
// провайдеру — их текст возвращается как есть (ручная правка переживает
// повторный перевод, DoD).
class ProviderSegmentTranslator(
    private val provider: TranslationProvider,
    private val batcher: TranslationBatcher = TranslationBatcher(),
) : SegmentTranslator {
    override suspend fun translate(segments: List<TextSegment>): DomainResult<List<TranslatedSegment>> {
        val edited = segments.filter { segment -> segment.isEditedByUser }
        val pending = segments.filterNot { segment -> segment.isEditedByUser }
        val editedResults = edited.map { segment ->
            TranslatedSegment(segment.id, segment.translatedText ?: segment.ocrText)
        }
        if (pending.isEmpty()) {
            return DomainResult.success(editedResults)
        }
        val requestSegments = pending.map { segment ->
            TranslatableSegment(segment.id, segment.ocrText, segment.isSfx)
        }
        val translated = BatchTranslator(provider, batcher)
            .translateAll(requestSegments, dominantLang(pending))
        // K2 не сужает sealed-иерархию после is-проверки — уходим через getOrNull.
        val value = translated.getOrNull() ?: return translated
        return DomainResult.success(value + editedResults)
    }

    // Язык батча — доминирующий среди сегментов (страницы главы однородны).
    private fun dominantLang(segments: List<TextSegment>): DetectedLang =
        segments.groupingBy { segment -> segment.ocrLang }
            .eachCount()
            .maxByOrNull { entry -> entry.value }
            ?.key
            ?: DetectedLang.UNKNOWN
}
