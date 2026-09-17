package com.manhwaread.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.source.SChapter
import com.manhwaread.core.source.SManga
import com.manhwaread.core.source.Source
import com.manhwaread.sources.SourceFormatException
import com.manhwaread.sources.SourceHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class CatalogFailure { NETWORK, RATE_LIMIT, FORMAT, UNKNOWN }

data class CatalogState(
    val items: List<SManga> = emptyList(),
    val query: String = "",
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val selected: SManga? = null,
    val chapters: List<SChapter> = emptyList(),
    val loading: Boolean = false,
    val error: CatalogFailure? = null,
)

class CatalogViewModel(private val source: Source) : ViewModel() {
    private val mutableState = MutableStateFlow(CatalogState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    init { search("") }

    fun search(query: String) {
        request?.cancel()
        mutableState.value = CatalogState(query = query.trim())
        loadCatalog(false)
    }

    fun loadMore() {
        if (!state.value.loading && state.value.hasNextPage && state.value.selected == null) loadCatalog(true)
    }

    private fun loadCatalog(append: Boolean) {
        request?.cancel()
        val previous = state.value
        val page = if (append) previous.page + 1 else 1
        mutableState.value = previous.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val result = if (previous.query.isBlank()) source.getPopular(page)
                else source.search(previous.query, emptyList(), page)
                mutableState.value = previous.copy(
                    items = ((if (append) previous.items else emptyList()) + result.mangas).distinctBy { it.url },
                    page = page,
                    hasNextPage = result.hasNextPage,
                    loading = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = previous.copy(loading = false, error = failure(error))
            }
        }
    }

    fun openDetails(manga: SManga) {
        request?.cancel()
        mutableState.value = state.value.copy(selected = manga, chapters = emptyList(), loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val details = source.getDetails(manga)
                val chapters = source.getChapterList(details)
                mutableState.value = state.value.copy(selected = details, chapters = chapters, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = state.value.copy(loading = false, error = failure(error))
            }
        }
    }

    fun back() {
        request?.cancel()
        mutableState.value = state.value.copy(selected = null, chapters = emptyList(), loading = false, error = null)
        if (state.value.page == 0) loadCatalog(false)
    }

    fun retry() {
        state.value.selected?.let(::openDetails) ?: loadCatalog(state.value.page > 0)
    }

    private fun failure(error: Exception): CatalogFailure = when {
        error is SourceHttpException && error.statusCode == 429 -> CatalogFailure.RATE_LIMIT
        error is SourceFormatException -> CatalogFailure.FORMAT
        error is IOException -> CatalogFailure.NETWORK
        else -> CatalogFailure.UNKNOWN
    }

    class Factory(private val source: Source) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CatalogViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CatalogViewModel(source) as T
        }
    }
}
