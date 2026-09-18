package com.manhwaread.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import com.manhwaread.source.api.SourceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel каталога: список источников из реестра, выдача выбранного источника
// (популярное/новинки/поиск) со страницами и ошибками источника (ФАЗА 14).
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val registry: SourceRegistry,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            registry.sources.collect { sources ->
                _uiState.update { state ->
                    state.copy(
                        sources = sources,
                        // Выбранный источник удалён из реестра — возврат к списку.
                        selectedSource = state.selectedSource
                            ?.let { selected -> sources.firstOrNull { it.id == selected.id } },
                    )
                }
            }
        }
    }

    fun onSourceSelected(source: Source) {
        _uiState.update {
            it.copy(
                selectedSource = source,
                mode = BrowseListingMode.POPULAR,
                query = "",
                listing = BrowseListing(),
                error = null,
            )
        }
        loadPage(BrowseListingMode.POPULAR, page = FIRST_PAGE, append = false)
    }

    fun onBackToSources() {
        _uiState.update {
            it.copy(selectedSource = null, query = "", listing = BrowseListing(), error = null)
        }
    }

    fun onModeSelected(mode: BrowseListingMode) {
        // В режим SEARCH переводит только отправка запроса, не чип.
        if (mode == BrowseListingMode.SEARCH) return
        _uiState.update { it.copy(mode = mode, error = null) }
        loadPage(mode, page = FIRST_PAGE, append = false)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onSearchSubmit() {
        if (_uiState.value.query.isBlank()) {
            onModeSelected(BrowseListingMode.POPULAR)
            return
        }
        _uiState.update { it.copy(mode = BrowseListingMode.SEARCH, error = null) }
        loadPage(BrowseListingMode.SEARCH, page = FIRST_PAGE, append = false)
    }

    fun onLoadMore() {
        val listing = _uiState.value.listing
        if (!listing.hasNextPage || listing.isLoading) return
        loadPage(_uiState.value.mode, page = listing.page + 1, append = true)
    }

    fun onRetry() {
        _uiState.update { it.copy(error = null) }
        loadPage(_uiState.value.mode, page = FIRST_PAGE, append = false)
    }

    private fun loadPage(mode: BrowseListingMode, page: Int, append: Boolean) {
        val source = _uiState.value.selectedSource ?: return
        val query = _uiState.value.query.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(listing = it.listing.copy(isLoading = true), error = null) }
            try {
                val result = when (mode) {
                    BrowseListingMode.POPULAR -> source.getPopular(page)
                    BrowseListingMode.LATEST -> source.getLatest(page)
                    BrowseListingMode.SEARCH -> source.search(query, emptyList(), page)
                }
                _uiState.update { state ->
                    val items = if (append) state.listing.items + result.mangas else result.mangas
                    state.copy(
                        listing = BrowseListing(
                            items = items,
                            page = page,
                            hasNextPage = result.hasNextPage,
                            isLoading = false,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                // Отмена coroutine — не ошибка источника, пробрасываем дальше.
                throw cancelled
            } catch (sourceError: SourceException) {
                _uiState.update {
                    it.copy(error = sourceError.error, listing = it.listing.copy(isLoading = false))
                }
            }
        }
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
