package com.manhwaread.source.mangamir

import com.manhwaread.core.network.HttpClientFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

// Живой smoke-тест MangaMir (Этап 9, финальный QA): реальный сайт, без моков,
// тот же OkHttp-стек, что в приложении. В CI выключен — сеть и вёрстка сайта
// нестабильны. Ручной запуск:
//   MANGAMIR_LIVE=1 ./gradlew :source:mangamir:test --tests '*MangaMirLiveSmokeTest*' --rerun --no-configuration-cache
@EnabledIfEnvironmentVariable(named = "MANGAMIR_LIVE", matches = "1")
class MangaMirLiveSmokeTest {
    private val source = MangaMirSource(HttpClientFactory.create())

    @Test
    fun `popular and latest catalogs return titles with next page`() = runBlocking {
        val popular = source.getPopular(page = 1)
        assertTrue(popular.mangas.size >= MIN_CATALOG_SIZE) { "popular=${popular.mangas.size}" }
        assertTrue(popular.hasNextPage)
        popular.mangas.forEach { manga ->
            assertTrue(manga.url.contains("/manga/")) { "url=${manga.url}" }
            assertFalse(manga.title.isBlank())
        }

        val latest = source.getLatest(page = 1)
        assertTrue(latest.mangas.size >= MIN_CATALOG_SIZE) { "latest=${latest.mangas.size}" }
    }

    @Test
    fun `search by russian word finds sword king`() = runBlocking {
        val result = source.search(query = "король", filters = emptyList(), page = 1)
        assertTrue(result.mangas.any { manga -> manga.url.endsWith(SWORD_KING_SLUG) }) {
            "results=${result.mangas.map { manga -> manga.url }}"
        }
    }

    @Test
    fun `sword king details expose full dated chapter list and readable pages`() = runBlocking {
        val swordKing = source.search(query = "Король меча", filters = emptyList(), page = 1).mangas
            .first { manga -> manga.url.endsWith(SWORD_KING_SLUG) }

        val details = source.getDetails(swordKing)
        assertFalse(details.title.isBlank())
        assertFalse(details.thumbnailUrl.isNullOrBlank())
        assertTrue(details.genres.isNotEmpty())

        val chapters = source.getChapterList(swordKing)
        assertTrue(chapters.size >= MIN_SWORD_KING_CHAPTERS) { "chapters=${chapters.size}" }
        assertEquals(chapters.size, chapters.map { chapter -> chapter.url }.toSet().size)
        assertTrue(chapters.all { chapter -> chapter.dateUpload > 0L }) {
            "undated=${chapters.filter { chapter -> chapter.dateUpload <= 0L }.take(UNDATED_SAMPLE).map { it.name }}"
        }
        assertTrue(chapters.all { chapter -> chapter.chapterNumber >= 0f })

        val pages = source.getPageList(chapters.first())
        assertTrue(pages.isNotEmpty())
        assertEquals(pages.indices.toList(), pages.map { page -> page.index })
        assertTrue(pages.all { page -> page.imageUrl?.startsWith("https://") == true })
    }

    private companion object {
        const val SWORD_KING_SLUG = "/manga/korol-mecha"
        const val MIN_CATALOG_SIZE = 10
        const val MIN_SWORD_KING_CHAPTERS = 300
        const val UNDATED_SAMPLE = 5
    }
}
