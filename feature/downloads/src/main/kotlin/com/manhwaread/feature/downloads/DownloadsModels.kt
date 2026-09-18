package com.manhwaread.feature.downloads

import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.StageStatus

// Строка очереди скачивания глав.
data class DownloadTaskRow(
    val taskId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val status: DownloadStatus,
    val progress: Float,
)

// Строка очереди перевода (задачи конвейера).
data class TranslationJobRow(
    val jobId: String,
    val mangaTitle: String,
    val chapterName: String,
    val status: StageStatus,
    val attempts: Int,
    val lastError: String?,
)

data class DownloadsUiState(
    val tasks: List<DownloadTaskRow> = emptyList(),
    val jobs: List<TranslationJobRow> = emptyList(),
    val isLoading: Boolean = true,
)

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class DownloadsActions(
    val onCancelTask: (Long) -> Unit,
    val onCancelJob: (String) -> Unit,
)
