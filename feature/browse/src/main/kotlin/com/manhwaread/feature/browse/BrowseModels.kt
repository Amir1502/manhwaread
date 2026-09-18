package com.manhwaread.feature.browse

import com.manhwaread.core.common.AppError
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source

// Режимы выдачи каталога: популярные, новинки, результаты поиска.
enum class BrowseListingMode { POPULAR, LATEST, SEARCH }

// Одна «страница» каталога: накопленные элементы, номер страницы, признак продолжения.
data class BrowseListing(
    val items: List<SManga> = emptyList(),
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
)

data class BrowseUiState(
    val sources: List<Source> = emptyList(),
    val selectedSource: Source? = null,
    val mode: BrowseListingMode = BrowseListingMode.POPULAR,
    val query: String = "",
    val listing: BrowseListing = BrowseListing(),
    val error: AppError? = null,
) {
    val isSourceSelected: Boolean get() = selectedSource != null
}

// Группировка колбэков экрана: фиксированный набор действий каталога
// (избегает detekt LongParameterList у Composable).
data class BrowseActions(
    val onSourceSelected: (Source) -> Unit,
    val onBackToSources: () -> Unit,
    val onModeSelected: (BrowseListingMode) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearchSubmit: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRetry: () -> Unit,
    val onMangaClick: (SManga) -> Unit,
)
