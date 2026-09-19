package com.manhwaread.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.manhwaread.core.model.ChapterNumberParser
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.source.api.MangaStatus

// Room-сущности: плоские зеркала доменных моделей :core:model / :core:pipeline /
// :core:vision-model. Value-классы (PageIndex/ScrollOffsetPx) расплющены в колонки.

@Entity(
    tableName = "manga",
    indices = [Index(value = ["sourceId", "url"], unique = true)],
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val nsfw: Boolean = false,
    val inLibrary: Boolean = false,
    val addedAtMs: Long = 0L,
    // Русский тайтл: переводится LLM-провайдером и хранится локально (null — ещё не переведён).
    val titleRu: String? = null,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mangaId", "url"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mangaId: Long,
    val url: String,
    val name: String,
    val season: Int = ChapterNumberParser.DEFAULT_SEASON,
    val chapterNumber: Float = ChapterNumberParser.UNKNOWN,
    val dateUploadMs: Long = 0L,
    val scanlator: String? = null,
    val read: Boolean = false,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    // Колонка НЕ называется "order" — это зарезервированное слово SQL.
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "manga_category_refs",
    primaryKeys = ["mangaId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class MangaCategoryCrossRef(val mangaId: Long, val categoryId: Long)

@Entity(
    tableName = "history",
    primaryKeys = ["chapterId"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mangaId")],
)
data class HistoryEntity(
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int = 0,
    val scrollOffsetPx: Int = 0,
    val lastReadMs: Long = 0L,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val createdAtMs: Long = 0L,
    val note: String? = null,
)

@Entity(tableName = "download_tasks", indices = [Index("chapterId")])
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mangaId: Long,
    val chapterId: Long,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val enqueuedAtMs: Long = 0L,
)

// Персистентная очередь конвейера (ChapterJob из :core:pipeline).
// Намеренно без FK: очередь переживает удаление глав из библиотеки.
@Entity(tableName = "translation_jobs")
data class TranslationJobEntity(
    @PrimaryKey val id: String,
    val sourceId: Long,
    val mangaId: Long,
    val chapterId: Long,
    val chapterUrl: String,
    val priority: Int = 0,
    val createdAt: Long = 0L,
    val status: StageStatus = StageStatus.QUEUED,
    val attempts: Int = 0,
    val lastError: String? = null,
)

// OCR-сегменты главы: перевод и ручные правки переживают перезапуск (DoD).
// Намеренно без FK: сегменты пишутся конвейером до/вне состояния библиотеки.
@Entity(tableName = "segments", indices = [Index("chapterId")])
data class SegmentEntity(
    @PrimaryKey val id: String,
    val chapterId: Long,
    val bubbleId: String,
    val pageIndex: Int,
    val ocrText: String,
    val ocrLang: DetectedLang,
    val ocrConfidence: Float,
    val readingOrder: Int,
    val isSfx: Boolean = false,
    val translatedText: String? = null,
    val isEditedByUser: Boolean = false,
    val needsRetry: Boolean = false,
)
