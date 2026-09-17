package com.manhwaread.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDaoTest : DaoTestBase() {
    @Test
    fun `upsert overwrites progress for chapter`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        val dao = db.historyDao()
        dao.upsert(HistoryEntity(mangaId, chapterId, pageIndex = 5, scrollOffsetPx = 10, lastReadMs = 100L))
        dao.upsert(HistoryEntity(mangaId, chapterId, pageIndex = 7, scrollOffsetPx = 0, lastReadMs = 200L))
        val entry = dao.forChapter(chapterId)
        assertEquals(7, entry?.pageIndex)
        assertEquals(0, entry?.scrollOffsetPx)
        assertEquals(200L, entry?.lastReadMs)
    }

    @Test
    fun `observe recent ordered by lastRead desc with limit`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        val secondChapter = db.chapterDao().insertIgnore(DatabaseFixtures.chapter(mangaId, url = "/ch/2"))
        val dao = db.historyDao()
        dao.upsert(HistoryEntity(mangaId, chapterId, lastReadMs = 100L))
        dao.upsert(HistoryEntity(mangaId, secondChapter, lastReadMs = 300L))
        val recent = withTimeout(DAO_TIMEOUT_MS) { dao.observeRecent(limit = 1).first() }
        assertEquals(listOf(secondChapter), recent.map { it.chapterId })
    }

    @Test
    fun `delete for manga removes entries`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        db.historyDao().upsert(HistoryEntity(mangaId, chapterId, lastReadMs = 1L))
        db.historyDao().deleteForManga(mangaId)
        assertNull(db.historyDao().forChapter(chapterId))
    }

    @Test
    fun `chapter deletion cascades history`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        db.historyDao().upsert(HistoryEntity(mangaId, chapterId))
        db.chapterDao().deleteForManga(mangaId)
        assertNull(db.historyDao().forChapter(chapterId))
    }

    @Test
    fun `clear removes everything`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        db.historyDao().upsert(HistoryEntity(mangaId, chapterId, lastReadMs = 1L))
        db.historyDao().clear()
        assertTrue(withTimeout(DAO_TIMEOUT_MS) { db.historyDao().observeRecent(10).first() }.isEmpty())
    }
}
