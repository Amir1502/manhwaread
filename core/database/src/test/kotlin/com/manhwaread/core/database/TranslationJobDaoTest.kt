package com.manhwaread.core.database

import com.manhwaread.core.common.AppError
import com.manhwaread.core.pipeline.JobState
import com.manhwaread.core.pipeline.StageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationJobDaoTest : DaoTestBase() {
    @Test
    fun `enqueue and all roundtrip domain fields`() = runBlocking {
        val dao = db.translationJobDao()
        val job = DatabaseFixtures.job(
            "j1",
            priority = 3,
            createdAt = 77L,
            state = JobState(status = StageStatus.TRANSLATING, attempts = 2),
        )
        dao.enqueue(job)
        assertEquals(job, dao.all().single())
        assertEquals(job, dao.findById("j1"))
        assertNull(dao.findById("ghost"))
    }

    @Test
    fun `next picks highest priority then fifo`() = runBlocking {
        val dao = db.translationJobDao()
        dao.enqueue(DatabaseFixtures.job("a", priority = 0, createdAt = 1L))
        dao.enqueue(DatabaseFixtures.job("b", priority = 5, createdAt = 2L))
        dao.enqueue(DatabaseFixtures.job("c", priority = 5, createdAt = 0L))
        assertEquals("c", dao.next()?.id)
    }

    @Test
    fun `next skips non-queued jobs`() = runBlocking {
        val dao = db.translationJobDao()
        dao.enqueue(DatabaseFixtures.job("a", state = JobState(status = StageStatus.DOWNLOADING)))
        assertNull(dao.next())
    }

    @Test
    fun `update replaces state including error`() = runBlocking {
        val dao = db.translationJobDao()
        dao.enqueue(DatabaseFixtures.job("a"))
        dao.update("a", JobState(status = StageStatus.FAILED, attempts = 1, lastError = AppError.ProviderAuth))
        val state = dao.findById("a")?.state
        assertNotNull(state)
        assertEquals(StageStatus.FAILED, state?.status)
        assertEquals(1, state?.attempts)
        assertTrue(state?.lastError.toString().contains("ProviderAuth"))
    }

    @Test
    fun `update of unknown id is no-op`() = runBlocking {
        val dao = db.translationJobDao()
        dao.update("ghost", JobState(status = StageStatus.DONE))
        assertTrue(dao.all().isEmpty())
    }
}
