package com.manhwaread.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.app.reader.StreamingChapterLoader
import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.fold
import com.manhwaread.core.database.BookmarkDao
import com.manhwaread.core.database.BookmarkEntity
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.datastore.ReaderSettingsStore
import com.manhwaread.core.model.Chapter
import com.manhwaread.core.model.ChapterReadingOrderComparator
import com.manhwaread.feature.downloads.queue.ChapterDirs
import com.manhwaread.feature.reader.ReaderMode
import com.manhwaread.feature.reader.ReaderNavigation
import com.manhwaread.feature.reader.ReaderTocItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

// UI-состояние читалки: каталог главы (офлайн или стриминговый кэш), стартовая
// страница из истории, прогресс стриминга и его ошибка; navigation — контекст
// тайтла для панелей читалки (оглавление, соседние главы, закладки, режим).
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
    val navigation: ReaderNavigation = ReaderNavigation(),
)

/**
 * Навигационная ViewModel читалки: скачанная глава открывается мгновенно из
 * каталога очереди (ChapterDirs); нескачанная — стримится с источника в кэш
 * (StreamingChapterLoader) и затем открывается той же файловой читалкой без
 * перевода. Прогресс чтения пишется в историю, глава помечается прочитанной
 * при первом репорте страницы. Контекст тайтла (Этап 8): оглавление в порядке
 * чтения, закладки главы и режим чтения, сохранённый для тайтла (по умолчанию —
 * по типу: манга справа налево, манхва/маньхуа — вебтун).
 */
@HiltViewModel
class ReaderNavViewModel @Inject constructor(
    private val chapterDirs: ChapterDirs,
    private val historyDao: HistoryDao,
    private val chapterDao: ChapterDao,
    private val streamingLoader: StreamingChapterLoader,
    private val mangaDao: MangaDao,
    private val bookmarkDao: BookmarkDao,
    private val readerSettings: ReaderSettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderNavUiState())
    val uiState: StateFlow<ReaderNavUiState> = _uiState.asStateFlow()

    private var readMarked = false
    private var streamJob: Job? = null
    private var bookmarksJob: Job? = null

    fun open(mangaId: Long, chapterId: Long) {
        readMarked = false
        streamJob?.cancel()
        val offlineDir = chapterDirs.dirFor(chapterId)
        _uiState.value = ReaderNavUiState(
            mangaId = mangaId,
            chapterId = chapterId,
            chapterDir = offlineDir.takeIf { dir -> isOfflineReady(dir) },
            navigation = ReaderNavigation(currentChapterId = chapterId),
        )
        observeBookmarks(chapterId)
        viewModelScope.launch {
            val savedPage = historyDao.forChapter(chapterId)?.pageIndex ?: 0
            // Контекст тайтла загружается до открытия главы: читалка сразу
            // стартует в сохранённом режиме. Сбой загрузки чтение не блокирует.
            val loaded = loadNavigationOrNull(mangaId, chapterId)
            _uiState.update { state ->
                state.copy(
                    initialPageIndex = savedPage,
                    navigation = loaded?.copy(bookmarkedPages = state.navigation.bookmarkedPages) ?: state.navigation,
                )
            }
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

    // Закладка текущей страницы: есть — удаляется, нет — создаётся.
    fun toggleBookmark(pageIndex: Int) {
        val state = _uiState.value
        if (state.chapterId <= 0L) return
        viewModelScope.launch {
            runCatching {
                val existing = bookmarkDao.observeForChapter(state.chapterId).first()
                    .firstOrNull { bookmark -> bookmark.pageIndex == pageIndex }
                if (existing != null) {
                    bookmarkDao.deleteById(existing.id)
                } else {
                    bookmarkDao.upsert(
                        BookmarkEntity(
                            mangaId = state.mangaId,
                            chapterId = state.chapterId,
                            pageIndex = pageIndex,
                            createdAtMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onFailure { error -> if (error is CancellationException) throw error }
        }
    }

    // Режим, выбранный в читалке, запоминается для тайтла.
    fun onReaderModeChanged(mode: ReaderMode) {
        val state = _uiState.value
        _uiState.update { current -> current.copy(navigation = current.navigation.copy(preferredMode = mode)) }
        if (state.mangaId <= 0L) return
        viewModelScope.launch {
            runCatching { readerSettings.setReaderMode(state.mangaId, mode.name) }
                .onFailure { error -> if (error is CancellationException) throw error }
        }
    }

    private suspend fun loadNavigationOrNull(mangaId: Long, chapterId: Long): ReaderNavigation? =
        runCatching { loadNavigation(mangaId, chapterId) }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrNull()

    private suspend fun loadNavigation(mangaId: Long, chapterId: Long): ReaderNavigation {
        val manga = mangaDao.findById(mangaId)
        val chapters = chapterDao.allForManga(mangaId)
            .map { entity -> entity.toChapter() }
            .sortedWith(ChapterReadingOrderComparator)
            .map { chapter -> ReaderTocItem(chapterId = chapter.id, title = chapter.name, isRead = chapter.read) }
        val savedMode = readerModeOf(readerSettings.readerMode(mangaId).first())
        return ReaderNavigation(
            mangaTitle = manga?.let { entity -> entity.titleRu?.takeIf { title -> title.isNotBlank() } ?: entity.title },
            chapters = chapters,
            currentChapterId = chapterId,
            preferredMode = savedMode ?: defaultReaderModeFor(manga?.type),
        )
    }

    // Закладки главы наблюдаются из БД: значок в верхней панели следует за ними.
    private fun observeBookmarks(chapterId: Long) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            bookmarkDao.observeForChapter(chapterId)
                .catch { emit(emptyList()) }
                .collect { bookmarks ->
                    val pages = bookmarks.mapTo(mutableSetOf()) { bookmark -> bookmark.pageIndex }
                    _uiState.update { state -> state.copy(navigation = state.navigation.copy(bookmarkedPages = pages)) }
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

    private fun ChapterEntity.toChapter(): Chapter =
        Chapter(
            id = id,
            mangaId = mangaId,
            url = url,
            name = name,
            season = season,
            chapterNumber = chapterNumber,
            dateUploadMs = dateUploadMs,
            scanlator = scanlator,
            read = read,
        )

    private companion object {
        const val CHAPTER_META_FILE = "chapter.json"
        const val PAGE_PREFIX = "page"
        val PAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
