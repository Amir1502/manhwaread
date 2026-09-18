package com.manhwaread.feature.browse

import com.manhwaread.core.common.AppError
import com.manhwaread.source.api.Filter
import com.manhwaread.source.api.InMemorySourceRegistry
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BrowseViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeSource(
        override val id: Long,
        override val name: String = "Fake",
        private val popularPages: Map<Int, MangasPage> = emptyMap(),
        private val latestPages: Map<Int, MangasPage> = emptyMap(),
        private val searchPages: Map<Int, MangasPage> = emptyMap(),
    ) : Source {
        var failure: AppError? = null
        var popularCalls = 0
            private set
        var latestCalls = 0
            private set
        var lastQuery: String? = null
            private set

        override val lang: String = "en"
        override val baseUrl: String = "https://fake.test"
        override val supportsSearch: Boolean = true
        override val isNsfw: Boolean = false

        override suspend fun getPopular(page: Int): MangasPage {
            popularCalls++
            return pageOrFailure(popularPages[page])
        }

        override suspend fun getLatest(page: Int): MangasPage {
            latestCalls++
            return pageOrFailure(latestPages[page])
        }

        override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage {
            lastQuery = query
            return pageOrFailure(searchPages[page])
        }

        override suspend fun getDetails(manga: SManga): SManga = manga

        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        private fun pageOrFailure(page: MangasPage?): MangasPage {
            failure?.let { error -> throw SourceException(error) }
            return page ?: MangasPage(mangas = emptyList(), hasNextPage = false)
        }
    }

    private fun manga(url: String, sourceId: Long = 1L) =
        SManga(url = url, title = "title-$url", sourceId = sourceId)

    private fun page(vararg urls: String, hasNextPage: Boolean = false, sourceId: Long = 1L) =
        MangasPage(mangas = urls.map { manga(it, sourceId) }, hasNextPage = hasNextPage)

    private fun viewModelWith(vararg sources: Source): Pair<BrowseViewModel, InMemorySourceRegistry> {
        val registry = InMemorySourceRegistry()
        sources.forEach { source -> registry.register(source) }
        return BrowseViewModel(registry) to registry
    }

    @Test
    fun `init publishes sources from registry`() = runTest {
        val (viewModel, _) = viewModelWith(
            FakeSource(id = 1L, name = "A"),
            FakeSource(id = 2L, name = "B"),
        )
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.sources.map { it.id })
    }

    @Test
    fun `selecting source loads popular first page`() = runTest {
        val source = FakeSource(
            id = 1L,
            popularPages = mapOf(1 to page("/a", "/b", hasNextPage = true)),
        )
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(source, state.selectedSource)
        assertEquals(2, state.listing.items.size)
        assertEquals(1, state.listing.page)
        assertTrue(state.listing.hasNextPage)
        assertFalse(state.listing.isLoading)
        assertEquals(1, source.popularCalls)
    }

    @Test
    fun `load more appends next page`() = runTest {
        val source = FakeSource(
            id = 1L,
            popularPages = mapOf(
                1 to page("/a", hasNextPage = true),
                2 to page("/b", "/c", hasNextPage = false),
            ),
        )
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()
        val listing = viewModel.uiState.value.listing
        assertEquals(listOf("/a", "/b", "/c"), listing.items.map { it.url })
        assertEquals(2, listing.page)
        assertFalse(listing.hasNextPage)
    }

    @Test
    fun `load more ignored without next page`() = runTest {
        val source = FakeSource(id = 1L, popularPages = mapOf(1 to page("/a")))
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()
        assertEquals(1, source.popularCalls)
    }

    @Test
    fun `mode switch reloads with latest`() = runTest {
        val source = FakeSource(
            id = 1L,
            popularPages = mapOf(1 to page("/popular")),
            latestPages = mapOf(1 to page("/latest")),
        )
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        viewModel.onModeSelected(BrowseListingMode.LATEST)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(BrowseListingMode.LATEST, state.mode)
        assertEquals(listOf("/latest"), state.listing.items.map { it.url })
        assertEquals(1, source.latestCalls)
    }

    @Test
    fun `search mode is not selectable via chip`() = runTest {
        val source = FakeSource(id = 1L, popularPages = mapOf(1 to page("/a")))
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        viewModel.onModeSelected(BrowseListingMode.SEARCH)
        advanceUntilIdle()
        assertEquals(BrowseListingMode.POPULAR, viewModel.uiState.value.mode)
    }

    @Test
    fun `search submit queries trimmed text`() = runTest {
        val source = FakeSource(
            id = 1L,
            popularPages = mapOf(1 to page("/a")),
            searchPages = mapOf(1 to page("/found")),
        )
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        viewModel.onQueryChange("  solo  ")
        viewModel.onSearchSubmit()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("solo", source.lastQuery)
        assertEquals(BrowseListingMode.SEARCH, state.mode)
        assertEquals(listOf("/found"), state.listing.items.map { it.url })
    }

    @Test
    fun `blank search submit falls back to popular`() = runTest {
        val source = FakeSource(id = 1L, popularPages = mapOf(1 to page("/a")))
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        viewModel.onQueryChange("   ")
        viewModel.onSearchSubmit()
        advanceUntilIdle()
        assertEquals(BrowseListingMode.POPULAR, viewModel.uiState.value.mode)
        assertEquals(2, source.popularCalls)
    }

    @Test
    fun `source error surfaces AppError and clears loading`() = runTest {
        val source = FakeSource(id = 1L)
        source.failure = AppError.SourceLayoutChanged
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(AppError.SourceLayoutChanged, state.error)
        assertFalse(state.listing.isLoading)
        assertTrue(state.listing.items.isEmpty())
    }

    @Test
    fun `retry reloads after error is gone`() = runTest {
        val source = FakeSource(id = 1L, popularPages = mapOf(1 to page("/a")))
        source.failure = AppError.SourceUnavailable
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        assertEquals(AppError.SourceUnavailable, viewModel.uiState.value.error)
        source.failure = null
        viewModel.onRetry()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("/a"), viewModel.uiState.value.listing.items.map { it.url })
    }

    @Test
    fun `back to sources clears selection and listing`() = runTest {
        val source = FakeSource(id = 1L, popularPages = mapOf(1 to page("/a")))
        val (viewModel, _) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        viewModel.onBackToSources()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertNull(state.selectedSource)
        assertTrue(state.listing.items.isEmpty())
        assertFalse(state.isSourceSelected)
    }

    @Test
    fun `unregistered source drops selection`() = runTest {
        val source = FakeSource(id = 1L, popularPages = mapOf(1 to page("/a")))
        val (viewModel, registry) = viewModelWith(source)
        advanceUntilIdle()
        viewModel.onSourceSelected(source)
        advanceUntilIdle()
        registry.unregister(source.id)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedSource)
        assertTrue(viewModel.uiState.value.sources.isEmpty())
    }
}
