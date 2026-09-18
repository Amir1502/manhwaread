package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.SegmentStore
import com.manhwaread.core.pipeline.SegmentTranslator
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.TextSegment

// Декоратор стадии TRANSLATING по образцу SegmentPersistingAnalyzer: записывает
// перевод обратно в SegmentStore — TextSegment.translatedText нужен карточке
// бабла и chapter.json. Закреплённая сигнатура translate() не несёт job,
// поэтому chapterId передаётся конструктором при сборке стадий в QueueProcessor.
class SegmentPersistingTranslator(
    private val delegate: SegmentTranslator,
    private val segmentStore: SegmentStore,
    private val chapterId: Long,
) : SegmentTranslator {
    override suspend fun translate(segments: List<TextSegment>): DomainResult<List<TranslatedSegment>> {
        val result = delegate.translate(segments)
        val translated = result.getOrNull() ?: return result
        val textById = translated.associate { item -> item.id to item.text }
        val updated = segments.map { segment ->
            textById[segment.id]?.let { text -> segment.copy(translatedText = text) } ?: segment
        }
        segmentStore.saveSegments(chapterId, updated)
        return result
    }
}
