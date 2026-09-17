package com.manhwaread.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterDaoTest : DaoTestBase() {
    @Test
    fun `refresh inserts new chapters`() = runBlocking {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        db.chapterDao().refreshChapters(
            listOf(
                DatabaseFixtures.chapter(mangaId, url = "/ch/1", chapterNumber = 1f),
                DatabaseFixtures.chapter(mangaId, url = "/ch/2", chapterNumber = 2f),
            ),
        )
        assertEquals(2, db.chapterDao().countForManga(mangaId))
    }

    @Test
    fun `refresh preserves read flag and updates metadata`() = runBlocking {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        val dao = db.chapterDao()
        dao.refreshChapters(listOf(DatabaseFixtures.chapter(mangaId, name = "Chapter 1")))
        val chapterId = dao.allForManga(mangaId).single().id
        dao.setRead(chapterId, true)
        dao.refreshChapters(listOf(DatabaseFixtures.chapter(mangaId, name = "Chapter 1 (v2)")))
        val refreshed = dao.allForManga(mangaId).single()
        assertEquals("Chapter 1 (v2)", refreshed.name)
        assertTrue(refreshed.read)
        assertEquals(chapterId, refreshed.id)
    }

    @Test
    fun `observe sorts by season then number`() = runBlocking {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        val dao = db.chapterDao()
        dao.refreshChapters(
            listOf(
                DatabaseFixtures.chapter(mangaId, url = "/s2c1", season = 2, chapterNumber = 1f),
                DatabaseFixtures.chapter(mangaId, url = "/s1c2", season = 1, chapterNumber = 2f),
                DatabaseFixtures.chapter(mangaId, url = "/s1c1", season = 1, chapterNumber = 1f),
            ),
        )
        val urls = withTimeout(DAO_TIMEOUT_MS) { dao.observeForManga(mangaId).first() }.map { it.url }
        assertEquals(listOf("/s1c1", "/s1c2", "/s2c1"), urls)
    }

    @Test
    fun `delete for manga removes all its chapters only`() = runBlocking {
        val first = db.mangaDao().upsert(DatabaseFixtures.manga(url = "/manga/a"))
        val second = db.mangaDao().upsert(DatabaseFixtures.manga(url = "/manga/b"))
        val dao = db.chapterDao()
        dao.refreshChapters(
            listOf(
                DatabaseFixtures.chapter(first, url = "/a/1"),
                DatabaseFixtures.chapter(second, url = "/b/1"),
            ),
        )
        dao.deleteForManga(first)
        assertEquals(0, dao.countForManga(first))
        assertEquals(1, dao.countForManga(second))
    }
}
