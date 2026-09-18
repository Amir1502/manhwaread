package com.manhwaread.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel библиотеки (ФАЗА 14): наблюдает строки с inLibrary = 1,
// фильтрация по названию — в памяти (список библиотеки невелик),
// удаление — снятие флага (строка и главы сохраняются для истории).
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mangaDao: MangaDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allManga: List<MangaEntity> = emptyList()

    init {
        viewModelScope.launch {
            mangaDao.observeLibrary().collect { library ->
                allManga = library
                _uiState.update { state ->
                    state.copy(
                        items = filterByQuery(library, state.query),
                        allCount = library.size,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { state -> state.copy(query = query, items = filterByQuery(allManga, query)) }
    }

    fun onRemove(manga: MangaEntity) {
        viewModelScope.launch {
            mangaDao.setInLibrary(id = manga.id, inLibrary = false, nowMs = 0L)
        }
    }

    private fun filterByQuery(items: List<MangaEntity>, query: String): List<MangaEntity> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return items
        return items.filter { manga -> manga.title.contains(normalized, ignoreCase = true) }
    }
}
