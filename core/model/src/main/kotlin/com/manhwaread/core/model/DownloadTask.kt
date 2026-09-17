package com.manhwaread.core.model

/** Статус задачи скачивания главы. */
enum class DownloadStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * Задача скачивания главы в офлайн-хранилище.
 * [progress] — доля выполнения 0.0..1.0.
 */
data class DownloadTask(
    val id: Long = 0L,
    val mangaId: Long,
    val chapterId: Long,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val enqueuedAtMs: Long = 0L,
) {
    init {
        require(progress in 0f..1f) { "progress must be in 0.0..1.0, got $progress" }
    }
}
