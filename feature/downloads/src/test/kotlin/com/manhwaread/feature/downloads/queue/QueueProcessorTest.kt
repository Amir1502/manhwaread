package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.database.TranslationJobEntity
import com.manhwaread.core.datastore.TranslationSettings
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.InMemoryOverlayStore
import com.manhwaread.core.pipeline.InMemorySegmentStore
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.translation.TranslationProviderFactory
import com.manhwaread.feature.downloads.vision.InMemoryBubbleStore
import com.manhwaread.source.api.InMemorySourceRegistry
import com.manhwaread.source.api.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class QueueProcessorTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var server: MockWebServer
    private val taskDao = FakeDownloadTaskDao()
    private val jobDao = FakeQueueJobDao()
    private val mangaDao = FakeQueueMangaDao()
    private val chapterDao = FakeQueueChapterDao()
    private lateinit var settingsStore: FakeQueueSettingsStore
    private val apiKeyStore = FakeQueueApiKeyStore()
    private val analyzer = FakeQueueAnalyzer()
    private val compositor = FakeQueueCompositor()
    private lateinit var source: FakePageSource
    private lateinit var dirs: ChapterDirs
    private lateinit var processor: QueueProcessor

    private val pageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        settingsStore = FakeQueueSettingsStore()
        dirs = ChapterDirs(tempDir)
        source = FakePageSource(baseUrl = server.url("/").toString())
        val registry = InMemorySourceRegistry()
        registry.register(source)
        val components = QueueComponents(
            registry = registry,
            httpClient = OkHttpClient(),
            pageStore = FilePageStore(dirs, Dispatchers.Unconfined),
            segmentStore = InMemorySegmentStore(),
            overlayStore = InMemoryOverlayStore(),
            bubbleStore = InMemoryBubbleStore(),
            analyzer = analyzer,
            compositor = compositor,
            archiveWriter = ChapterArchiveWriter(dirs, Dispatchers.Unconfined),
            providerFactory = TranslationProviderFactory(OkHttpClient()),
        )
        val data = QueueData(
            taskDao = taskDao,
            jobDao = jobDao,
            mangaDao = mangaDao,
            chapterDao = chapterDao,
            settingsStore = settingsStore,
            apiKeyStore = apiKeyStore,
        )
        processor = QueueProcessor(components, data, Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun seedChapter(): Pair<Long, Long> {
        val mangaId = mangaDao.upsert(MangaEntity(sourceId = 7L, url = "/m", title = "M"))
        val chapterId = chapterDao.insertIgnore(ChapterEntity(mangaId = mangaId, url = "/ch/10", name = "Глава 10"))
        return mangaId to chapterId
    }

    private fun seedTask(mangaId: Long, chapterId: Long): Long =
        taskDao.seedTask(DownloadTaskEntity(mangaId = mangaId, chapterId = chapterId, enqueuedAtMs = 10L))

    private fun seedPages(count: Int) {
        source.pages = (0 until count).map { index -> Page(index, server.url("/p$index.png").toString()) }
        repeat(count) { server.enqueue(MockResponse().setBody(Buffer().write(pageBytes))) }
    }

    @Test
    fun `download task completes and writes offline archive`() = runTest {
        val (mangaId, chapterId) = seedChapter()
        val taskId = seedTask(mangaId, chapterId)
        seedPages(count = 2)

        assertTrue(processor.processNextDownloadTask())

        val task = taskDao.findById(taskId)
        assertEquals(DownloadStatus.COMPLETED, task?.status)
        assertEquals(1f, task?.progress)
        assertEquals("/ch/10", source.lastChapterUrl)
        val meta = File(dirs.dirFor(chapterId), ChapterArchiveWriter.META_FILE_NAME)
        assertTrue(meta.isFile)
        assertTrue(File(dirs.dirFor(chapterId), FileOverlayStore.OVERLAYS_FILE_NAME).isFile)
        assertTrue(File(dirs.dirFor(chapterId), "page001.jpg").isFile)
        // Провайдер не выбран — задача перевода НЕ ставится (перевод только по настройке).
        assertNull(jobDao.findEntityById("translate-$chapterId"))
    }

    @Test
    fun `download task enqueues translation when provider selected`() = runTest {
        settingsStore.updateTranslation(TranslationSettings(providerId = "gemini"))
        val (mangaId, chapterId) = seedChapter()
        seedTask(mangaId, chapterId)
        seedPages(count = 1)

        processor.processNextDownloadTask()

        val job = jobDao.findEntityById("translate-$chapterId")
        assertNotNull(job)
        assertEquals(StageStatus.QUEUED, job?.status)
        assertEquals("/ch/10", job?.chapterUrl)
        assertEquals(7L, job?.sourceId)
    }

    @Test
    fun `download failure marks task failed`() = runTest {
        val (mangaId, chapterId) = seedChapter()
        val taskId = seedTask(mangaId, chapterId)
        source.pages = listOf(Page(0, server.url("/p0.png").toString()))
        source.failure = AppError.CloudflareBlocked

        processor.processNextDownloadTask()

        assertEquals(DownloadStatus.FAILED, taskDao.findById(taskId)?.status)
        assertFalseMeta(chapterId)
    }

    @Test
    fun `cancelled during download task stays cancelled`() = runTest {
        val (mangaId, chapterId) = seedChapter()
        val taskId = seedTask(mangaId, chapterId)
        seedPages(count = 2)
        taskDao.cancelOnRunning = true

        processor.processNextDownloadTask()

        assertEquals(DownloadStatus.CANCELLED, taskDao.findById(taskId)?.status)
        assertFalseMeta(chapterId)
    }

    @Test
    fun `missing manga marks task failed`() = runTest {
        val taskId = taskDao.seedTask(DownloadTaskEntity(mangaId = 999L, chapterId = 999L, enqueuedAtMs = 1L))
        assertTrue(processor.processNextDownloadTask())
        assertEquals(DownloadStatus.FAILED, taskDao.findById(taskId)?.status)
        // Пустая очередь — false.
        assertEquals(false, processor.processNextDownloadTask())
    }

    @Test
    fun `translation job without provider fails with ProviderAuth`() = runTest {
        jobDao.upsertEntity(
            TranslationJobEntity(
                id = "translate-10",
                sourceId = 7L,
                mangaId = 1L,
                chapterId = 10L,
                chapterUrl = "/ch/10",
                createdAt = 5L,
            ),
        )

        assertTrue(processor.processNextTranslationJob())

        val job = jobDao.findEntityById("translate-10")
        assertEquals(StageStatus.FAILED, job?.status)
        assertEquals(1, job?.attempts)
        assertTrue(job?.lastError?.contains("ProviderAuth") == true, "lastError=${job?.lastError}")
        // Пустая очередь — false.
        assertEquals(false, processor.processNextTranslationJob())
    }

    @Test
    fun `requeue stalled jobs respects attempts and terminal statuses`() = runTest {
        jobDao.upsertEntity(jobEntity("j-failed-once", StageStatus.FAILED, attempts = 1))
        jobDao.upsertEntity(jobEntity("j-failed-max", StageStatus.FAILED, attempts = MAX_ATTEMPTS_LIMIT))
        jobDao.upsertEntity(jobEntity("j-done", StageStatus.DONE, attempts = 0))
        jobDao.upsertEntity(jobEntity("j-cancelled", StageStatus.CANCELLED, attempts = 0))
        jobDao.upsertEntity(jobEntity("j-stalled", StageStatus.ANALYZING, attempts = 1))

        assertTrue(processor.requeueStalledJobs())

        assertEquals(StageStatus.QUEUED, jobDao.findEntityById("j-failed-once")?.status)
        assertEquals(StageStatus.FAILED, jobDao.findEntityById("j-failed-max")?.status)
        assertEquals(StageStatus.DONE, jobDao.findEntityById("j-done")?.status)
        assertEquals(StageStatus.CANCELLED, jobDao.findEntityById("j-cancelled")?.status)
        assertEquals(StageStatus.QUEUED, jobDao.findEntityById("j-stalled")?.status)
    }

    @Test
    fun `recover stale tasks resets running to pending`() = runTest {
        val taskId = taskDao.seedTask(
            DownloadTaskEntity(mangaId = 1L, chapterId = 2L, status = DownloadStatus.RUNNING, progress = 0.4f),
        )

        processor.recoverStaleTasks()

        val task = taskDao.findById(taskId)
        assertEquals(DownloadStatus.PENDING, task?.status)
        assertEquals(0f, task?.progress)
    }

    private fun assertFalseMeta(chapterId: Long) {
        assertNull(File(dirs.dirFor(chapterId), ChapterArchiveWriter.META_FILE_NAME).takeIf { file -> file.isFile })
    }

    private fun jobEntity(id: String, status: StageStatus, attempts: Int) = TranslationJobEntity(
        id = id,
        sourceId = 7L,
        mangaId = 1L,
        chapterId = 10L,
        chapterUrl = "/ch/10",
        createdAt = 1L,
        status = status,
        attempts = attempts,
    )

    private companion object {
        const val MAX_ATTEMPTS_LIMIT = 3
    }
}
