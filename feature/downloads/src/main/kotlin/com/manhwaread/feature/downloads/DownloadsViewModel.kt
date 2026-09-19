package com.manhwaread.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.database.TranslationJobDao
import com.manhwaread.core.database.TranslationJobEntity
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.JobState
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.feature.downloads.queue.ChapterDeleter
import com.manhwaread.feature.downloads.selfcheck.SelfCheckExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана «Загрузки» (ФАЗЫ 14–15): две очереди — скачивание глав
 * (DownloadTaskDao) и задачи перевода (TranslationJobDao). Названия
 * тайтлов/глав подтягиваются из БД с кэшем id→строка. Отмена доступна
 * только для незавершённых элементов; кнопка самопроверки прогоняет
 * конвейер офлайн (DoD «Self-check прогоняет пайплайн офлайн»).
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadTaskDao: DownloadTaskDao,
    private val translationJobDao: TranslationJobDao,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val selfCheckExecutor: SelfCheckExecutor,
    private val chapterDeleter: ChapterDeleter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private var rawTasks: List<DownloadTaskEntity> = emptyList()
    private var rawJobs: List<TranslationJobEntity> = emptyList()
    private val mangaCache = mutableMapOf<Long, MangaEntity?>()
    private val chapterCache = mutableMapOf<Long, ChapterEntity?>()

    init {
        viewModelScope.launch {
            combine(
                downloadTaskDao.observeAll(),
                translationJobDao.observeAllEntities(),
            ) { tasks, jobs -> tasks to jobs }.collect { (tasks, jobs) ->
                rawTasks = tasks
                rawJobs = jobs
                val taskRows = tasks.map { task -> toTaskRow(task) }
                val jobRows = jobs.map { job -> toJobRow(job) }
                _uiState.update { state ->
                    state.copy(tasks = taskRows, jobs = jobRows, isLoading = false)
                }
            }
        }
    }

    fun onCancelTask(taskId: Long) {
        val task = rawTasks.firstOrNull { it.id == taskId } ?: return
        if (task.status != DownloadStatus.PENDING && task.status != DownloadStatus.RUNNING) return
        viewModelScope.launch {
            downloadTaskDao.updateProgress(taskId, DownloadStatus.CANCELLED, task.progress)
        }
    }

    fun onCancelJob(jobId: String) {
        val job = rawJobs.firstOrNull { it.id == jobId } ?: return
        if (job.status == StageStatus.DONE || job.status == StageStatus.FAILED ||
            job.status == StageStatus.CANCELLED
        ) {
            return
        }
        viewModelScope.launch {
            translationJobDao.update(
                jobId,
                JobState(status = StageStatus.CANCELLED, attempts = job.attempts, lastError = null),
            )
        }
    }

    // Удаление скачанной главы: файлы на диске и связанные строки БД.
    // Ряд исчезает с экрана сам — потоки DAO переопубликуют очереди.
    fun onDeleteChapter(chapterId: Long) {
        viewModelScope.launch {
            chapterDeleter.delete(chapterId)
        }
    }

    // Запуск офлайн-самопроверки конвейера; повторный запуск во время прогона игнорируется.
    fun onRunSelfCheck() {
        if (_uiState.value.isSelfCheckRunning) return
        _uiState.update { state -> state.copy(isSelfCheckRunning = true, selfCheck = null) }
        viewModelScope.launch {
            val result = selfCheckExecutor.run()
            _uiState.update { state -> state.copy(isSelfCheckRunning = false, selfCheck = result) }
        }
    }

    // Результат показан (snackbar) — очищаем ячейку сообщения.
    fun onSelfCheckShown() {
        _uiState.update { state -> state.copy(selfCheck = null) }
    }

    private suspend fun toTaskRow(task: DownloadTaskEntity): DownloadTaskRow = DownloadTaskRow(
        taskId = task.id,
        chapterId = task.chapterId,
        mangaTitle = titleFor(task.mangaId),
        chapterName = chapterNameFor(task.chapterId),
        status = task.status,
        progress = task.progress,
    )

    private suspend fun toJobRow(job: TranslationJobEntity): TranslationJobRow = TranslationJobRow(
        jobId = job.id,
        mangaTitle = titleFor(job.mangaId),
        chapterName = chapterNameFor(job.chapterId),
        status = job.status,
        attempts = job.attempts,
        lastError = job.lastError,
    )

    // При удалённой строке показываем технический идентификатор: экран не должен падать.
    private suspend fun titleFor(mangaId: Long): String =
        mangaFor(mangaId)?.title ?: "#$mangaId"

    private suspend fun chapterNameFor(chapterId: Long): String =
        chapterFor(chapterId)?.name ?: "#$chapterId"

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
}
