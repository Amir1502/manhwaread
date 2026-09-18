package com.manhwaread.feature.history

// Элемент списка истории: тайтл + глава + позиция чтения.
data class HistoryItem(
    val mangaId: Long,
    val mangaTitle: String,
    val mangaThumbnailUrl: String?,
    val chapterId: Long,
    val chapterName: String,
    val lastReadMs: Long,
    val pageIndex: Int,
)

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: HistoryMessage? = null,
)

// Одноразовые сообщения экрана (Snackbar).
sealed interface HistoryMessage {
    data object Cleared : HistoryMessage
}

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class HistoryActions(
    val onOpenManga: (Long) -> Unit,
    val onClear: () -> Unit,
    val onMessageShown: () -> Unit,
)
