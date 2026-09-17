package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** OCR-сегменты глав: перевод и ручные правки переживают перезапуск (DoD). */
@Dao
interface SegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SegmentEntity>)

    @Query("DELETE FROM segments WHERE chapterId = :chapterId")
    suspend fun deleteForChapter(chapterId: Long)

    // Полная замена сегментов главы (повторный прогон конвейера OCR/перевода).
    @Transaction
    suspend fun replaceForChapter(chapterId: Long, segments: List<SegmentEntity>) {
        deleteForChapter(chapterId)
        insertAll(segments)
    }

    @Query("SELECT * FROM segments WHERE chapterId = :chapterId ORDER BY readingOrder ASC")
    fun observeForChapter(chapterId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE chapterId = :chapterId ORDER BY readingOrder ASC")
    suspend fun allForChapter(chapterId: Long): List<SegmentEntity>

    // Ручная правка перевода: editedByUser защищает текст от перезаписи автопереводом.
    @Query("UPDATE segments SET translatedText = :text, isEditedByUser = :editedByUser, needsRetry = 0 WHERE id = :id")
    suspend fun updateTranslation(id: String, text: String, editedByUser: Boolean)
}
