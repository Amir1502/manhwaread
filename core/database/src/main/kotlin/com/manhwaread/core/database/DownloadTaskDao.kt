package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.manhwaread.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/** Задачи офлайн-скачивания глав. */
@Dao
interface DownloadTaskDao {
    @Upsert
    suspend fun upsert(task: DownloadTaskEntity): Long

    @Query("UPDATE download_tasks SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float)

    @Query("SELECT * FROM download_tasks WHERE status = :status ORDER BY enqueuedAtMs ASC")
    fun observeByStatus(status: DownloadStatus): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun findById(id: Long): DownloadTaskEntity?

    // Последняя задача главы: карточка решает, глава уже скачана или ставится в очередь (ФАЗА 15).
    @Query("SELECT * FROM download_tasks WHERE chapterId = :chapterId ORDER BY enqueuedAtMs DESC LIMIT 1")
    suspend fun latestForChapter(chapterId: Long): DownloadTaskEntity?

    // Вся очередь загрузок для экрана «Загрузки» (ФАЗА 14).
    @Query("SELECT * FROM download_tasks ORDER BY enqueuedAtMs ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("DELETE FROM download_tasks WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)
}
