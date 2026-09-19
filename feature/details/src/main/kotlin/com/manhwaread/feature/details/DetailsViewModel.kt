package com.manhwaread.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.datastore.ApiKeyStore
import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.translation.ProviderConfig
import com.manhwaread.core.translation.TitleTranslator
import com.manhwaread.core.translation.TranslationProvider
import com.manhwaread.core.translation.TranslationProviderFactory
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.SourceException
import com.manhwaread.source.api.SourceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel карточки тайтла (ФАЗА 14): кэш из БД показывается мгновенно, затем
 * источник обновляет детали и список глав (read-флаги глав не затираются —
 * refreshChapters). Клик по главе всегда открывает читалку (нескачанная глава
 * стримится с источника); скачивание — явное действие [onChapterDownloadClick].
 * Тайтл переводится на русский выбранным провайдером один раз и хранится в
 * [MangaDao.setTitleRu] — обновление карточки его не затирает.
 *
 * Вход через [openById] (библиотека/история) или [openBySourceUrl] (каталог).
 */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val providerFactory: TranslationProviderFactory,
    private val settingsStore: SettingsStore,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var sourceId: Long = 0L
    private var mangaUrl: String = ""
    private var chaptersJob: Job? = null

    fun openById(mangaId: Long) {
        viewModelScope.launch {
            val entity = mangaDao.findById(mangaId)
            if (entity == null) {
                _uiState.update { it.copy(error = AppError.SourceUnavailable) }
                return@launch
            }
            sourceId = entity.sourceId
            mangaUrl = entity.url
            startOpen()
        }
    }

    fun openBySourceUrl(newSourceId: Long, newUrl: String) {
        viewModelScope.launch {
            sourceId = newSourceId
            mangaUrl = newUrl
            startOpen()
        }
    }

    fun onToggleLibrary() {
        val manga = _uiState.value.manga ?: return
        viewModelScope.launch {
            val newInLibrary = !manga.inLibrary
            mangaDao.setInLibrary(manga.id, newInLibrary, System.currentTimeMillis())
            val fresh = mangaDao.findById(manga.id)
            _uiState.update {
                it.copy(
                    manga = fresh,
                    message = if (newInLibrary) {
                        DetailsMessage.AddedToLibrary
                    } else {
                        DetailsMessage.RemovedFromLibrary
                    },
                )
            }
        }
    }

    // Клик по главе: читалка открывается сразу — нескачанная глава стримится
    // с источника на уровне приложения (чтение без загрузки, без перевода).
    fun onChapterClick(chapter: ChapterEntity) {
        _uiState.update {
            it.copy(message = DetailsMessage.OpenReader(mangaId = chapter.mangaId, chapterId = chapter.id))
        }
    }

    // Явная кнопка скачивания главы: задача ставится в очередь загрузок.
    fun onChapterDownloadClick(chapter: ChapterEntity) {
        viewModelScope.launch {
            downloadTaskDao.upsert(
                DownloadTaskEntity(
                    mangaId = chapter.mangaId,
                    chapterId = chapter.id,
                    status = DownloadStatus.PENDING,
                    progress = 0f,
                    enqueuedAtMs = System.currentTimeMillis(),
                ),
            )
            _uiState.update { it.copy(message = DetailsMessage.AddedToDownloads(chapter.name)) }
        }
    }

    fun onRetry() {
        viewModelScope.launch { startOpen() }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun startOpen() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        // Кэш из БД показываем сразу, до сети.
        val cached = mangaDao.findBySourceUrl(sourceId, mangaUrl)
        if (cached != null) {
            _uiState.update { it.copy(manga = cached) }
            observeChapters(cached.id)
        }
        val source = registry.get(sourceId)
        if (source == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    // Кэш есть — карточка показана, ошибка не мешает чтению.
                    error = if (cached == null) AppError.SourceUnavailable else null,
                )
            }
            return
        }
        try {
            val probe = SManga(url = mangaUrl, title = cached?.title.orEmpty(), sourceId = sourceId)
            val details = source.getDetails(probe)
            // Перечитываем строку перед upsert: за время запроса могли нажать «в библиотеку».
            val existing = mangaDao.findBySourceUrl(sourceId, details.url)
            val mangaId = mangaDao.upsertBySourceUrl(details.toEntityPreserving(existing))
            val fresh = mangaDao.findById(mangaId)
            _uiState.update { it.copy(manga = fresh) }
            ensureTitleTranslated(mangaId, details.title)
            observeChapters(mangaId)
            val chapters = source.getChapterList(details)
            chapterDao.refreshChapters(chapters.map { chapter -> chapter.toEntity(mangaId = mangaId) })
            _uiState.update { it.copy(isLoading = false) }
        } catch (cancelled: CancellationException) {
            // Отмена coroutine — не ошибка источника.
            throw cancelled
        } catch (sourceError: SourceException) {
            _uiState.update { it.copy(isLoading = false, error = sourceError.error) }
        }
    }

    private fun observeChapters(mangaId: Long) {
        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            chapterDao.observeForManga(mangaId).collect { chapters ->
                _uiState.update { it.copy(chapters = chapters) }
            }
        }
    }

    // Перевод тайтла выполняется фоном и один раз на тайтл: если titleRu уже
    // сохранён или провайдер не выбран, запрос к LLM не делается.
    private fun ensureTitleTranslated(mangaId: Long, title: String) {
        viewModelScope.launch {
            if (mangaDao.findById(mangaId)?.titleRu != null) return@launch
            val provider = currentTitleProvider() ?: return@launch
            val translated = TitleTranslator(provider).translate(title) ?: return@launch
            mangaDao.setTitleRu(mangaId, translated)
            if (_uiState.value.manga?.id == mangaId) {
                _uiState.update { it.copy(manga = mangaDao.findById(mangaId)) }
            }
        }
    }

    // Провайдер перевода из настроек — собирается как в очереди перевода.
    private suspend fun currentTitleProvider(): TranslationProvider? {
        val settings = settingsStore.translationSettings.first()
        if (!settings.isProviderSelected) return null
        val config = ProviderConfig(
            id = settings.providerId,
            apiKey = apiKeyStore.apiKey(settings.providerId).orEmpty(),
            baseUrl = settings.baseUrl.trim().ifBlank { null },
            model = settings.model.trim().ifBlank { null },
        )
        return providerFactory.create(config)
    }
}
