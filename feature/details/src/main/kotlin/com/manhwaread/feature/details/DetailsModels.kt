package com.manhwaread.feature.details

import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.MangaEntity

data class DetailsUiState(
    val manga: MangaEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val message: DetailsMessage? = null,
)

// Одноразовые сообщения экрана (показываются в Snackbar; OpenReader — навигация).
sealed interface DetailsMessage {
    data class AddedToDownloads(val chapterName: String) : DetailsMessage
    data object AddedToLibrary : DetailsMessage
    data object RemovedFromLibrary : DetailsMessage

    // Глава уже скачана — открываем офлайн-читалку вместо постановки в очередь (ФАЗА 15).
    data class OpenReader(val mangaId: Long, val chapterId: Long) : DetailsMessage
}

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class DetailsActions(
    val onBack: () -> Unit,
    val onToggleLibrary: () -> Unit,
    val onChapterClick: (ChapterEntity) -> Unit,
    val onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    val onRetry: () -> Unit,
    val onMessageShown: () -> Unit,
)
