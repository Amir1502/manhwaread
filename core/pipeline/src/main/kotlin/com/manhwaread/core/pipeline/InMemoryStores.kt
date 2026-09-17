package com.manhwaread.core.pipeline

import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment

// In-memory реализации хранилищ: unit-тесты, превью и офлайн self-check (ФАЗА 15).
// Не потокобезопасны — использовать из одного диспетчера/процесса.

/** Очередь задач в памяти: приоритет по убыванию, внутри приоритета — FIFO по createdAt. */
class InMemoryChapterJobStore : ChapterJobStore {
    private val jobs = mutableMapOf<String, ChapterJob>()

    override suspend fun enqueue(job: ChapterJob) {
        jobs[job.id] = job
    }

    override suspend fun update(id: String, state: JobState) {
        val existing = jobs[id] ?: return
        jobs[id] = existing.copy(state = state)
    }

    override suspend fun findById(id: String): ChapterJob? = jobs[id]

    override suspend fun next(): ChapterJob? = jobs.values
        .filter { it.state.status == StageStatus.QUEUED }
        .sortedWith(compareByDescending<ChapterJob> { it.priority }.thenBy { it.createdAt })
        .firstOrNull()

    override suspend fun all(): List<ChapterJob> = jobs.values.toList()
}

class InMemoryOverlayStore : OverlayStore {
    private val chapters = mutableMapOf<Long, List<OverlaySpec>>()

    override suspend fun saveChapter(chapterId: Long, specs: List<OverlaySpec>) {
        chapters[chapterId] = specs
    }

    override suspend fun loadChapter(chapterId: Long): List<OverlaySpec> = chapters[chapterId].orEmpty()

    override suspend fun clearChapter(chapterId: Long) {
        chapters.remove(chapterId)
    }
}

class InMemorySegmentStore : SegmentStore {
    private val chapters = mutableMapOf<Long, List<TextSegment>>()

    override suspend fun saveSegments(chapterId: Long, segments: List<TextSegment>) {
        chapters[chapterId] = segments
    }

    override suspend fun loadSegments(chapterId: Long): List<TextSegment> = chapters[chapterId].orEmpty()
}

class InMemoryPageStore : PageStore {
    private val pages = mutableMapOf<Pair<Long, Int>, ByteArray>()

    override suspend fun savePage(chapterId: Long, pageIndex: Int, bytes: ByteArray) {
        pages[chapterId to pageIndex] = bytes
    }

    override suspend fun loadPage(chapterId: Long, pageIndex: Int): ByteArray? = pages[chapterId to pageIndex]
}
