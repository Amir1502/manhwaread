package com.manhwaread.feature.details

import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.source.api.Filter
import com.manhwaread.source.api.InMemorySourceRegistry
import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
class DetailsViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeMangaDao : MangaDao {
        val rows = mutableMapOf<Long, MangaEntity>()
        val inLibraryCalls = mutableListOf<Triple<Long, Boolean, Long>>()
        private var nextId = 1L

        override suspend fun upsert(manga: MangaEntity): Long {
            val id = if (manga.id == 0L) nextId++ else manga.id
            rows[id] = manga.copy(id = id)
            return id
        }

        override suspend fun upsertBySourceUrl(manga: MangaEntity): Long {
            val existing = findBySourceUrl(manga.sourceId, manga.url)
            return if (existing == null) upsert(manga) else upsert(manga.copy(id = existing.id))
        }

        override suspend fun upsertAll(mangas: List<MangaEntity>): List<Long> = mangas.map { upsert(it) }

        override suspend fun findById(id: Long): MangaEntity? = rows[id]

        override suspend fun findBySourceUrl(sourceId: Long, url: String): MangaEntity? =
            rows.values.firstOrNull { it.sourceId == sourceId && it.url == url }

        override fun observeLibrary(): Flow<List<MangaEntity>> = flow {
            emit(rows.values.filter { it.inLibrary })
        }

        override suspend fun searchInLibrary(query: String): List<MangaEntity> =
            rows.values.filter { it.inLibrary && it.title.contains(query, ignoreCase = true) }

