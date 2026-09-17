package com.manhwaread.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkDaoTest : DaoTestBase() {
    @Test
    fun `bookmarks ordered by page and deletable`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        val dao = db.bookmarkDao()
        val secondPage = dao.upsert(BookmarkEntity(mangaId = mangaId, chapterId = chapterId, pageIndex = 2))
        dao.upsert(BookmarkEntity(mangaId = mangaId, chapterId = chapterId, pageIndex = 0))
        dao.upsert(BookmarkEntity(mangaId = mangaId, chapterId = chapterId, pageIndex = 1, note = "важно"))
        val bookmarks = withTimeout(DAO_TIMEOUT_MS) { dao.observeForChapter(chapterId).first() }
        assertEquals(listOf(0, 1, 2), bookmarks.map { it.pageIndex })
        assertEquals("важно", bookmarks[1].note)
        dao.deleteById(secondPage)
        val afterDelete = withTimeout(DAO_TIMEOUT_MS) { dao.observeForChapter(chapterId).first() }
        assertEquals(listOf(0, 1), afterDelete.map { it.pageIndex })
    }

    @Test
    fun `upsert updates existing bookmark by id`() = runBlocking {
        val (mangaId, chapterId) = insertMangaWithChapter()
        val dao = db.bookmarkDao()
        val id = dao.upsert(BookmarkEntity(mangaId = mangaId, chapterId = chapterId, pageIndex = 1))
        dao.upsert(BookmarkEntity(id = id, mangaId = mangaId, chapterId = chapterId, pageIndex = 5, note = "обновлено"))
        val bookmarks = withTimeout(DAO_TIMEOUT_MS) { dao.observeForChapter(chapterId).first() }
        assertEquals(1, bookmarks.size)
        assertEquals(5, bookmarks.single().pageIndex)
        assertEquals("обновлено", bookmarks.single().note)
    }
}
