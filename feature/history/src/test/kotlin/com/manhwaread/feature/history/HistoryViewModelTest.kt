package com.manhwaread.feature.history

import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.source.api.MangaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class HistoryViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeHistoryDao : HistoryDao {
        val rows = mutableMapOf<Long, HistoryEntity>()
        private val recentFlow = MutableStateFlow<List<HistoryEntity>>(emptyList())
        var lastLimit: Int? = null
            private set

        override suspend fun upsert(entry: HistoryEntity) {
            rows[entry.chapterId] = entry
            publish()
        }

        override suspend fun forChapter(chapterId: Long): HistoryEntity? = rows[chapterId]

        override fun observeRecent(limit: Int): Flow<List<HistoryEntity>> {
            lastLimit = limit
            return recentFlow.map { all -> all.take(limit) }
        }

        override suspend fun deleteForManga(mangaId: Long) {
            val keys = rows.filterValues { it.mangaId == mangaId }.keys
            keys.forEach { rows.remove(it) }
            publish()
        }

        override suspend fun clear() {
            rows.clear()
            publish()
        }

        private fun publish() {
            recentFlow.value = rows.values.sortedByDescending { it.lastReadMs }
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

    private val historyDao = FakeHistoryDao()
    private val mangaDao = FakeMangaDao()
    private val chapterDao = FakeChapterDao()

    private fun seedManga(id: Long, title: String) {
        mangaDao.rows[id] = MangaEntity(
            id = id,
            sourceId = 1L,
            url = "/manga/$id",
            title = title,
            genres = emptyList(),
            status = MangaStatus.UNKNOWN,
            thumbnailUrl = "https://cdn.example/$id.jpg",
            nsfw = false,
            inLibrary = false,
            addedAtMs = 0L,
        )
    }

    private fun seedChapter(id: Long, mangaId: Long, name: String) {
        chapterDao.rows[id] = ChapterEntity(
            id = id,
            mangaId = mangaId,
            url = "/chapter/$id",
            name = name,
            season = 1,
            chapterNumber = id.toFloat(),
            dateUploadMs = 0L,
            scanlator = null,
            read = false,
        )
    }

    private suspend fun seedHistory(chapterId: Long, mangaId: Long, lastReadMs: Long, pageIndex: Int = 0) {
        historyDao.upsert(
            HistoryEntity(
                chapterId = chapterId,
                mangaId = mangaId,
                pageIndex = pageIndex,
                scrollOffsetPx = 0,
                lastReadMs = lastReadMs,
            ),
        )
    }

    @Test
    fun `init joins entries with manga and chapter`() = runTest {
        seedManga(1L, "Solo Leveling")
        seedChapter(10L, 1L, "Chapter 5")
        seedHistory(chapterId = 10L, mangaId = 1L, lastReadMs = 500L, pageIndex = 3)
        val viewModel = HistoryViewModel(historyDao, mangaDao, chapterDao)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(100, historyDao.lastLimit)
        assertEquals(1, state.items.size)
        val item = state.items[0]
        assertEquals(1L, item.mangaId)
        assertEquals("Solo Leveling", item.mangaTitle)
        assertEquals("https://cdn.example/1.jpg", item.mangaThumbnailUrl)
        assertEquals(10L, item.chapterId)
        assertEquals("Chapter 5", item.chapterName)
        assertEquals(500L, item.lastReadMs)
        assertEquals(3, item.pageIndex)
    }

    @Test
    fun `entries with missing rows are skipped`() = runTest {
        seedManga(1L, "Solo Leveling")
        seedHistory(chapterId = 999L, mangaId = 1L, lastReadMs = 100L)
        val viewModel = HistoryViewModel(historyDao, mangaDao, chapterDao)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `items keep dao order by lastRead desc`() = runTest {
        seedManga(1L, "A")
        seedChapter(10L, 1L, "old")
        seedChapter(11L, 1L, "new")
        seedHistory(chapterId = 10L, mangaId = 1L, lastReadMs = 100L)
        seedHistory(chapterId = 11L, mangaId = 1L, lastReadMs = 900L)
        val viewModel = HistoryViewModel(historyDao, mangaDao, chapterDao)
        advanceUntilIdle()
        assertEquals(listOf("new", "old"), viewModel.uiState.value.items.map { it.chapterName })
    }

    @Test
    fun `clear empties list and emits message`() = runTest {
        seedManga(1L, "A")
        seedChapter(10L, 1L, "Chapter 5")
        seedHistory(chapterId = 10L, mangaId = 1L, lastReadMs = 100L)
        val viewModel = HistoryViewModel(historyDao, mangaDao, chapterDao)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
        viewModel.onClear()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertEquals(HistoryMessage.Cleared, state.message)
        assertTrue(historyDao.rows.isEmpty())
    }

    @Test
    fun `message cleared after shown`() = runTest {
        val viewModel = HistoryViewModel(historyDao, mangaDao, chapterDao)
        advanceUntilIdle()
        viewModel.onClear()
        advanceUntilIdle()
        assertEquals(HistoryMessage.Cleared, viewModel.uiState.value.message)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `flow updates republish items`() = runTest {
        seedManga(1L, "A")
        seedChapter(10L, 1L, "Chapter 5")
        val viewModel = HistoryViewModel(historyDao, mangaDao, chapterDao)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.items.isEmpty())
        seedHistory(chapterId = 10L, mangaId = 1L, lastReadMs = 100L)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)
    }
}
