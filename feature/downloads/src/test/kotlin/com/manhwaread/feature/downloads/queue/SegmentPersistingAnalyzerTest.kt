package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.InMemorySegmentStore
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SegmentPersistingAnalyzerTest {
    private class FakeDelegate(
        val outcome: DomainResult<List<TextSegment>>,
    ) : ChapterAnalyzer {
        var calls = 0
            private set

        override suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>> {
            calls++
            return outcome
        }
    }

    private val job = ChapterJob(
        id = "job-1",
        ref = ChapterRef(sourceId = 7L, mangaId = 1L, chapterId = 10L, chapterUrl = "/ch/10"),
    )

    private val segments = listOf(
        TextSegment(
            id = "s1",
            bubbleId = "b1",
            pageIndex = 0,
            ocrText = "안녕",
            ocrLang = DetectedLang.KO,
            ocrConfidence = 1f,
            readingOrder = 0,
        ),
    )

    @Test
    fun `success persists segments to store`() = runTest {
        val store = InMemorySegmentStore()
        val analyzer = SegmentPersistingAnalyzer(FakeDelegate(DomainResult.success(segments)), store)

        val result = analyzer.analyze(job, emptyList())

        assertEquals(segments, result.getOrNull())
        assertEquals(segments, store.loadSegments(10L))
    }

    @Test
    fun `failure passes through without saving`() = runTest {
        val store = InMemorySegmentStore()
        val analyzer = SegmentPersistingAnalyzer(
            FakeDelegate(DomainResult.failure(AppError.OcrFailed)),
            store,
        )

        val result = analyzer.analyze(job, emptyList())

        assertEquals(AppError.OcrFailed, result.errorOrNull())
        assertTrue(store.loadSegments(10L).isEmpty())
    }
}
