package com.manhwaread.core.pipeline

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment

/** Страница главы, скачанная для анализа. [referer] — для защиты от хотлинка. */
data class PageRef(val index: Int, val url: String, val referer: String? = null)

/** Стадия загрузки: URL страниц (+ байты в PageStore в реализациях). */
interface ChapterDownloader {
    suspend fun download(job: ChapterJob): DomainResult<List<PageRef>>
}

/** Стадия анализа: OCR + детекция баблов → текстовые сегменты. */
interface ChapterAnalyzer {
    suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>>
}

/** Стадия перевода: сегменты оригинала → переведённые сегменты. */
interface SegmentTranslator {
    suspend fun translate(segments: List<TextSegment>): DomainResult<List<TranslatedSegment>>
}

/** Стадия типографики: перевод → векторный оверлей (контур продукта из AGENTS.md). */
interface OverlayCompositor {
    suspend fun composite(
        job: ChapterJob,
        segments: List<TextSegment>,
        translated: List<TranslatedSegment>,
    ): DomainResult<List<OverlaySpec>>
}

/** Набор исполнителей стадий для координатора (объект-параметр против длинного конструктора). */
data class PipelineStages(
    val downloader: ChapterDownloader,
    val analyzer: ChapterAnalyzer,
    val translator: SegmentTranslator,
    val compositor: OverlayCompositor,
    val overlays: OverlayStore,
)
