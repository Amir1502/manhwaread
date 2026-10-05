package com.manhwaread.feature.library

import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.model.ReadingStatus

data class LibraryUiState(
    val items: List<MangaEntity> = emptyList(),
    val allCount: Int = 0,
    val query: String = "",
    val isLoading: Boolean = true,
    val selectedStatus: ReadingStatus? = null,
    val statusCounts: Map<ReadingStatus, Int> = emptyMap(),
)

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class LibraryActions(
    val onQueryChange: (String) -> Unit,
    val onStatusSelected: (ReadingStatus?) -> Unit,
    val onOpenManga: (MangaEntity) -> Unit,
    val onRemove: (MangaEntity) -> Unit,
)
