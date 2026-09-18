package com.manhwaread.feature.library

import com.manhwaread.core.database.MangaEntity

data class LibraryUiState(
    val items: List<MangaEntity> = emptyList(),
    val allCount: Int = 0,
    val query: String = "",
    val isLoading: Boolean = true,
)

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class LibraryActions(
    val onQueryChange: (String) -> Unit,
    val onOpenManga: (MangaEntity) -> Unit,
    val onRemove: (MangaEntity) -> Unit,
)
