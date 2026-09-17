package com.manhwaread.core.database

import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.JobState
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.source.api.MangaStatus

// Общие фикстуры тестов DAO.
internal object DatabaseFixtures {
    fun manga(
        sourceId: Long = 1L,
        url: String = "/manga/solo-leveling",
        title: String = "Solo Leveling",
        inLibrary: Boolean = true,
        addedAtMs: Long = 0L,
        genres: List<String> = listOf("action", "fantasy"),
        status: MangaStatus = MangaStatus.ONGOING,
    ) = MangaEntity(
        sourceId = sourceId,
        url = url,
        title = title,
        artist = "DUBU",
        author = "Chugong",
        description = "Охотник ранга E становится сильнейшим",
        genres = genres,
        status = status,
        thumbnailUrl = "https://cdn.example/cover.jpg",
        nsfw = false,
        inLibrary = inLibrary,
        addedAtMs = addedAtMs,
    )

    fun chapter(
        mangaId: Long,
        url: String = "/ch/1",
        name: String = "Chapter 1",
        season: Int = 1,
        chapterNumber: Float = 1f,
        read: Boolean = false,
    ) = ChapterEntity(
        mangaId = mangaId,
        url = url,
        name = name,
        season = season,
        chapterNumber = chapterNumber,
        dateUploadMs = 1_700_000_000_000L,
        scanlator = "Asura",
        read = read,
    )

    fun segment(
        id: String,
        chapterId: Long,
        readingOrder: Int,
        translatedText: String? = null,
        needsRetry: Boolean = false,
    ) = SegmentEntity(
        id = id,
        chapterId = chapterId,
        bubbleId = "b-$id",
        pageIndex = 0,
        ocrText = "안녕",
        ocrLang = DetectedLang.KO,
        ocrConfidence = 0.95f,
        readingOrder = readingOrder,
        translatedText = translatedText,
        needsRetry = needsRetry,
    )

    fun job(
        id: String,
        priority: Int = 0,
        createdAt: Long = 0L,
        state: JobState = JobState(),
    ) = ChapterJob(
        id = id,
        ref = ChapterRef(sourceId = 1L, mangaId = 10L, chapterId = 100L, chapterUrl = "/ch/100"),
        priority = priority,
        createdAt = createdAt,
        state = state,
    )
}