        override suspend fun setInLibrary(id: Long, inLibrary: Boolean, nowMs: Long) {
            inLibraryCalls += Triple(id, inLibrary, nowMs)
            rows[id]?.let { current ->
                rows[id] = current.copy(
                    inLibrary = inLibrary,
                    addedAtMs = if (inLibrary) nowMs else current.addedAtMs,
                )
            }
        }

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }
    }

    private class FakeChapterDao : ChapterDao {
        val rows = mutableListOf<ChapterEntity>()
        private val chaptersFlow = MutableStateFlow<List<ChapterEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insertIgnore(chapter: ChapterEntity): Long {
            if (rows.any { it.mangaId == chapter.mangaId && it.url == chapter.url }) return -1L
            val id = if (chapter.id == 0L) nextId++ else chapter.id
            rows += chapter.copy(id = id)
            publish()
            return id
        }

        override suspend fun updateMetadata(
            mangaId: Long,
            url: String,
            name: String,
            season: Int,
            chapterNumber: Float,
            dateUploadMs: Long,
            scanlator: String?,
        ) {
            val index = rows.indexOfFirst { it.mangaId == mangaId && it.url == url }
            if (index >= 0) {
                rows[index] = rows[index].copy(
                    name = name,
                    season = season,
                    chapterNumber = chapterNumber,
                    dateUploadMs = dateUploadMs,
                    scanlator = scanlator,
                )
                publish()
            }
        }

        override suspend fun refreshChapters(chapters: List<ChapterEntity>) {
            for (chapter in chapters) {
                insertIgnore(chapter)
                updateMetadata(
                    chapter.mangaId,
                    chapter.url,
                    chapter.name,
                    chapter.season,
                    chapter.chapterNumber,
                    chapter.dateUploadMs,
                    chapter.scanlator,
                )
            }
        }

        override fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>> =
            chaptersFlow.map { all -> all.filter { it.mangaId == mangaId } }

        override suspend fun allForManga(mangaId: Long): List<ChapterEntity> =
            rows.filter { it.mangaId == mangaId }

        override suspend fun findById(id: Long): ChapterEntity? = rows.firstOrNull { it.id == id }

        override suspend fun setRead(id: Long, read: Boolean) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) {
                rows[index] = rows[index].copy(read = read)
                publish()
            }
        }

        override suspend fun countForManga(mangaId: Long): Int = rows.count { it.mangaId == mangaId }

        override suspend fun deleteForManga(mangaId: Long) {
            rows.removeAll { it.mangaId == mangaId }
            publish()
        }

        private fun publish() {
            chaptersFlow.value = rows.toList()
        }
    }

    private class FakeDownloadTaskDao : DownloadTaskDao {
        val tasks = mutableListOf<DownloadTaskEntity>()
        private var nextId = 1L

        override suspend fun upsert(task: DownloadTaskEntity): Long {
            val id = if (task.id == 0L) nextId++ else task.id
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) tasks[index] = task.copy(id = id) else tasks += task.copy(id = id)
            return id
        }

        override suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float) {
            val index = tasks.indexOfFirst { it.id == id }
            if (index >= 0) tasks[index] = tasks[index].copy(status = status, progress = progress)
        }

        override fun observeByStatus(status: DownloadStatus): Flow<List<DownloadTaskEntity>> = flow {
            emit(tasks.filter { it.status == status })
        }

        override suspend fun findById(id: Long): DownloadTaskEntity? = tasks.firstOrNull { it.id == id }

        override suspend fun latestForChapter(chapterId: Long): DownloadTaskEntity? =
            tasks.filter { it.chapterId == chapterId }.maxByOrNull { it.enqueuedAtMs }

        override fun observeAll(): Flow<List<DownloadTaskEntity>> = flow { emit(tasks.toList()) }

        override suspend fun deleteByStatus(status: DownloadStatus) {
            tasks.removeAll { it.status == status }
        }
    }

    private class FakeSource(
        override val id: Long,
        private val details: SManga?,
        private val chapters: List<SChapter> = emptyList(),
        var failure: AppError? = null,
    ) : Source {
        var detailsCalls = 0
            private set
        var lastProbeUrl: String? = null
            private set

        override val name: String = "Fake"
        override val lang: String = "en"
        override val baseUrl: String = "https://fake.test"
        override val supportsSearch: Boolean = true
        override val isNsfw: Boolean = false

        override suspend fun getPopular(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatest(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage =
            MangasPage(emptyList(), false)

        override suspend fun getDetails(manga: SManga): SManga {
            detailsCalls++
            lastProbeUrl = manga.url
            failure?.let { error -> throw SourceException(error) }
            return details ?: throw SourceException(AppError.SourceLayoutChanged)
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            failure?.let { error -> throw SourceException(error) }
            return chapters
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    private val detailsManga = SManga(
        url = "/manga/solo",
        title = "Solo Leveling",
        sourceId = 1L,
        author = "Chugong",
        status = MangaStatus.ONGOING,
        genres = listOf("Action"),
        thumbnailUrl = "https://cdn.example/cover.jpg",
        initialized = true,
    )

    private fun viewModelWith(source: Source?): DetailsViewModel {
        val registry = InMemorySourceRegistry()
        source?.let { registry.register(it) }
        return DetailsViewModel(registry, mangaDao, chapterDao, downloadTaskDao)
    }

    private val mangaDao = FakeMangaDao()
    private val chapterDao = FakeChapterDao()
    private val downloadTaskDao = FakeDownloadTaskDao()

    @Test
    fun `open by source url loads details and chapters`() = runTest {
        val source = FakeSource(
            id = 1L,
            details = detailsManga,
            chapters = listOf(
                SChapter(url = "/manga/solo/chapter-5", name = "Season 2 - Chapter 5", dateUpload = 123L, scanlator = "group"),
            ),
        )
        val viewModel = viewModelWith(source)
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("Solo Leveling", state.manga?.title)
        assertEquals("Chugong", state.manga?.author)
        assertEquals(MangaStatus.ONGOING, state.manga?.status)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("/manga/solo", source.lastProbeUrl)
        assertEquals(1, state.chapters.size)
        val chapter = state.chapters[0]
        assertEquals(2, chapter.season)
        assertEquals(5f, chapter.chapterNumber)
        assertEquals(123L, chapter.dateUploadMs)
        assertEquals("group", chapter.scanlator)
        assertFalse(chapter.read)
    }

    @Test
    fun `open by id uses stored source coordinates`() = runTest {
        val stored = MangaEntity(id = 7L, sourceId = 1L, url = "/manga/solo", title = "cached")
        mangaDao.rows[7L] = stored
        val source = FakeSource(id = 1L, details = detailsManga)
        val viewModel = viewModelWith(source)
        viewModel.openById(7L)
        advanceUntilIdle()
        assertEquals("Solo Leveling", viewModel.uiState.value.manga?.title)
        assertEquals("/manga/solo", source.lastProbeUrl)
    }

    @Test
    fun `open by unknown id reports SourceUnavailable`() = runTest {
        val viewModel = viewModelWith(FakeSource(id = 1L, details = detailsManga))
        viewModel.openById(404L)
        advanceUntilIdle()
        assertEquals(AppError.SourceUnavailable, viewModel.uiState.value.error)
    }

    @Test
    fun `missing source without cache reports SourceUnavailable`() = runTest {
        val viewModel = viewModelWith(source = null)
        viewModel.openBySourceUrl(9L, "/manga/x")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(AppError.SourceUnavailable, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `source error with cache keeps cached manga`() = runTest {
        mangaDao.rows[3L] = MangaEntity(id = 3L, sourceId = 1L, url = "/manga/solo", title = "cached title")
        val source = FakeSource(id = 1L, details = null, failure = AppError.SourceLayoutChanged)
        val viewModel = viewModelWith(source)
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("cached title", state.manga?.title)
        assertEquals(AppError.SourceLayoutChanged, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `toggle library flips flag and emits message`() = runTest {
        val viewModel = viewModelWith(FakeSource(id = 1L, details = detailsManga))
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val manga = requireNotNull(viewModel.uiState.value.manga)
        assertFalse(manga.inLibrary)
        viewModel.onToggleLibrary()
        advanceUntilIdle()
        assertTrue(requireNotNull(viewModel.uiState.value.manga).inLibrary)
        assertEquals(DetailsMessage.AddedToLibrary, viewModel.uiState.value.message)
        assertEquals(1, mangaDao.inLibraryCalls.size)
        assertTrue(mangaDao.inLibraryCalls[0].second)
        assertTrue(mangaDao.inLibraryCalls[0].third > 0L)
        viewModel.onToggleLibrary()
        advanceUntilIdle()
        assertFalse(requireNotNull(viewModel.uiState.value.manga).inLibrary)
        assertEquals(DetailsMessage.RemovedFromLibrary, viewModel.uiState.value.message)
    }

    @Test
    fun `chapter click enqueues pending download task`() = runTest {
        val source = FakeSource(
            id = 1L,
            details = detailsManga,
            chapters = listOf(SChapter(url = "/manga/solo/chapter-5", name = "Chapter 5")),
        )
        val viewModel = viewModelWith(source)
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val chapter = viewModel.uiState.value.chapters.first()
        viewModel.onChapterClick(chapter)
        advanceUntilIdle()
        assertEquals(1, downloadTaskDao.tasks.size)
        val task = downloadTaskDao.tasks[0]
        assertEquals(chapter.id, task.chapterId)
        assertEquals(chapter.mangaId, task.mangaId)
        assertEquals(DownloadStatus.PENDING, task.status)
        assertEquals(0f, task.progress)
        assertTrue(task.enqueuedAtMs > 0L)
        assertEquals(DetailsMessage.AddedToDownloads("Chapter 5"), viewModel.uiState.value.message)
    }

    @Test
    fun `chapter click opens reader when download completed`() = runTest {
        val source = FakeSource(
            id = 1L,
            details = detailsManga,
            chapters = listOf(SChapter(url = "/manga/solo/chapter-5", name = "Chapter 5")),
        )
        val viewModel = viewModelWith(source)
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val chapter = viewModel.uiState.value.chapters.first()
        downloadTaskDao.upsert(
            DownloadTaskEntity(
                mangaId = chapter.mangaId,
                chapterId = chapter.id,
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                enqueuedAtMs = 5L,
            ),
        )
        viewModel.onChapterClick(chapter)
        advanceUntilIdle()
        // Новая задача НЕ создаётся — глава уже скачана, открывается читалка.
        assertEquals(1, downloadTaskDao.tasks.size)
        assertEquals(
            DetailsMessage.OpenReader(mangaId = chapter.mangaId, chapterId = chapter.id),
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `chapter click re-enqueues when latest download failed`() = runTest {
        val source = FakeSource(
            id = 1L,
            details = detailsManga,
            chapters = listOf(SChapter(url = "/manga/solo/chapter-5", name = "Chapter 5")),
        )
        val viewModel = viewModelWith(source)
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val chapter = viewModel.uiState.value.chapters.first()
        downloadTaskDao.upsert(
            DownloadTaskEntity(
                mangaId = chapter.mangaId,
                chapterId = chapter.id,
                status = DownloadStatus.FAILED,
                progress = 0.2f,
                enqueuedAtMs = 5L,
            ),
        )
        viewModel.onChapterClick(chapter)
        advanceUntilIdle()
        assertEquals(2, downloadTaskDao.tasks.size)
        assertEquals(DownloadStatus.PENDING, downloadTaskDao.tasks[1].status)
        assertEquals(DetailsMessage.AddedToDownloads("Chapter 5"), viewModel.uiState.value.message)
    }

    @Test
    fun `library flag survives details refresh`() = runTest {
        mangaDao.rows[5L] = MangaEntity(
            id = 5L,
            sourceId = 1L,
            url = "/manga/solo",
            title = "cached",
            inLibrary = true,
            addedAtMs = 999L,
        )
        val viewModel = viewModelWith(FakeSource(id = 1L, details = detailsManga))
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        val refreshed = requireNotNull(viewModel.uiState.value.manga)
        assertEquals("Solo Leveling", refreshed.title)
        assertTrue(refreshed.inLibrary)
        assertEquals(999L, refreshed.addedAtMs)
    }

    @Test
    fun `message cleared after shown`() = runTest {
        val viewModel = viewModelWith(FakeSource(id = 1L, details = detailsManga))
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        viewModel.onToggleLibrary()
        advanceUntilIdle()
        assertEquals(DetailsMessage.AddedToLibrary, viewModel.uiState.value.message)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `retry reloads after failure`() = runTest {
        val source = FakeSource(id = 1L, details = null, failure = AppError.SourceUnavailable)
        val viewModel = viewModelWith(source)
        viewModel.openBySourceUrl(1L, "/manga/solo")
        advanceUntilIdle()
        assertEquals(AppError.SourceUnavailable, viewModel.uiState.value.error)
        viewModel.onRetry()
        advanceUntilIdle()
        // Повтор при той же ошибке снова даёт ошибку (счётчик вызовов вырос).
        assertEquals(AppError.SourceUnavailable, viewModel.uiState.value.error)
        assertEquals(2, source.detailsCalls)
    }
}
