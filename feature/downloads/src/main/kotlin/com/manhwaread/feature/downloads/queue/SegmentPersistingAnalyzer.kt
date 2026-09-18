package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.SegmentStore
import com.manhwaread.core.vision.TextSegment

// Декоратор стадии ANALYZING (ФАЗА 15): координатор — закреплённый контракт
// и сегменты не персистит, а chapter.json и ручные правки требуют сегменты
// в хранилище. Сохраняем их после успешного анализа, не меняя контракт.
class SegmentPersistingAnalyzer(
    private val delegate: ChapterAnalyzer,
    private val segmentStore: SegmentStore,
) : ChapterAnalyzer {
    override suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>> {
        val result = delegate.analyze(job, pages)
        val segments = result.getOrNull() ?: return result
        segmentStore.saveSegments(job.ref.chapterId, segments)
        return result
    }
}
