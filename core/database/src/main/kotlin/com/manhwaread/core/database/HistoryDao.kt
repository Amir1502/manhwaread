package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** История чтения: одна запись на главу (PK chapterId), точная позиция. */
@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("SELECT * FROM history WHERE chapterId = :chapterId")
    suspend fun forChapter(chapterId: Long): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY lastReadMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE mangaId = :mangaId")
    suspend fun deleteForManga(mangaId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
