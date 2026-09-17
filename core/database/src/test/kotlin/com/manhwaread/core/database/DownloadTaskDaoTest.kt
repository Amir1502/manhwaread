package com.manhwaread.core.database

import com.manhwaread.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskDaoTest : DaoTestBase() {
    @Test
    fun `status filter and progress update`() = runBlocking {
        val dao = db.downloadTaskDao()
        val id = dao.upsert(DownloadTaskEntity(mangaId = 1L, chapterId = 2L, enqueuedAtMs = 10L))
        assertEquals(1, withTimeout(DAO_TIMEOUT_MS) { dao.observeByStatus(DownloadStatus.PENDING).first() }.size)
        dao.updateProgress(id, DownloadStatus.RUNNING, 0.5f)
        assertTrue(withTimeout(DAO_TIMEOUT_MS) { dao.observeByStatus(DownloadStatus.PENDING).first() }.isEmpty())
        val running = withTimeout(DAO_TIMEOUT_MS) { dao.observeByStatus(DownloadStatus.RUNNING).first() }
        assertEquals(0.5f, running.single().progress)
    }

    @Test
    fun `observe by status ordered by enqueue time`() = runBlocking {
        val dao = db.downloadTaskDao()
        dao.upsert(DownloadTaskEntity(mangaId = 1L, chapterId = 3L, enqueuedAtMs = 30L))
        dao.upsert(DownloadTaskEntity(mangaId = 1L, chapterId = 2L, enqueuedAtMs = 20L))
        val pending = withTimeout(DAO_TIMEOUT_MS) { dao.observeByStatus(DownloadStatus.PENDING).first() }
        assertEquals(listOf(2L, 3L), pending.map { it.chapterId })
    }

    @Test
    fun `delete by status removes only matching`() = runBlocking {
        val dao = db.downloadTaskDao()
        dao.upsert(
            DownloadTaskEntity(mangaId = 1L, chapterId = 2L, status = DownloadStatus.COMPLETED, progress = 1f),
        )
        dao.upsert(DownloadTaskEntity(mangaId = 1L, chapterId = 3L))
        dao.deleteByStatus(DownloadStatus.COMPLETED)
        assertEquals(1, withTimeout(DAO_TIMEOUT_MS) { dao.observeByStatus(DownloadStatus.PENDING).first() }.size)
        assertTrue(withTimeout(DAO_TIMEOUT_MS) { dao.observeByStatus(DownloadStatus.COMPLETED).first() }.isEmpty())
    }
}
