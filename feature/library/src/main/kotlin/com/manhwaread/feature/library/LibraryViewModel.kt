package com.manhwaread.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel библиотеки (ФАЗА 14): наблюдает строки с inLibrary = 1,
// фильтрация по названию и статусу чтения — в памяти (список библиотеки невелик),
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
                val statusCounts = ReadingStatus.entries.associateWith { status ->
                    library.count { it.readingStatus == status }
                }
                _uiState.update { state ->
                    state.copy(
                        items = filterItems(library, state.query, state.selectedStatus),
                        allCount = library.size,
                        statusCounts = statusCounts,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                query = query,
                items = filterItems(allManga, query, state.selectedStatus),
            )
        }
    }

    fun onStatusSelected(status: ReadingStatus?) {
        _uiState.update { state ->
            state.copy(
                selectedStatus = status,
                items = filterItems(allManga, state.query, status),
            )
        }
    }

    fun onRemove(manga: MangaEntity) {
        viewModelScope.launch {
            mangaDao.setInLibrary(id = manga.id, inLibrary = false, nowMs = 0L)
        }
    }

    private fun filterItems(
        items: List<MangaEntity>,
        query: String,
        status: ReadingStatus?,
    ): List<MangaEntity> {
        val byStatus = if (status == null) items else items.filter { it.readingStatus == status }
        val normalized = query.trim()
        if (normalized.isEmpty()) return byStatus
        return byStatus.filter { manga ->
            manga.title.contains(normalized, ignoreCase = true) ||
                (manga.titleRu?.contains(normalized, ignoreCase = true) == true)
        }
    }
}
