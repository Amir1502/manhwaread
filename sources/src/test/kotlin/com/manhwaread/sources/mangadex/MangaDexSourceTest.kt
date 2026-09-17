package com.manhwaread.sources.mangadex

import com.manhwaread.core.source.Filter
import com.manhwaread.core.source.MangaStatus
import com.manhwaread.core.source.SChapter
import com.manhwaread.sources.JsonTransport
import com.manhwaread.sources.SourceFormatException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test

class MangaDexSourceTest {
    private val mangaId = "11111111-1111-1111-1111-111111111111"
    private val chapterId = "22222222-2222-2222-2222-222222222222"

    private fun manga(rating: String = "safe") = buildJsonObject {
        put("id", mangaId)
        putJsonObject("attributes") {
            putJsonObject("title") { put("en", "English title"); put("ru", "Русское название") }
            putJsonObject("description") { put("en", "Description") }
            put("status", "ongoing")
            put("contentRating", rating)
            putJsonArray("tags") {
                addJsonObject { putJsonObject("attributes") { putJsonObject("name") { put("en", "Action") } } }
            }
        }
        putJsonArray("relationships") {
            addJsonObject {
                put("type", "author")
                putJsonObject("attributes") { put("name", "Author") }
            }
            addJsonObject {
                put("type", "cover_art")
                putJsonObject("attributes") { put("fileName", "cover.png") }
            }
        }
    }

    private fun chapter(number: String = "12.5", external: Boolean = false) = buildJsonObject {
        put("id", chapterId)
        putJsonObject("attributes") {
            put("chapter", number)
            put("title", "Arrival")
            put("publishAt", "2025-01-02T00:00:00Z")
            if (external) put("externalUrl", "https://example.org/chapter") else put("externalUrl", JsonNull)
        }
        putJsonArray("relationships") {
            addJsonObject {
                put("type", "scanlation_group")
                putJsonObject("attributes") { put("name", "Group") }
            }
        }
    }

    private fun collection(items: List<JsonObject>, total: Int = items.size) = buildJsonObject {
        put("result", "ok")
        put("total", total)
        put("data", JsonArray(items))
    }

    @Test fun parsesLocalizedMetadataAndCover() {
        val parsed = MangaDexParser.manga(manga())
        assertEquals("Русское название", parsed.title)
        assertEquals("Author", parsed.author)
        assertNull(parsed.artist)
        assertEquals(listOf("Action"), parsed.genres)
        assertEquals(MangaStatus.ONGOING, parsed.status)
        assertEquals("https://uploads.mangadex.org/covers/$mangaId/cover.png.256.jpg", parsed.thumbnailUrl)
        assertFalse(parsed.nsfw)
        assertTrue(parsed.initialized)
    }

    @Test fun parsesDecimalsDatesAndGroups() {
        val parsed = MangaDexParser.chapter(chapter())!!
        assertEquals(12.5f, parsed.chapterNumber, 0f)
        assertEquals(1735776000000L, parsed.dateUpload)
        assertEquals("Group", parsed.scanlator)
        assertNull(MangaDexParser.chapter(chapter(external = true)))
    }

    @Test fun encodesSearchAndUsesRequestedLanguages() = runTest {
        var captured: HttpUrl? = null
        val source = MangaDexSource(JsonTransport { url -> captured = url; collection(listOf(manga()), 70) })
        val result = source.search(" A & B ", listOf(Filter.Languages(setOf("ja"))), 2)
        assertEquals("A & B", captured!!.queryParameter("title"))
        assertEquals("30", captured!!.queryParameter("offset"))
        assertEquals(listOf("ja"), captured!!.queryParameterValues("availableTranslatedLanguage[]"))
        assertEquals(listOf("safe", "suggestive"), captured!!.queryParameterValues("contentRating[]"))
        assertTrue(result.hasNextPage)
        assertEquals(1, result.mangas.size)
    }

    @Test fun hidesAdultAndUnclassifiedContent() = runTest {
        val source = MangaDexSource(JsonTransport { collection(listOf(manga("pornographic"), manga("unknown"))) })
        assertTrue(source.getPopular(1).mangas.isEmpty())
        assertTrue(MangaDexParser.manga(manga("erotica")).nsfw)
    }

    @Test fun resolvesPagesThroughAtHome() = runTest {
        val source = MangaDexSource(JsonTransport { url ->
            assertEquals("/at-home/server/$chapterId", url.encodedPath)
            buildJsonObject {
                put("result", "ok")
                put("baseUrl", "https://uploads.mangadex.org")
                putJsonObject("chapter") {
                    put("hash", "abc123")
                    putJsonArray("data") { add("first.png"); add("second page.jpg") }
                }
            }
        })
        val result = source.getPageList(SChapter(chapterId, "Chapter 1", 0, 1f, null))
        assertEquals(listOf(0, 1), result.map { it.index })
        assertEquals("https://uploads.mangadex.org/data/abc123/second%20page.jpg", result[1].imageUrl)
    }

    @Test fun paginatesChapterFeedBeforeSorting() = runTest {
        val offsets = mutableListOf<String?>()
        val source = MangaDexSource(JsonTransport { url ->
            offsets += url.queryParameter("offset")
            val second = JsonObject(chapter("2").toMutableMap().apply { put("id", JsonPrimitive("33333333-3333-3333-3333-333333333333")) })
            collection(listOf(if (offsets.size == 1) chapter("12.5") else second), 2)
        })
        val result = source.getChapterList(MangaDexParser.manga(manga()))
        assertEquals(listOf("0", "1"), offsets)
        assertEquals(listOf(2f, 12.5f), result.map { it.chapterNumber })
    }

    @Test fun rejectsTruncatedPagination() = runTest {
        val source = MangaDexSource(JsonTransport { collection(emptyList(), 10) })
        try {
            source.getChapterList(MangaDexParser.manga(manga()))
            fail("Expected a pagination failure")
        } catch (_: SourceFormatException) {
            assertTrue(true)
        }
    }

    @Test fun rejectsApiErrorResponses() = runTest {
        val source = MangaDexSource(JsonTransport { buildJsonObject { put("result", "error") } })
        try {
            source.getPopular(1)
            fail("Expected an API failure")
        } catch (_: SourceFormatException) {
            assertTrue(true)
        }
    }

    @Test fun rejectsInvalidPageBeforeRequesting() = runTest {
        val source = MangaDexSource(JsonTransport { error("Network must not be called") })
        try {
            source.getPopular(0)
            fail("Expected an invalid page failure")
        } catch (_: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
