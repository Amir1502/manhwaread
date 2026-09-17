package com.manhwaread.source.api

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InMemorySourceRegistryTest {
    private lateinit var registry: InMemorySourceRegistry

    @BeforeEach
    fun setUp() {
        registry = InMemorySourceRegistry()
    }

    @Test
    fun `register adds source and publishes snapshot`() = runTest {
        val source = FakeSource(id = 1L)

        assertTrue(registry.register(source))
        assertSame(source, registry.get(1L))

        registry.sources.test {
            assertEquals(listOf(source), awaitItem())
        }
    }

    @Test
    fun `duplicate id is rejected and list is unchanged`() = runTest {
        val first = FakeSource(id = 5L)
        val duplicate = FakeSource(id = 5L)
        assertTrue(registry.register(first))

        assertFalse(registry.register(duplicate))

        assertEquals(1, registry.sources.value.size)
        assertSame(first, registry.get(5L))

        registry.sources.test {
            // Повторная регистрация не публикует новый снимок.
            assertEquals(listOf(first), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `unregister removes source and returns false for missing id`() {
        val source = FakeSource(id = 9L)
        registry.register(source)

        assertTrue(registry.unregister(9L))
        assertNull(registry.get(9L))
        assertTrue(registry.sources.value.isEmpty())
        assertFalse(registry.unregister(9L))
    }

    @Test
    fun `registration order is preserved`() {
        val sources = listOf(FakeSource(3L), FakeSource(1L), FakeSource(2L))
        sources.forEach { registry.register(it) }

        assertEquals(listOf(3L, 1L, 2L), registry.sources.value.map { it.id })
    }

    @Test
    fun `get returns null for unknown id`() {
        assertNull(registry.get(42L))
    }

    @Test
    fun `flow emits new snapshot on each register`() = runTest {
        registry.sources.test {
            assertEquals(emptyList<Source>(), awaitItem())
            registry.register(FakeSource(1L))
            assertEquals(1, awaitItem().size)
            registry.register(FakeSource(2L))
            assertEquals(2, awaitItem().size)
            registry.unregister(1L)
            assertEquals(listOf(2L), awaitItem().map { it.id })
        }
    }

    @Test
    fun `concurrent registrations from 100 threads lose nothing`() {
        val threadCount = 100
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(16)
        try {
            repeat(threadCount) { i ->
                executor.execute {
                    try {
                        startLatch.await()
                        if (registry.register(FakeSource(id = i.toLong()))) {
                            successCount.incrementAndGet()
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
            startLatch.countDown()
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "threads did not finish in time")
        } finally {
            executor.shutdownNow()
        }

        assertEquals(threadCount, successCount.get())
        assertEquals(threadCount, registry.sources.value.size)
        assertEquals(threadCount, registry.sources.value.map { it.id }.distinct().size)
    }

    /** Минимальная реализация [Source] для тестов реестра. */
    private class FakeSource(override val id: Long) : Source {
        override val name: String = "fake-$id"
        override val lang: String = "en"
        override val baseUrl: String = "https://example.com/$id"
        override val supportsSearch: Boolean = true
        override val isNsfw: Boolean = false
        override suspend fun getPopular(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)
        override suspend fun getLatest(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)
        override suspend fun search(
            query: String,
            filters: List<Filter>,
            page: Int,
        ): MangasPage = MangasPage(emptyList(), hasNextPage = false)
        override suspend fun getDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }
}
