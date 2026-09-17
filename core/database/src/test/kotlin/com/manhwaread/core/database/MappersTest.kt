package com.manhwaread.core.database

import com.manhwaread.core.common.AppError
import com.manhwaread.core.model.Bookmark
import com.manhwaread.core.model.Category
import com.manhwaread.core.model.Chapter
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.model.DownloadTask
import com.manhwaread.core.model.HistoryEntry
import com.manhwaread.core.model.Manga
import com.manhwaread.core.model.ReadingProgress
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.JobState
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.TextSegment
import com.manhwaread.source.api.MangaStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class MappersTest {
    @Test
    fun `manga roundtrip`() {
        val manga = Manga(
            id = 5L,
            sourceId = 1L,
            url = "/manga/solo",
            title = "Solo Leveling",
            artist = "DUBU",
            author = "Chugong",
            description = "Охотник ранга E",
            genres = listOf("action", "fantasy"),
            status = MangaStatus.ONGOING,
            thumbnailUrl = "https://cdn/c.jpg",
            nsfw = false,
            inLibrary = true,
            addedAtMs = 42L,
        )
        assertEquals(manga, manga.toEntity().toDomain())
    }

    @Test
    fun `chapter roundtrip`() {
        val chapter = Chapter(
            id = 3L,
            mangaId = 5L,
            url = "/ch/1",
            name = "Chapter 1",
            season = 2,
            chapterNumber = 10.5f,
            dateUploadMs = 77L,
            scanlator = "Asura",
            read = true,
        )
        assertEquals(chapter, chapter.toEntity().toDomain())
    }

    @Test
    fun `category roundtrip maps order to sortOrder`() {
        val category = Category(id = 2L, name = "Читаю", order = 7, isSystem = true)
        val entity = category.toEntity()
        assertEquals(7, entity.sortOrder)
        assertEquals(category, entity.toDomain())
    }

    @Test
    fun `history roundtrip with value classes`() {
        val entry = HistoryEntry(mangaId = 1L, chapterId = 2L, progress = ReadingProgress.of(7, 13), lastReadMs = 99L)
        val entity = entry.toEntity()
        assertEquals(7, entity.pageIndex)
        assertEquals(13, entity.scrollOffsetPx)
        assertEquals(entry, entity.toDomain())
    }

    @Test
    fun `bookmark roundtrip`() {
        val bookmark = Bookmark(id = 4L, mangaId = 1L, chapterId = 2L, pageIndex = 9, createdAtMs = 55L, note = "важно")
        assertEquals(bookmark, bookmark.toEntity().toDomain())
    }

    @Test
    fun `download task roundtrip`() {
        val task = DownloadTask(id = 6L, mangaId = 1L, chapterId = 2L, status = DownloadStatus.RUNNING, progress = 0.5f, enqueuedAtMs = 3L)
        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun `job roundtrip without error`() {
        val job = ChapterJob(
            id = "j1",
            ref = ChapterRef(sourceId = 1L, mangaId = 2L, chapterId = 3L, chapterUrl = "/ch/3"),
            priority = 7,
            createdAt = 123L,
            state = JobState(status = StageStatus.TRANSLATING, attempts = 2, lastError = null),
        )
        assertNull(job.toEntity().lastError)
        assertEquals(job, job.toEntity().toDomain())
    }

    @Test
    fun `job error roundtrip preserves description`() {
        val error = AppError.Network(IOException("net down"))
        val job = ChapterJob(
            id = "j2",
            ref = ChapterRef(1L, 2L, 3L, "/ch/3"),
            state = JobState(status = StageStatus.FAILED, attempts = 1, lastError = error),
        )
        val restored = job.toEntity().toDomain()
        assertTrue(restored.state.lastError is AppError.Unknown)
        assertTrue(restored.state.lastError.toString().contains("net down"))
    }

    @Test
    fun `segment roundtrip`() {
        val segment = TextSegment(
            id = "s1",
            bubbleId = "b1",
            pageIndex = 2,
            ocrText = "안녕",
            ocrLang = DetectedLang.KO,
            ocrConfidence = 0.9f,
            readingOrder = 1,
            isSfx = true,
            translatedText = "привет",
            isEditedByUser = true,
            needsRetry = false,
        )
        assertEquals(segment, segment.toEntity(chapterId = 100L).toDomain())
    }
}
