package com.manhwaread.feature.downloads.selfcheck

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterDownloader
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterPipelineCoordinator
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.InMemoryChapterJobStore
import com.manhwaread.core.pipeline.InMemoryOverlayStore
import com.manhwaread.core.pipeline.InMemoryPageStore
import com.manhwaread.core.pipeline.InMemorySegmentStore
import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.PipelineStages
import com.manhwaread.core.pipeline.SegmentTranslator
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.TextSegment
import com.manhwaread.feature.downloads.queue.SegmentPersistingAnalyzer

/**
 * Офлайн-самопроверка конвейера (ФАЗА 15, DoD): полный прогон
 * DOWNLOADING → ANALYZING → TRANSLATING → COMPOSITING → DONE на синтетической
 * главе без сети и без пользовательского провайдера. Загрузчик возвращает
 * локально сгенерированные страницы, анализатор — настоящий (OpenCV/ML Kit),
 * перевод подменяется эхо-переводчиком, типографика — настоящая (fit + buildOverlay).
 * Успех — задача дошла до DONE; счётчики сегментов/оверлеев показывают,
 * что артефакты стадий действительно создаются.
 */
class PipelineSelfCheck(
    private val analyzerFactory: SelfCheckAnalyzerFactory,
    private val compositor: OverlayCompositor,
    private val pageFactory: SyntheticPageFactory,
) : SelfCheckExecutor {
    override suspend fun run(): SelfCheckResult {
        val pageStore = InMemoryPageStore()
        val segmentStore = InMemorySegmentStore()
        val overlayStore = InMemoryOverlayStore()
        val jobStore = InMemoryChapterJobStore()
        val job = ChapterJob(
            id = JOB_ID,
            ref = ChapterRef(sourceId = SELFCHECK_SOURCE_ID, mangaId = 0L, chapterId = SELFCHECK_CHAPTER_ID, chapterUrl = ""),
        )
        jobStore.enqueue(job)
        val pages = pageFactory.createPages()
        pages.forEachIndexed { index, bytes -> pageStore.savePage(SELFCHECK_CHAPTER_ID, index, bytes) }
        val stages = PipelineStages(
            downloader = SyntheticDownloader(pages.size),
            analyzer = SegmentPersistingAnalyzer(analyzerFactory.create(pageStore), segmentStore),
            translator = EchoSegmentTranslator(),
            compositor = compositor,
            overlays = overlayStore,
        )
        val result = ChapterPipelineCoordinator(jobStore, stages).run(JOB_ID)
        val finalJob = jobStore.findById(JOB_ID)
        val status = finalJob?.state?.status ?: StageStatus.FAILED
        return SelfCheckResult(
            success = result.getOrNull() != null && status == StageStatus.DONE,
            status = status,
            segments = segmentStore.loadSegments(SELFCHECK_CHAPTER_ID).size,
            overlays = overlayStore.loadChapter(SELFCHECK_CHAPTER_ID).size,
            error = finalJob?.state?.lastError ?: result.errorOrNull(),
        )
    }

    // Страницы уже лежат в PageStore: загрузчик лишь возвращает ссылки, сеть не нужна.
    private class SyntheticDownloader(private val pageCount: Int) : ChapterDownloader {
        override suspend fun download(job: ChapterJob): DomainResult<List<PageRef>> =
            DomainResult.success((0 until pageCount).map { index -> PageRef(index = index, url = "") })
    }

    // Эхо-перевод: самопроверка идёт офлайн без провайдера пользователя (DoD).
    private class EchoSegmentTranslator : SegmentTranslator {
        override suspend fun translate(segments: List<TextSegment>): DomainResult<List<TranslatedSegment>> =
            DomainResult.success(
                segments.map { segment -> TranslatedSegment(segment.id, ECHO_PREFIX + segment.ocrText) },
            )

        private companion object {
            const val ECHO_PREFIX = "RU: "
        }
    }

    private companion object {
        const val JOB_ID = "selfcheck"
        const val SELFCHECK_SOURCE_ID = -1L
        const val SELFCHECK_CHAPTER_ID = -1L
    }
}
