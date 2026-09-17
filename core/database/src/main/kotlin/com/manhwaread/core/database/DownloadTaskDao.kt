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

    @Query("DELETE FROM download_tasks WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)
}
