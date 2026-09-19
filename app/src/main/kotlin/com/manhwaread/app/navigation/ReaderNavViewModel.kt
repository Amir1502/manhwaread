package com.manhwaread.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.app.reader.StreamingChapterLoader
import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.fold
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.feature.downloads.queue.ChapterDirs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

// UI-состояние читалки: каталог главы (офлайн или стриминговый кэш), стартовая
// страница из истории, прогресс стриминга и его ошибка.
data class ReaderNavUiState(
    val mangaId: Long = 0L,
    val chapterId: Long = 0L,
    val chapterDir: File? = null,
    val initialPageIndex: Int = 0,
    val isOpen: Boolean = false,
    val isStreaming: Boolean = false,
    val streamDone: Int = 0,
    val streamTotal: Int = 0,
    val streamError: AppError? = null,
)

/**
 * Навигационная ViewModel читалки: скачанная глава открывается мгновенно из
 * каталога очереди (ChapterDirs); нескачанная — стримится с источника в кэш
 * (StreamingChapterLoader) и затем открывается той же файловой читалкой без
 * перевода. Прогресс чтения пишется в историю, глава помечается прочитанной
 * при первом репорте страницы.
 */
@HiltViewModel
class ReaderNavViewModel @Inject constructor(
    private val chapterDirs: ChapterDirs,
    private val historyDao: HistoryDao,
    private val chapterDao: ChapterDao,
    private val streamingLoader: StreamingChapterLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderNavUiState())
    val uiState: StateFlow<ReaderNavUiState> = _uiState.asStateFlow()

    private var readMarked = false
    private var streamJob: Job? = null

    fun open(mangaId: Long, chapterId: Long) {
        readMarked = false
        streamJob?.cancel()
        val offlineDir = chapterDirs.dirFor(chapterId)
        _uiState.value = ReaderNavUiState(
            mangaId = mangaId,
            chapterId = chapterId,
            chapterDir = offlineDir.takeIf { dir -> isOfflineReady(dir) },
        )
        viewModelScope.launch {
            val savedPage = historyDao.forChapter(chapterId)?.pageIndex ?: 0
            _uiState.update { it.copy(initialPageIndex = savedPage) }
            if (isOfflineReady(offlineDir)) {
                _uiState.update { it.copy(isOpen = true) }
            } else {
                startStreaming(savedPage)
            }
        }
    }

    // Повтор стриминга после ошибки: ошибка сбрасывается, страницы грузятся заново.
    fun retryStream() {
        startStreaming(_uiState.value.initialPageIndex)
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

    private fun startStreaming(savedPage: Int) {
        streamJob?.cancel()
        _uiState.update {
            it.copy(
                isStreaming = true,
                streamDone = 0,
                streamTotal = 0,
                streamError = null,
                initialPageIndex = savedPage,
                isOpen = false,
            )
        }
        streamJob = viewModelScope.launch {
            val result = streamingLoader.ensureStreamed(_uiState.value.chapterId) { done, total ->
                _uiState.update { it.copy(streamDone = done, streamTotal = total) }
            }
            result.fold(
                onSuccess = { dir ->
                    _uiState.update { it.copy(chapterDir = dir, isStreaming = false, isOpen = true) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isStreaming = false, streamError = error) }
                },
            )
        }
    }

    // Глава доступна офлайн: в каталоге есть метаданные и хотя бы одна картинка
    // страницы (page*.png/jpg/jpeg/webp) — формат FileChapterLoader.
    private fun isOfflineReady(dir: File): Boolean {
        if (!dir.isDirectory || !File(dir, CHAPTER_META_FILE).isFile) return false
        val pages = dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(PAGE_PREFIX) &&
                file.extension.lowercase(Locale.US) in PAGE_EXTENSIONS
        }
        return !pages.isNullOrEmpty()
    }

    private companion object {
        const val CHAPTER_META_FILE = "chapter.json"
        const val PAGE_PREFIX = "page"
        val PAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
