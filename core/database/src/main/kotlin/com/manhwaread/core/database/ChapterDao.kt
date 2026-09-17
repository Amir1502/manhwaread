package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Главы: бесконфликтное обновление списка с сохранением флага read, наблюдение, отметки. */
@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(chapter: ChapterEntity): Long

    @Query(
        "UPDATE chapters SET name = :name, season = :season, chapterNumber = :chapterNumber, " +
            "dateUploadMs = :dateUploadMs, scanlator = :scanlator WHERE mangaId = :mangaId AND url = :url",
    )
    suspend fun updateMetadata(
        mangaId: Long,
        url: String,
        name: String,
        season: Int,
        chapterNumber: Float,
        dateUploadMs: Long,
        scanlator: String?,
    )

    // Обновление списка глав: новые вставляются, у существующих обновляются метаданные,
    // флаг read НЕ затирается (в отличие от INSERT OR REPLACE).
    @Transaction
    suspend fun refreshChapters(chapters: List<ChapterEntity>) {
        for (chapter in chapters) {
            insertIgnore(chapter)
            updateMetadata(
                chapter.mangaId,
                chapter.url,
                chapter.name,
                chapter.season,
                chapter.chapterNumber,
                chapter.dateUploadMs,
                chapter.scanlator,
            )
        }
    }

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY season ASC, chapterNumber ASC, id ASC")
    fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY season ASC, chapterNumber ASC, id ASC")
    suspend fun allForManga(mangaId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun findById(id: Long): ChapterEntity?

    @Query("UPDATE chapters SET read = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId")
    suspend fun countForManga(mangaId: Long): Int

    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteForManga(mangaId: Long)
}
