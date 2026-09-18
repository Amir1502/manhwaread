package com.manhwaread.feature.downloads

import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.database.TranslationJobDao
import com.manhwaread.core.database.TranslationJobEntity
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.feature.downloads.selfcheck.SelfCheckExecutor
import com.manhwaread.feature.downloads.selfcheck.SelfCheckResult
import com.manhwaread.source.api.MangaStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeDownloadTaskDao : DownloadTaskDao {
        val rows = mutableMapOf<Long, DownloadTaskEntity>()
        private val allFlow = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())
        private var nextId = 1L

        fun seed(task: DownloadTaskEntity): DownloadTaskEntity {
            val id = if (task.id == 0L) nextId++ else task.id
            val stored = task.copy(id = id)
            rows[id] = stored
            publish()
            return stored
        }

        override suspend fun upsert(task: DownloadTaskEntity): Long {
            val id = if (task.id == 0L) nextId++ else task.id
            rows[id] = task.copy(id = id)
            publish()
            return id
        }

        override suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float) {
            rows[id]?.let { current -> rows[id] = current.copy(status = status, progress = progress) }
            publish()
        }

        override fun observeByStatus(status: DownloadStatus): Flow<List<DownloadTaskEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun findById(id: Long): DownloadTaskEntity? = rows[id]

        override suspend fun latestForChapter(chapterId: Long): DownloadTaskEntity? =
            rows.values.filter { it.chapterId == chapterId }.maxByOrNull { it.enqueuedAtMs }

        override fun observeAll(): Flow<List<DownloadTaskEntity>> = allFlow

        override suspend fun deleteByStatus(status: DownloadStatus) {
            val keys = rows.filterValues { it.status == status }.keys
            keys.forEach { rows.remove(it) }
            publish()
        }

        private fun publish() {
            allFlow.value = rows.values.sortedBy { it.enqueuedAtMs }
        }
    }

    private class FakeTranslationJobDao : TranslationJobDao {
        val rows = mutableMapOf<String, TranslationJobEntity>()
        private val allFlow = MutableStateFlow<List<TranslationJobEntity>>(emptyList())

        fun seed(job: TranslationJobEntity) {
            rows[job.id] = job
            publish()
        }

        override suspend fun upsertEntity(entity: TranslationJobEntity) {
            rows[entity.id] = entity
            publish()
        }

        override suspend fun findEntityById(id: String): TranslationJobEntity? = rows[id]

        override suspend fun nextEntity(): TranslationJobEntity? =
            rows.values.filter { it.status == StageStatus.QUEUED }
                .sortedWith(compareByDescending<TranslationJobEntity> { it.priority }.thenBy { it.createdAt })
                .firstOrNull()

        override suspend fun allEntities(): List<TranslationJobEntity> =
            rows.values.sortedWith(
                compareByDescending<TranslationJobEntity> { it.priority }.thenBy { it.createdAt },
            )

        override fun observeAllEntities(): Flow<List<TranslationJobEntity>> = allFlow

        private fun publish() {
            allFlow.value = rows.values.sortedWith(
                compareByDescending<TranslationJobEntity> { it.priority }.thenBy { it.createdAt },
            )
        }
    }

    private class FakeMangaDao : MangaDao {
        val rows = mutableMapOf<Long, MangaEntity>()

        override suspend fun upsert(manga: MangaEntity): Long {
            rows[manga.id] = manga
            return manga.id
        }

        override suspend fun upsertBySourceUrl(manga: MangaEntity): Long = upsert(manga)

        override suspend fun upsertAll(mangas: List<MangaEntity>): List<Long> = mangas.map { upsert(it) }

        override suspend fun findById(id: Long): MangaEntity? = rows[id]

        override suspend fun findBySourceUrl(sourceId: Long, url: String): MangaEntity? =
            rows.values.firstOrNull { it.sourceId == sourceId && it.url == url }

        override fun observeLibrary(): Flow<List<MangaEntity>> = MutableStateFlow(emptyList())

        override suspend fun searchInLibrary(query: String): List<MangaEntity> = emptyList()

        override suspend fun setInLibrary(id: Long, inLibrary: Boolean, nowMs: Long) = Unit

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }
    }

    private class FakeChapterDao : ChapterDao {
        val rows = mutableMapOf<Long, ChapterEntity>()

        override suspend fun insertIgnore(chapter: ChapterEntity): Long {
            rows[chapter.id] = chapter
            return chapter.id
        }

        override suspend fun updateMetadata(
            mangaId: Long,
            url: String,
            name: String,
            season: Int,
            chapterNumber: Float,
            dateUploadMs: Long,
            scanlator: String?,
        ) = Unit

        override suspend fun refreshChapters(chapters: List<ChapterEntity>) = Unit

        override fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun allForManga(mangaId: Long): List<ChapterEntity> = emptyList()

        override suspend fun findById(id: Long): ChapterEntity? = rows[id]

        override suspend fun setRead(id: Long, read: Boolean) = Unit

        override suspend fun countForManga(mangaId: Long): Int = 0

        override suspend fun deleteForManga(mangaId: Long) = Unit
    }

    private class FakeSelfCheckExecutor : SelfCheckExecutor {
        var result = SelfCheckResult(success = true, status = StageStatus.DONE, segments = 2, overlays = 1)
        var runs = 0
            private set

        // Затвор для проверки блокировки повторного запуска во время прогона.
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun run(): SelfCheckResult {
            runs++
            gate?.await()
            return result
        }
    }

    private val taskDao = FakeDownloadTaskDao()
    private val jobDao = FakeTranslationJobDao()
    private val mangaDao = FakeMangaDao()
    private val chapterDao = FakeChapterDao()
    private val selfCheckExecutor = FakeSelfCheckExecutor()

    private fun seedTitles() {
        mangaDao.rows[1L] = MangaEntity(
            id = 1L,
            sourceId = 1L,
            url = "/manga/1",
            title = "Solo Leveling",
            genres = emptyList(),
            status = MangaStatus.UNKNOWN,
            nsfw = false,
            inLibrary = false,
            addedAtMs = 0L,
        )
        chapterDao.rows[10L] = ChapterEntity(
            id = 10L,
            mangaId = 1L,
            url = "/chapter/10",
            name = "Chapter 5",
            season = 1,
            chapterNumber = 5f,
            dateUploadMs = 0L,
            scanlator = null,
            read = false,
        )
    }

    private fun job(
        id: String = "job-1",
        status: StageStatus = StageStatus.TRANSLATING,
        attempts: Int = 0,
        lastError: String? = null,
    ) = TranslationJobEntity(
        id = id,
        sourceId = 1L,
        mangaId = 1L,
        chapterId = 10L,
        chapterUrl = "/chapter/10",
        priority = 0,
        createdAt = 1L,
        status = status,
        attempts = attempts,
        lastError = lastError,
    )

    private fun viewModel(): DownloadsViewModel =
        DownloadsViewModel(taskDao, jobDao, mangaDao, chapterDao, selfCheckExecutor)

    @Test
    fun `init maps tasks and jobs with titles`() = runTest {
        seedTitles()
        taskDao.seed(
            DownloadTaskEntity(mangaId = 1L, chapterId = 10L, status = DownloadStatus.RUNNING, progress = 0.5f, enqueuedAtMs = 1L),
        )
        jobDao.seed(job(attempts = 2, lastError = "RateLimited"))
        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.tasks.size)
        val task = state.tasks[0]
        assertEquals("Solo Leveling", task.mangaTitle)
        assertEquals("Chapter 5", task.chapterName)
        assertEquals(DownloadStatus.RUNNING, task.status)
        assertEquals(0.5f, task.progress)
        assertEquals(1, state.jobs.size)
        val jobRow = state.jobs[0]
        assertEquals("job-1", jobRow.jobId)
        assertEquals(StageStatus.TRANSLATING, jobRow.status)
        assertEquals(2, jobRow.attempts)
        assertEquals("RateLimited", jobRow.lastError)
    }

    @Test
    fun `cancel running task marks it cancelled and keeps progress`() = runTest {
        seedTitles()
        val task = taskDao.seed(
            DownloadTaskEntity(mangaId = 1L, chapterId = 10L, status = DownloadStatus.RUNNING, progress = 0.4f, enqueuedAtMs = 1L),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onCancelTask(task.id)
        advanceUntilIdle()
        assertEquals(DownloadStatus.CANCELLED, taskDao.rows[task.id]?.status)
        assertEquals(0.4f, taskDao.rows[task.id]?.progress)
        assertEquals(DownloadStatus.CANCELLED, viewModel.uiState.value.tasks[0].status)
    }

    @Test
    fun `cancel completed task is ignored`() = runTest {
        seedTitles()
        val task = taskDao.seed(
            DownloadTaskEntity(mangaId = 1L, chapterId = 10L, status = DownloadStatus.COMPLETED, progress = 1f, enqueuedAtMs = 1L),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onCancelTask(task.id)
        advanceUntilIdle()
        assertEquals(DownloadStatus.COMPLETED, taskDao.rows[task.id]?.status)
    }

    @Test
    fun `cancel translating job marks cancelled and keeps attempts`() = runTest {
        seedTitles()
        jobDao.seed(job(status = StageStatus.TRANSLATING, attempts = 3))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onCancelJob("job-1")
        advanceUntilIdle()
        val stored = requireNotNull(jobDao.rows["job-1"])
        assertEquals(StageStatus.CANCELLED, stored.status)
        assertEquals(3, stored.attempts)
    }

    @Test
    fun `cancel done job is ignored`() = runTest {
        seedTitles()
        jobDao.seed(job(status = StageStatus.DONE))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onCancelJob("job-1")
        advanceUntilIdle()
        assertEquals(StageStatus.DONE, jobDao.rows["job-1"]?.status)
    }

    @Test
    fun `cancel unknown ids is no-op`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onCancelTask(404L)
        viewModel.onCancelJob("missing")
        advanceUntilIdle()
        assertTrue(taskDao.rows.isEmpty())
        assertTrue(jobDao.rows.isEmpty())
    }

    @Test
    fun `missing titles fall back to id placeholders`() = runTest {
        taskDao.seed(
            DownloadTaskEntity(mangaId = 7L, chapterId = 8L, status = DownloadStatus.PENDING, enqueuedAtMs = 1L),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        val task = viewModel.uiState.value.tasks[0]
        assertEquals("#7", task.mangaTitle)
        assertEquals("#8", task.chapterName)
    }

    @Test
    fun `queue updates republish rows`() = runTest {
        seedTitles()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tasks.isEmpty())
        jobDao.seed(job())
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.jobs.size)
    }

    @Test
    fun `self check run publishes result`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRunSelfCheck()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isSelfCheckRunning)
        assertEquals(2, state.selfCheck?.segments)
        assertEquals(1, state.selfCheck?.overlays)
        assertTrue(state.selfCheck?.success == true)
        assertEquals(1, selfCheckExecutor.runs)
    }

    @Test
    fun `self check running blocks second run`() = runTest {
        selfCheckExecutor.gate = CompletableDeferred()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRunSelfCheck()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelfCheckRunning)
        viewModel.onRunSelfCheck()
        advanceUntilIdle()
        assertEquals(1, selfCheckExecutor.runs)
        selfCheckExecutor.gate?.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSelfCheckRunning)
    }

    @Test
    fun `self check shown clears result`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRunSelfCheck()
        advanceUntilIdle()
        viewModel.onSelfCheckShown()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selfCheck)
    }
}
