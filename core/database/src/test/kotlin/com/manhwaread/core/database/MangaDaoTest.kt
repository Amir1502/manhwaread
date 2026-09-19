package com.manhwaread.core.database

import com.manhwaread.source.api.MangaStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDaoTest : DaoTestBase() {
    @Test
    fun `insert and find roundtrip preserves fields`() = runBlocking {
        val id = db.mangaDao().upsert(DatabaseFixtures.manga())
        val found = db.mangaDao().findById(id)
        assertNotNull(found)
        assertEquals("Solo Leveling", found!!.title)
        assertEquals(listOf("action", "fantasy"), found.genres)
        assertEquals(MangaStatus.ONGOING, found.status)
        assertEquals("Chugong", found.author)
        assertEquals("https://cdn.example/cover.jpg", found.thumbnailUrl)
    }

    @Test
    fun `upsert on same source url updates row keeping id`() = runBlocking {
        val dao = db.mangaDao()
        val id = dao.upsertBySourceUrl(DatabaseFixtures.manga())
        val secondId = dao.upsertBySourceUrl(DatabaseFixtures.manga(title = "Solo Leveling (RAWR)"))
        assertEquals(id, secondId)
        assertEquals(1, dao.searchInLibrary("Solo").size)
        assertEquals("Solo Leveling (RAWR)", dao.findById(id)?.title)
    }

    @Test
    fun `library flow contains only in-library manga`() = runBlocking {
        val dao = db.mangaDao()
        dao.upsert(DatabaseFixtures.manga(url = "/manga/a", addedAtMs = 1L))
        dao.upsert(DatabaseFixtures.manga(url = "/manga/b", inLibrary = false, addedAtMs = 2L))
        val library = withTimeout(DAO_TIMEOUT_MS) { dao.observeLibrary().first() }
        assertEquals(listOf("/manga/a"), library.map { it.url })
    }

    @Test
    fun `search matches title substring case-insensitively`() = runBlocking {
        val dao = db.mangaDao()
        dao.upsert(DatabaseFixtures.manga(url = "/manga/solo", title = "Solo Leveling"))
        dao.upsert(DatabaseFixtures.manga(url = "/manga/omniscient", title = "Omniscient Reader"))
        assertEquals(listOf("Solo Leveling"), dao.searchInLibrary("solo").map { it.title })
        assertEquals(listOf("Solo Leveling"), dao.searchInLibrary("LEVEL").map { it.title })
        assertTrue(dao.searchInLibrary("not-present").isEmpty())
    }

    @Test
    fun `set in library updates flag and timestamp only when adding`() = runBlocking {
        val dao = db.mangaDao()
        val id = dao.upsert(DatabaseFixtures.manga(inLibrary = false, addedAtMs = 0L))
        dao.setInLibrary(id, true, nowMs = 123L)
        val added = dao.findById(id)
        assertTrue(added?.inLibrary == true)
        assertEquals(123L, added?.addedAtMs)
        dao.setInLibrary(id, false, nowMs = 999L)
        val removed = dao.findById(id)
        assertTrue(removed?.inLibrary == false)
        assertEquals(123L, removed?.addedAtMs)
    }

    @Test
    fun `delete cascades to chapters`() = runBlocking {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        db.chapterDao().insertIgnore(DatabaseFixtures.chapter(mangaId))
        assertEquals(1, db.chapterDao().countForManga(mangaId))
        db.mangaDao().deleteById(mangaId)
        assertEquals(0, db.chapterDao().countForManga(mangaId))
    }

    @Test
    fun `set title ru roundtrip stores and clears translation`() = runBlocking {
        val dao = db.mangaDao()
        val id = dao.upsert(DatabaseFixtures.manga())
        // Новая колонка по умолчанию null — старые вызывающие стороны не меняются.
        assertNull(dao.findById(id)?.titleRu)
        dao.setTitleRu(id, "Поднятие уровня в одиночку")
        assertEquals("Поднятие уровня в одиночку", dao.findById(id)?.titleRu)
        dao.setTitleRu(id, null)
        assertNull(dao.findById(id)?.titleRu)
        // UPDATE несуществующей строки — безопасный no-op.
        dao.setTitleRu(999L, "Вселенная")
        assertNull(dao.findById(999L))
    }

    @Test
    fun `upsert roundtrips title ru column`() = runBlocking {
        val dao = db.mangaDao()
        val id = dao.upsert(DatabaseFixtures.manga().copy(titleRu = "Поднятие уровня в одиночку"))
        assertEquals("Поднятие уровня в одиночку", dao.findById(id)?.titleRu)
        // toEntityPreserving переносит titleRu из существующей строки — upsert его сохраняет.
        val updated = dao.upsertBySourceUrl(
            DatabaseFixtures.manga(title = "Solo Leveling (RAWR)").copy(titleRu = "Поднятие уровня (RAWR)"),
        )
        assertEquals(id, updated)
        assertEquals("Поднятие уровня (RAWR)", dao.findById(id)?.titleRu)
        assertEquals("Solo Leveling (RAWR)", dao.findById(id)?.title)
    }

    @Test
    fun `find by source url locates manga`() = runBlocking {
        db.mangaDao().upsert(DatabaseFixtures.manga(sourceId = 7L, url = "/manga/x"))
        assertNull(db.mangaDao().findBySourceUrl(9L, "/manga/x"))
        assertEquals("Solo Leveling", db.mangaDao().findBySourceUrl(7L, "/manga/x")?.title)
    }
}
