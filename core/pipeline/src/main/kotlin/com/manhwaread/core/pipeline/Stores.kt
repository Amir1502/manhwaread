package com.manhwaread.core.pipeline

import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment

/** Хранилище задач конвейера; персистентная реализация — Room (ФАЗА 8). */
interface ChapterJobStore {
    suspend fun enqueue(job: ChapterJob)
    suspend fun update(id: String, state: JobState)
    suspend fun findById(id: String): ChapterJob?

    /** Следующая QUEUED-задача: максимальный приоритет, затем FIFO по createdAt. */
    suspend fun next(): ChapterJob?

    suspend fun all(): List<ChapterJob>
}

/** Хранилище векторных оверлеев главы (перевод НЕ запекается в растр). */
interface OverlayStore {
    suspend fun saveChapter(chapterId: Long, specs: List<OverlaySpec>)
    suspend fun loadChapter(chapterId: Long): List<OverlaySpec>
    suspend fun clearChapter(chapterId: Long)
}

/** Хранилище текстовых сегментов (OCR-результатов) главы. */
interface SegmentStore {
    suspend fun saveSegments(chapterId: Long, segments: List<TextSegment>)
    suspend fun loadSegments(chapterId: Long): List<TextSegment>
}

/** Хранилище растров страниц главы (кэш для офлайн-чтения). */
interface PageStore {
    suspend fun savePage(chapterId: Long, pageIndex: Int, bytes: ByteArray)
    suspend fun loadPage(chapterId: Long, pageIndex: Int): ByteArray?
}
