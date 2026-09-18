package com.manhwaread.feature.library

import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.source.api.MangaStatus
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
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
        private val libraryFlow = MutableStateFlow<List<MangaEntity>>(emptyList())
        private var nextId = 1L

        fun seed(manga: MangaEntity) {
            val id = if (manga.id == 0L) nextId++ else manga.id
            rows[id] = manga.copy(id = id)
            publish()
        }

        override suspend fun upsert(manga: MangaEntity): Long {
            val id = if (manga.id == 0L) nextId++ else manga.id
            rows[id] = manga.copy(id = id)
            publish()
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

        override fun observeLibrary(): Flow<List<MangaEntity>> = libraryFlow

        override suspend fun searchInLibrary(query: String): List<MangaEntity> =
            rows.values.filter { it.inLibrary && it.title.contains(query, ignoreCase = true) }

        override suspend fun setInLibrary(id: Long, inLibrary: Boolean, nowMs: Long) {
            rows[id]?.let { current ->
                rows[id] = current.copy(
                    inLibrary = inLibrary,
                    addedAtMs = if (inLibrary) nowMs else current.addedAtMs,
                )
            }
            publish()
        }

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
            publish()
        }

        private fun publish() {
            libraryFlow.value = rows.values.filter { it.inLibrary }
        }
    }

    private fun manga(id: Long, title: String, inLibrary: Boolean = true) = MangaEntity(
        id = id,
        sourceId = 1L,
        url = "/manga/$id",
        title = title,
        genres = emptyList(),
        status = MangaStatus.UNKNOWN,
        nsfw = false,
        inLibrary = inLibrary,
        addedAtMs = id,
    )

    private val mangaDao = FakeMangaDao()

    @Test
    fun `init publishes library rows`() = runTest {
        mangaDao.seed(manga(id = 1L, title = "Solo Leveling"))
        mangaDao.seed(manga(id = 2L, title = "Omniscient Reader"))
        mangaDao.seed(manga(id = 3L, title = "Not in library", inLibrary = false))
        val viewModel = LibraryViewModel(mangaDao)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.allCount)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
    }

    @Test
    fun `query filters case-insensitively`() = runTest {
        mangaDao.seed(manga(id = 1L, title = "Solo Leveling"))
        mangaDao.seed(manga(id = 2L, title = "Omniscient Reader"))
        val viewModel = LibraryViewModel(mangaDao)
        advanceUntilIdle()
        viewModel.onQueryChange("solo")
        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })
        viewModel.onQueryChange("READER")
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        viewModel.onQueryChange("zzz")
        assertTrue(viewModel.uiState.value.items.isEmpty())
        // allCount не зависит от фильтра.
        assertEquals(2, viewModel.uiState.value.allCount)
    }

    @Test
    fun `query is trimmed before filtering`() = runTest {
        mangaDao.seed(manga(id = 1L, title = "Solo Leveling"))
        val viewModel = LibraryViewModel(mangaDao)
        advanceUntilIdle()
        viewModel.onQueryChange("   ")
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `filter reapplies when library updates`() = runTest {
        mangaDao.seed(manga(id = 1L, title = "Solo Leveling"))
        val viewModel = LibraryViewModel(mangaDao)
        advanceUntilIdle()
        viewModel.onQueryChange("solo")
        mangaDao.seed(manga(id = 2L, title = "Solo Max"))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun `remove clears inLibrary flag and updates list`() = runTest {
        mangaDao.seed(manga(id = 1L, title = "Solo Leveling"))
        mangaDao.seed(manga(id = 2L, title = "Omniscient Reader"))
        val viewModel = LibraryViewModel(mangaDao)
        advanceUntilIdle()
        val target = requireNotNull(viewModel.uiState.value.items.first())
        viewModel.onRemove(target)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals(1, state.allCount)
        assertFalse(requireNotNull(mangaDao.rows[1L]).inLibrary)
        // Строка не удалена физически — история сохраняется.
        assertTrue(mangaDao.rows.containsKey(1L))
    }
}
