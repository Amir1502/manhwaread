package com.manhwaread.app.navigation

import com.manhwaread.app.reader.StreamingChapterLoader
import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.database.BookmarkDao
import com.manhwaread.core.database.BookmarkEntity
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.datastore.ReaderSettingsStore
import com.manhwaread.feature.downloads.queue.ChapterDirs
import com.manhwaread.feature.reader.ReaderMode
import com.manhwaread.source.api.MangaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderNavViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { mangaDao.findById(any()) } returns null
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @TempDir
    lateinit var tempDir: File

    private class FakeHistoryDao : HistoryDao {
        val entries = mutableMapOf<Long, HistoryEntity>()

        override suspend fun upsert(entry: HistoryEntity) {
            entries[entry.chapterId] = entry
        }

        override suspend fun forChapter(chapterId: Long): HistoryEntity? = entries[chapterId]

        override fun observeRecent(limit: Int): Flow<List<HistoryEntity>> =
            MutableStateFlow(entries.values.sortedByDescending { it.lastReadMs }.take(limit))

        override suspend fun deleteForManga(mangaId: Long) {
            entries.entries.removeAll { entry -> entry.value.mangaId == mangaId }
        }

        override suspend fun clear() {
            entries.clear()
        }
    }

    private class FakeReaderChapterDao : ChapterDao {
        val readMarks = mutableListOf<Pair<Long, Boolean>>()
        var chapters: List<ChapterEntity> = emptyList()

        override suspend fun insertIgnore(chapter: ChapterEntity): Long = chapter.id

        override suspend fun updateMetadata(
            mangaId: Long,
            url: String,
            name: String,
            season: Int,
            chapterNumber: Float,
            dateUploadMs: Long,
            scanlator: String?,
        ) = Unit

        override fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun allForManga(mangaId: Long): List<ChapterEntity> =
            chapters.filter { chapter -> chapter.mangaId == mangaId }

        override suspend fun findById(id: Long): ChapterEntity? = null

        override suspend fun setRead(id: Long, read: Boolean) {
            readMarks += id to read
        }

        override suspend fun countForManga(mangaId: Long): Int = 0

        override suspend fun deleteForManga(mangaId: Long) = Unit
    }

    // Закладки в памяти: наблюдение отражает каждое изменение, как Room Flow.
    private class FakeBookmarkDao : BookmarkDao {
        val bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun upsert(bookmark: BookmarkEntity): Long {
            val id = if (bookmark.id == 0L) nextId++ else bookmark.id
            bookmarks.value = bookmarks.value.filterNot { existing -> existing.id == id } + bookmark.copy(id = id)
            return id
        }

        override fun observeForChapter(chapterId: Long): Flow<List<BookmarkEntity>> =
            bookmarks.map { list -> list.filter { bookmark -> bookmark.chapterId == chapterId }.sortedBy { it.pageIndex } }

        override suspend fun deleteById(id: Long) {
            bookmarks.value = bookmarks.value.filterNot { bookmark -> bookmark.id == id }
        }
    }

    private class FakeReaderSettingsStore : ReaderSettingsStore {
        val modes = mutableMapOf<Long, String>()

        override fun readerMode(mangaId: Long): Flow<String?> = flowOf(modes[mangaId])

        override suspend fun setReaderMode(mangaId: Long, mode: String) {
            modes[mangaId] = mode
        }
    }

    private val historyDao = FakeHistoryDao()
    private val chapterDao = FakeReaderChapterDao()
    private val streamingLoader = mockk<StreamingChapterLoader>()
    private val mangaDao = mockk<MangaDao>()
    private val bookmarkDao = FakeBookmarkDao()
    private val readerSettings = FakeReaderSettingsStore()

    private fun viewModel(): ReaderNavViewModel =
        ReaderNavViewModel(
            ChapterDirs(tempDir),
            historyDao,
            chapterDao,
            streamingLoader,
            mangaDao,
            bookmarkDao,
            readerSettings,
        )

    private fun manga(type: MangaType?, titleRu: String? = null) =
        MangaEntity(id = 1L, sourceId = 7L, url = "/manga/1", title = "Sword King", titleRu = titleRu, type = type)

    // Каталог скачанной главы формата FileChapterLoader: метаданные + страница.
    private fun prepareOfflineChapter(chapterId: Long = 10L): File {
        val dir = File(tempDir, "chapter_$chapterId")
        dir.mkdirs()
        File(dir, "page001.png").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "chapter.json").writeText("""{"title":"offline","bubbles":[]}""")
        return dir
    }

    @Test
    fun `open resolves offline chapter dir and restores page from history`() = runTest {
        val offlineDir = prepareOfflineChapter()
        historyDao.entries[10L] = HistoryEntity(mangaId = 1L, chapterId = 10L, pageIndex = 5, lastReadMs = 100L)
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertFalse(state.isStreaming)
        assertEquals(5, state.initialPageIndex)
        assertEquals(offlineDir, state.chapterDir)
    }

    @Test
    fun `open without history starts at page zero`() = runTest {
        prepareOfflineChapter()
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertEquals(0, state.initialPageIndex)
    }

    @Test
    fun `incomplete offline dir triggers streaming instead of open`() = runTest {
        // Каталог есть, но страниц нет (или нет chapter.json) — глава не готова офлайн.
        File(tempDir, "chapter_10").mkdirs()
        val streamDir = File(tempDir, "stream/chapter_10")
        coEvery { streamingLoader.ensureStreamed(eq(10L), any()) } coAnswers {
            DomainResult.success(streamDir)
        }
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertEquals(streamDir, state.chapterDir)
    }

    @Test
    fun `open streams chapter and reports progress`() = runTest {
        val streamDir = File(tempDir, "stream/chapter_10")
        coEvery { streamingLoader.ensureStreamed(eq(10L), any()) } coAnswers {
            val progress = secondArg<(Int, Int) -> Unit>()
            progress(1, 2)
            progress(2, 2)
            DomainResult.success(streamDir)
        }
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertFalse(state.isStreaming)
        assertNull(state.streamError)
        assertEquals(streamDir, state.chapterDir)
        assertEquals(2, state.streamDone)
        assertEquals(2, state.streamTotal)
    }

    @Test
    fun `stream failure sets error and retry recovers`() = runTest {
        coEvery { streamingLoader.ensureStreamed(eq(10L), any()) } coAnswers {
            DomainResult.failure(AppError.SourceLayoutChanged)
        }
        val viewModel = viewModel()
        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertFalse(failed.isOpen)
        assertFalse(failed.isStreaming)
        assertEquals(AppError.SourceLayoutChanged, failed.streamError)

        val streamDir = File(tempDir, "stream/chapter_10")
        coEvery { streamingLoader.ensureStreamed(eq(10L), any()) } coAnswers {
            DomainResult.success(streamDir)
        }
        viewModel.retryStream()
        advanceUntilIdle()

        val recovered = viewModel.uiState.value
        assertTrue(recovered.isOpen)
        assertNull(recovered.streamError)
        assertEquals(streamDir, recovered.chapterDir)
    }

    @Test
    fun `page changed records history and marks chapter read once`() = runTest {
        prepareOfflineChapter()
        val viewModel = viewModel()
        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        viewModel.onPageChanged(3)
        viewModel.onPageChanged(4)
        advanceUntilIdle()

        val entry = historyDao.entries[10L]
        assertEquals(4, entry?.pageIndex)
        assertEquals(1L, entry?.mangaId)
        assertTrue((entry?.lastReadMs ?: 0L) > 0L)
        assertEquals(listOf(10L to true), chapterDao.readMarks)
    }

    @Test
    fun `page changed before open is ignored`() = runTest {
        val viewModel = viewModel()

        viewModel.onPageChanged(2)
        advanceUntilIdle()

        assertTrue(historyDao.entries.isEmpty())
        assertTrue(chapterDao.readMarks.isEmpty())
        assertFalse(viewModel.uiState.value.isOpen)
        assertNull(viewModel.uiState.value.chapterDir)
    }

    @Test
    fun `navigation lists chapters in reading order with neighbours and russian title`() = runTest {
        prepareOfflineChapter(chapterId = 11L)
        coEvery { mangaDao.findById(1L) } returns manga(type = MangaType.MANHWA, titleRu = "Король меча")
        chapterDao.chapters = listOf(
            ChapterEntity(id = 12L, mangaId = 1L, url = "/c3", name = "Глава 3", chapterNumber = 3f),
            ChapterEntity(id = 10L, mangaId = 1L, url = "/c1", name = "Глава 1", chapterNumber = 1f, read = true),
            ChapterEntity(id = 11L, mangaId = 1L, url = "/c2", name = "Глава 2", chapterNumber = 2f),
        )
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 11L)
        advanceUntilIdle()

        val navigation = viewModel.uiState.value.navigation
        assertEquals("Король меча", navigation.mangaTitle)
        assertEquals(listOf(10L, 11L, 12L), navigation.chapters.map { item -> item.chapterId })
        assertTrue(navigation.chapters.first().isRead)
        assertEquals(10L, navigation.previousChapter?.chapterId)
        assertEquals(12L, navigation.nextChapter?.chapterId)
        assertEquals(ReaderMode.WEBTOON, navigation.preferredMode)
    }

    @Test
    fun `manga defaults to right to left and saved mode wins over default`() = runTest {
        prepareOfflineChapter()
        coEvery { mangaDao.findById(1L) } returns manga(type = MangaType.MANGA)
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()
        assertEquals(ReaderMode.RTL, viewModel.uiState.value.navigation.preferredMode)
        assertEquals("Sword King", viewModel.uiState.value.navigation.mangaTitle)

        readerSettings.modes[1L] = ReaderMode.VERTICAL.name
        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()
        assertEquals(ReaderMode.VERTICAL, viewModel.uiState.value.navigation.preferredMode)
    }

    @Test
    fun `mode change is saved for the title`() = runTest {
        prepareOfflineChapter()
        val viewModel = viewModel()
        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        viewModel.onReaderModeChanged(ReaderMode.LTR)
        advanceUntilIdle()

        assertEquals("LTR", readerSettings.modes[1L])
        assertEquals(ReaderMode.LTR, viewModel.uiState.value.navigation.preferredMode)
    }

    @Test
    fun `toggle bookmark adds then removes page bookmark`() = runTest {
        prepareOfflineChapter()
        val viewModel = viewModel()
        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        viewModel.toggleBookmark(4)
        advanceUntilIdle()
        assertEquals(setOf(4), viewModel.uiState.value.navigation.bookmarkedPages)
        val saved = bookmarkDao.bookmarks.value.single()
        assertEquals(1L, saved.mangaId)
        assertEquals(10L, saved.chapterId)

        viewModel.toggleBookmark(4)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigation.bookmarkedPages.isEmpty())
        assertTrue(bookmarkDao.bookmarks.value.isEmpty())
    }

    @Test
    fun `navigation load failure does not block opening chapter`() = runTest {
        prepareOfflineChapter()
        coEvery { mangaDao.findById(any()) } throws IllegalStateException("db closed")
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertEquals(10L, state.navigation.currentChapterId)
        assertNull(state.navigation.preferredMode)
        assertTrue(state.navigation.chapters.isEmpty())
    }
}
