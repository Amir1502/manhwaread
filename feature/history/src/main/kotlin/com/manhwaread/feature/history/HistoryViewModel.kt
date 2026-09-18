package com.manhwaread.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel истории чтения (ФАЗА 14): наблюдает последние записи и джойнит
 * их с тайтлами/главами в памяти (кэш id→строка). Записи о удалённых главах
 * пропускаются. Кэш может устаревать в пределах сессии экрана — допустимо,
 * список истории пересобирается при каждом возврате на экран.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: HistoryDao,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val mangaCache = mutableMapOf<Long, MangaEntity?>()
    private val chapterCache = mutableMapOf<Long, ChapterEntity?>()

    init {
        viewModelScope.launch {
            historyDao.observeRecent(RECENT_LIMIT).collect { entries ->
                val items = entries.mapNotNull { entry -> toHistoryItem(entry) }
                _uiState.update { state -> state.copy(items = items, isLoading = false) }
            }
        }
    }

    fun onClear() {
        viewModelScope.launch {
            historyDao.clear()
            _uiState.update { state -> state.copy(message = HistoryMessage.Cleared) }
        }
    }

    fun onMessageShown() {
        _uiState.update { state -> state.copy(message = null) }
    }

    private suspend fun toHistoryItem(entry: HistoryEntity): HistoryItem? {
        val manga = mangaFor(entry.mangaId) ?: return null
        val chapter = chapterFor(entry.chapterId) ?: return null
        return HistoryItem(
            mangaId = entry.mangaId,
            mangaTitle = manga.title,
            mangaThumbnailUrl = manga.thumbnailUrl,
            chapterId = entry.chapterId,
            chapterName = chapter.name,
            lastReadMs = entry.lastReadMs,
            pageIndex = entry.pageIndex,
        )
    }

    private suspend fun mangaFor(mangaId: Long): MangaEntity? {
        if (mangaCache.containsKey(mangaId)) return mangaCache[mangaId]
        val found = mangaDao.findById(mangaId)
        mangaCache[mangaId] = found
        return found
    }

    private suspend fun chapterFor(chapterId: Long): ChapterEntity? {
        if (chapterCache.containsKey(chapterId)) return chapterCache[chapterId]
        val found = chapterDao.findById(chapterId)
        chapterCache[chapterId] = found
        return found
    }

    private companion object {
        const val RECENT_LIMIT = 100
    }
}
