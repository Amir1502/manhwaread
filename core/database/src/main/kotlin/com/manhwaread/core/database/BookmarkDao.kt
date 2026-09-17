package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Закладки на страницы глав. */
@Dao
interface BookmarkDao {
    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM bookmarks WHERE chapterId = :chapterId ORDER BY pageIndex ASC")
    fun observeForChapter(chapterId: Long): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
