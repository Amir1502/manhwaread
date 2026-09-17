package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.core.vision.TextSegment

// Реализация стадии ANALYZING конвейера (ФАЗА 7): страницы главы →
// детекция баблов → OCR → порядок чтения → привязка строк к баблам →
// TextSegment-ы. Строки вне баблов не вписываются (контур продукта:
// перевод живёт только в облачках реплик).
class VisionChapterAnalyzer(
    private val pageStore: PageStore,
    private val bitmapDecoder: PageBitmapDecoder,
    private val detector: BubbleDetector,
    private val ocr: OcrEngine,
    private val direction: ReadingDirection = ReadingDirection.VERTICAL_WEBTOON,
) : ChapterAnalyzer {
    override suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>> {
        val segments = mutableListOf<TextSegment>()
        for (page in pages) {
            val outcome = runCatching { analyzePage(job, page, segments) }
            val error = outcome.exceptionOrNull()
            if (error != null) {
                return DomainResult.Failure(mapVisionError(error))
            }
        }
        return DomainResult.Success(segments)
    }

    private suspend fun analyzePage(job: ChapterJob, page: PageRef, out: MutableList<TextSegment>) {
        val bytes = pageStore.loadPage(job.ref.chapterId, page.index)
            ?: throw VisionException("page ${page.index}: downloaded bytes not found")
        val bitmap = bitmapDecoder.decode(bytes)
            ?: throw VisionException("page ${page.index}: image decode failed")
        try {
            val bubbles = detector.detect(bitmap, page.index)
            val lines = assignReadingOrder(ocr.recognize(bitmap, page.index), bitmap.width, direction)
            for (assignment in assignLinesToBubbles(lines, bubbles)) {
                val line = assignment.first
                val bubble = assignment.second ?: continue
                out += TextSegment(
                    id = "${job.id}:p${page.index}:l${line.readingOrder}",
                    bubbleId = bubble.id,
                    pageIndex = page.index,
                    ocrText = line.text,
                    ocrLang = line.lang,
                    ocrConfidence = line.confidence,
                    // Глобальный сквозной порядок по главе — для очереди перевода.
                    readingOrder = out.size,
                    isSfx = isSfx(line.text),
                )
            }
        } finally {
            bitmap.recycle()
        }
    }
}

// Отображение ошибок стадии анализа: сбой движка — VisionFailed,
// прочее — Unknown с причиной.
fun mapVisionError(error: Throwable): AppError =
    when (error) {
        is VisionException -> AppError.VisionFailed
        else -> AppError.Unknown(error)
    }
