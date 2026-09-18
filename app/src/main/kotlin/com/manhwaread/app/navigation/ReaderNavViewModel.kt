package com.manhwaread.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.feature.downloads.queue.ChapterDirs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// UI-состояние офлайн-читалки: каталог главы, стартовая страница из истории.
data class ReaderNavUiState(
    val mangaId: Long = 0L,
    val chapterId: Long = 0L,
    val chapterDir: File? = null,
    val initialPageIndex: Int = 0,
    val isOpen: Boolean = false,
)

/**
 * Навигационная ViewModel читалки (ФАЗА 15): каталог главы берётся из
 * ChapterDirs (результат скачивания из очереди), стартовая страница — из
 * истории чтения; прогресс чтения пишется в историю, глава помечается
 * прочитанной при первом репорте страницы.
 */
@HiltViewModel
class ReaderNavViewModel @Inject constructor(
    private val chapterDirs: ChapterDirs,
    private val historyDao: HistoryDao,
    private val chapterDao: ChapterDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderNavUiState())
    val uiState: StateFlow<ReaderNavUiState> = _uiState.asStateFlow()

    private var readMarked = false

    fun open(mangaId: Long, chapterId: Long) {
        readMarked = false
        _uiState.value = ReaderNavUiState(
            mangaId = mangaId,
            chapterId = chapterId,
            chapterDir = chapterDirs.dirFor(chapterId),
        )
        viewModelScope.launch {
            val savedPage = historyDao.forChapter(chapterId)?.pageIndex ?: 0
            _uiState.value = _uiState.value.copy(initialPageIndex = savedPage, isOpen = true)
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val state = _uiState.value
        if (!state.isOpen) return
        viewModelScope.launch {
            historyDao.upsert(
                HistoryEntity(
                    mangaId = state.mangaId,
                    chapterId = state.chapterId,
                    pageIndex = pageIndex,
                    scrollOffsetPx = 0,
                    lastReadMs = System.currentTimeMillis(),
                ),
            )
            if (!readMarked) {
                readMarked = true
                chapterDao.setRead(state.chapterId, read = true)
            }
        }
    }
}
