package com.manhwaread.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDaoTest : DaoTestBase() {
    @Test
    fun `all ordered by sortOrder then name`() = runBlocking {
        val dao = db.categoryDao()
        dao.upsert(CategoryEntity(name = "Beta", sortOrder = 1))
        dao.upsert(CategoryEntity(name = "Alpha", sortOrder = 0))
        dao.upsert(CategoryEntity(name = "B", sortOrder = 1))
        assertEquals(listOf("Alpha", "B", "Beta"), dao.all().map { it.name })
    }

    @Test
    fun `system categories survive deleteIfNotSystem`() = runBlocking {
        val dao = db.categoryDao()
        val systemId = dao.upsert(CategoryEntity(name = "Все", isSystem = true))
        val userId = dao.upsert(CategoryEntity(name = "Читаю"))
        dao.deleteIfNotSystem(systemId)
        dao.deleteIfNotSystem(userId)
        assertEquals(listOf("Все"), dao.all().map { it.name })
    }

    @Test
    fun `manga category join roundtrip and duplicate ignored`() = runBlocking {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        val dao = db.categoryDao()
        val categoryId = dao.upsert(CategoryEntity(name = "Читаю"))
        dao.addMangaToCategory(MangaCategoryCrossRef(mangaId, categoryId))
        dao.addMangaToCategory(MangaCategoryCrossRef(mangaId, categoryId))
        val mangas = withTimeout(DAO_TIMEOUT_MS) { dao.observeMangaForCategory(categoryId).first() }
        assertEquals(listOf(mangaId), mangas.map { it.id })
        dao.removeMangaFromCategory(mangaId, categoryId)
        assertTrue(withTimeout(DAO_TIMEOUT_MS) { dao.observeMangaForCategory(categoryId).first() }.isEmpty())
    }

    @Test
    fun `deleting category cascades cross refs`() = runBlocking {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        val dao = db.categoryDao()
        val categoryId = dao.upsert(CategoryEntity(name = "Читаю"))
        dao.addMangaToCategory(MangaCategoryCrossRef(mangaId, categoryId))
        dao.deleteIfNotSystem(categoryId)
        assertTrue(dao.all().isEmpty())
        assertTrue(withTimeout(DAO_TIMEOUT_MS) { dao.observeMangaForCategory(categoryId).first() }.isEmpty())
    }
}
