package com.manhwaread.feature.downloads.queue

import android.util.Log
import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterPipelineCoordinator
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.PipelineStages
import com.manhwaread.core.pipeline.ProviderSegmentTranslator
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.translation.ProviderConfig
import com.manhwaread.core.translation.TranslationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Очередь приложения (ФАЗА 15): два последовательных воркера на одном scope —
 * задачи скачивания глав (download_tasks) и задачи перевода (translation_jobs
 * → ChapterPipelineCoordinator). Скачивание доводится до конца и без провайдера
 * (офлайн-чтение); задача перевода ставится в очередь только при выбранном
 * провайдере. Обрыв процесса не теряет задачи: RUNNING-задачи при старте
 * возвращаются в PENDING, застрявшие стадии и FAILED с лимитом попыток — в QUEUED.
 */
class QueueProcessor(
    private val components: QueueComponents,
    private val data: QueueData,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Прод-валидатор растров страниц — BitmapFactory; JVM-тесты подставляют фейк.
    private val imageValidator: (ByteArray) -> Boolean = ::isDecodableImage,
) {
    // Прикладной scope очереди: живёт столько же, сколько процесс.
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Запуск фоновых циклов; вызывается из Application.onCreate. */
    fun start() {
        scope.launch {
            recoverStaleTasks()
            drainLoop { processNextDownloadTask() }
        }
        scope.launch {
            drainLoop { processNextTranslationJob() || requeueStalledJobs() }
        }
    }

    // Цикл воркера: true — очередь сдвинулась, повторяем сразу; false — пауза опроса.
    // Сбой одной итерации не убивает воркер; отмена scope пробрасывается дальше.
    private suspend fun drainLoop(processNext: suspend () -> Boolean) {
        while (currentCoroutineContext().isActive) {
            val processed = try {
                processNext()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                Log.w(LOG_TAG, "queue worker iteration failed", e)
                false
            }
            if (!processed) delay(IDLE_POLL_MS)
        }
    }

    /** Одна задача скачивания: PENDING → RUNNING (прогресс по страницам) → COMPLETED/FAILED. */
    suspend fun processNextDownloadTask(): Boolean {
        val task = data.taskDao.observeByStatus(DownloadStatus.PENDING).first().firstOrNull()
            ?: return false
        return runDownloadTask(task)
    }

    private suspend fun runDownloadTask(task: DownloadTaskEntity): Boolean {
        val manga = data.mangaDao.findById(task.mangaId)
        val chapter = data.chapterDao.findById(task.chapterId)
        if (manga == null || chapter == null) {
            data.taskDao.updateProgress(task.id, DownloadStatus.FAILED, task.progress)
            return true
        }
        data.taskDao.updateProgress(task.id, DownloadStatus.RUNNING, 0f)
        val outcome = executeDownload(task.id, manga, chapter)
        val refs = outcome?.getOrNull()
        if (outcome != null && refs == null) {
            val progress = data.taskDao.findById(task.id)?.progress ?: 0f
            data.taskDao.updateProgress(task.id, DownloadStatus.FAILED, progress)
        } else if (refs != null) {
            // Запись архива внутри обработки ошибок: сбой (например, нет места)
            // больше не оставляет задачу висеть в RUNNING до перезапуска приложения.
            val archiveError = writeDownloadedArchive(chapter)
            if (archiveError == null) {
                data.taskDao.updateProgress(task.id, DownloadStatus.COMPLETED, 1f)
                enqueueTranslationIfConfigured(manga, chapter)
            } else {
                val progress = data.taskDao.findById(task.id)?.progress ?: 0f
                data.taskDao.updateProgress(task.id, DownloadStatus.FAILED, progress)
            }
        }
        // outcome == null — задача отменена из UI во время загрузки: статус уже CANCELLED.
        return true
    }

    // Обёртка записи офлайн-архива: null — успех, иначе ошибка для статуса FAILED.
    // CancellationException пробрасывается: отмена scope не является сбоем записи.
    private suspend fun writeDownloadedArchive(chapter: ChapterEntity): Throwable? =
        try {
            components.archiveWriter.writeDownloadedChapter(chapter.id, chapter.name)
            null
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (expected: Exception) {
            expected
        }

    private suspend fun executeDownload(
        taskId: Long,
        manga: MangaEntity,
        chapter: ChapterEntity,
    ): DomainResult<List<PageRef>>? =
        try {
            downloaderFor(taskId).download(
                ChapterJob(
                    id = downloadJobId(chapter.id),
                    ref = ChapterRef(manga.sourceId, manga.id, chapter.id, chapter.url),
                ),
            )
        } catch (expected: CancellationException) {
            // Отмена задачи из UI — ожидаемый исход: статус уже CANCELLED,
            // результат не нужен (null отличает отмену от ошибки стадии).
            null
        }

    private fun downloaderFor(taskId: Long) = SourceChapterDownloader(
        registry = components.registry,
        client = components.httpClient,
        pageStore = components.pageStore,
        onProgress = { done, total -> reportTaskProgress(taskId, done, total) },
        imageValidator = imageValidator,
    )

    // Прогресс задачи + проверка отмены: false останавливает загрузчик.
    private suspend fun reportTaskProgress(taskId: Long, done: Int, total: Int): Boolean {
        val task = data.taskDao.findById(taskId) ?: return false
        if (task.status == DownloadStatus.CANCELLED) {
            throw CancellationException("download task $taskId cancelled")
        }
        val fraction = if (total <= 0) 0f else done.toFloat() / total
        data.taskDao.updateProgress(taskId, DownloadStatus.RUNNING, fraction.coerceIn(0f, 1f))
        return true
    }

    // Офлайн-перевод после скачивания (DoD): задача перевода ставится только
    // при выбранном провайдере; активная или завершённая задача не перезапускается.
    private suspend fun enqueueTranslationIfConfigured(manga: MangaEntity, chapter: ChapterEntity) {
        val settings = data.settingsStore.translationSettings.first()
        if (!settings.isProviderSelected) return
        val jobId = translationJobId(chapter.id)
        val existing = data.jobDao.findEntityById(jobId)
        if (existing != null && existing.status !in REQUEUEABLE_JOB_STATUSES) return
        data.jobDao.enqueue(
            ChapterJob(
                id = jobId,
                ref = ChapterRef(manga.sourceId, manga.id, chapter.id, chapter.url),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Одна задача перевода: QUEUED → конвейер → DONE + офлайн-архив главы. */
    suspend fun processNextTranslationJob(): Boolean {
        val job = data.jobDao.next() ?: return false
        val provider = currentProvider()
        if (provider == null) {
            data.jobDao.update(
                job.id,
                job.state.copy(
                    status = StageStatus.FAILED,
                    attempts = job.state.attempts + 1,
                    lastError = AppError.ProviderAuth,
                ),
            )
            return true
        }
        val result = ChapterPipelineCoordinator(data.jobDao, pipelineStages(provider, job.ref.chapterId)).run(job.id)
        if (result.getOrNull() != null) {
            writeTranslatedArchive(job.ref.chapterId)
        }
        return true
    }

    // Перевод записывается обратно в SegmentStore декоратором: писатель архива
    // берёт для карточки бабла цельный текст сегментов, а не строки оверлея.
    private fun pipelineStages(provider: TranslationProvider, chapterId: Long) = PipelineStages(
        downloader = SourceChapterDownloader(
            registry = components.registry,
            client = components.httpClient,
            pageStore = components.pageStore,
            imageValidator = imageValidator,
        ),
        analyzer = SegmentPersistingAnalyzer(components.analyzer, components.segmentStore),
        translator = SegmentPersistingTranslator(ProviderSegmentTranslator(provider), components.segmentStore, chapterId),
        compositor = components.compositor,
        overlays = components.overlayStore,
    )

    private suspend fun writeTranslatedArchive(chapterId: Long) {
        val title = data.chapterDao.findById(chapterId)?.name.orEmpty()
        components.archiveWriter.writeTranslatedChapter(
            chapterId = chapterId,
            title = title,
            bubbles = components.bubbleStore.loadBubbles(chapterId),
            segments = components.segmentStore.loadSegments(chapterId),
            specs = components.overlayStore.loadChapter(chapterId),
        )
    }

    private suspend fun currentProvider(): TranslationProvider? {
        val settings = data.settingsStore.translationSettings.first()
        if (!settings.isProviderSelected) return null
        val config = ProviderConfig(
            id = settings.providerId,
            apiKey = data.apiKeyStore.apiKey(settings.providerId).orEmpty(),
            baseUrl = settings.baseUrl.trim().ifBlank { null },
            model = settings.model.trim().ifBlank { null },
        )
        return components.providerFactory.create(config)
    }

    // Задачи, застрявшие в RUNNING после смерти процесса, начинаем заново.
    // internal — доступен unit-тестам очереди без запуска фоновых циклов.
    internal suspend fun recoverStaleTasks() {
        data.taskDao.observeByStatus(DownloadStatus.RUNNING).first().forEach { task ->
            data.taskDao.updateProgress(task.id, DownloadStatus.PENDING, 0f)
        }
    }

    // Промежуточные стадии после обрыва процесса и FAILED с запасом попыток
    // возвращаются в QUEUED; DONE и CANCELLED не трогаем. internal — для unit-тестов.
    internal suspend fun requeueStalledJobs(): Boolean {
        val stalled = data.jobDao.all().filter { job ->
            job.state.status in RETRYABLE_JOB_STATUSES && job.state.attempts < MAX_JOB_ATTEMPTS
        }
        stalled.forEach { job -> data.jobDao.update(job.id, job.state.copy(status = StageStatus.QUEUED)) }
        return stalled.isNotEmpty()
    }

    private fun downloadJobId(chapterId: Long): String = "download-$chapterId"

    private fun translationJobId(chapterId: Long): String = "translate-$chapterId"

    private companion object {
        const val LOG_TAG = "QueueProcessor"
        const val IDLE_POLL_MS = 1_500L
        const val MAX_JOB_ATTEMPTS = 3
        val RETRYABLE_JOB_STATUSES = setOf(
            StageStatus.DOWNLOADING,
            StageStatus.ANALYZING,
            StageStatus.TRANSLATING,
            StageStatus.COMPOSITING,
            StageStatus.FAILED,
        )
        val REQUEUEABLE_JOB_STATUSES = setOf(StageStatus.FAILED, StageStatus.CANCELLED)
    }
}
