package com.manhwaread.core.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryStoresTest {
    @Test
    fun `job store next picks highest priority then fifo`() = runBlocking {
        val store = InMemoryChapterJobStore()
        store.enqueue(PipelineFixtures.testJob("a", priority = 0, createdAt = 1L))
        store.enqueue(PipelineFixtures.testJob("b", priority = 5, createdAt = 2L))
        store.enqueue(PipelineFixtures.testJob("c", priority = 5, createdAt = 0L))
        assertEquals("c", store.next()?.id)
    }

    @Test
    fun `job store next skips non-queued jobs`() = runBlocking {
        val store = InMemoryChapterJobStore()
        store.enqueue(PipelineFixtures.testJob("a", state = JobState(status = StageStatus.DOWNLOADING)))
        assertNull(store.next())
    }

    @Test
    fun `job store update replaces state and all lists everything`() = runBlocking {
        val store = InMemoryChapterJobStore()
        store.enqueue(PipelineFixtures.testJob("a"))
        store.enqueue(PipelineFixtures.testJob("b"))
        store.update("a", JobState(status = StageStatus.DONE))
        assertEquals(StageStatus.DONE, store.findById("a")?.state?.status)
        assertNull(store.findById("a")?.state?.lastError)
        assertEquals(2, store.all().size)
    }

    @Test
    fun `job store update of unknown id is no-op`() = runBlocking {
        val store = InMemoryChapterJobStore()
        store.update("ghost", JobState(status = StageStatus.DONE))
        assertNull(store.findById("ghost"))
    }

    @Test
    fun `overlay store saves loads and clears`() = runBlocking {
        val store = InMemoryOverlayStore()
        assertTrue(store.loadChapter(999L).isEmpty())
        store.saveChapter(100L, listOf(PipelineFixtures.overlay("b1")))
        assertEquals(1, store.loadChapter(100L).size)
        store.clearChapter(100L)
        assertTrue(store.loadChapter(100L).isEmpty())
    }

    @Test
    fun `segment store roundtrips segments`() = runBlocking {
        val store = InMemorySegmentStore()
        assertTrue(store.loadSegments(100L).isEmpty())
        store.saveSegments(100L, listOf(PipelineFixtures.segment("s1")))
        assertEquals(listOf("s1"), store.loadSegments(100L).map { it.id })
    }

    @Test
    fun `page store roundtrips bytes`() = runBlocking {
        val store = InMemoryPageStore()
        assertNull(store.loadPage(100L, 0))
        store.savePage(100L, 0, byteArrayOf(1, 2, 3))
        assertEquals(3, store.loadPage(100L, 0)?.size)
        assertEquals(1.toByte(), store.loadPage(100L, 0)?.get(0))
        assertNull(store.loadPage(100L, 5))
    }
}
