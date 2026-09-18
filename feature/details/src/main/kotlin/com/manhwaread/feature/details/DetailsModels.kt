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

// Одноразовые сообщения экрана (показываются в Snackbar).
sealed interface DetailsMessage {
    data class AddedToDownloads(val chapterName: String) : DetailsMessage
    data object AddedToLibrary : DetailsMessage
    data object RemovedFromLibrary : DetailsMessage
}

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class DetailsActions(
    val onBack: () -> Unit,
    val onToggleLibrary: () -> Unit,
    val onChapterClick: (ChapterEntity) -> Unit,
    val onRetry: () -> Unit,
    val onMessageShown: () -> Unit,
)
