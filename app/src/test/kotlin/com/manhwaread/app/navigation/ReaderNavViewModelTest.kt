package com.manhwaread.app.navigation

import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.HistoryEntity
import com.manhwaread.feature.downloads.queue.ChapterDirs
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
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderNavViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
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

        override suspend fun allForManga(mangaId: Long): List<ChapterEntity> = emptyList()

        override suspend fun findById(id: Long): ChapterEntity? = null

        override suspend fun setRead(id: Long, read: Boolean) {
            readMarks += id to read
        }

        override suspend fun countForManga(mangaId: Long): Int = 0

        override suspend fun deleteForManga(mangaId: Long) = Unit
    }

    private val historyDao = FakeHistoryDao()
    private val chapterDao = FakeReaderChapterDao()

    private fun viewModel(): ReaderNavViewModel =
        ReaderNavViewModel(ChapterDirs(tempDir), historyDao, chapterDao)

    @Test
    fun `open resolves chapter dir and restores page from history`() = runTest {
        historyDao.entries[10L] = HistoryEntity(mangaId = 1L, chapterId = 10L, pageIndex = 5, lastReadMs = 100L)
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertEquals(5, state.initialPageIndex)
        assertEquals(File(tempDir, "chapter_10"), state.chapterDir)
    }

    @Test
    fun `open without history starts at page zero`() = runTest {
        val viewModel = viewModel()

        viewModel.open(mangaId = 1L, chapterId = 10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOpen)
        assertEquals(0, state.initialPageIndex)
    }

    @Test
    fun `page changed records history and marks chapter read once`() = runTest {
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
}
