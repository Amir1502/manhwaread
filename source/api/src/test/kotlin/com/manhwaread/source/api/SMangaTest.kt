package com.manhwaread.source.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SMangaTest {

    private val original = SManga(
        url = "/manga/1",
        title = "Solo Leveling",
        sourceId = 1L,
        author = "Chugong",
        genres = listOf("Action", "Fantasy"),
        status = MangaStatus.COMPLETED,
        thumbnailUrl = "https://example.com/cover.jpg",
        initialized = true,
    )

    @Test
    fun `copy does not mutate original`() {
        val modified = original.copy(title = "Наруто", status = MangaStatus.ONGOING, nsfw = true)

        assertEquals("Solo Leveling", original.title)
        assertEquals(MangaStatus.COMPLETED, original.status)
        assertFalse(original.nsfw)
        assertEquals("Наруто", modified.title)
        assertEquals(MangaStatus.ONGOING, modified.status)
        assertTrue(modified.nsfw)
        assertNotSame(original, modified)
    }

    @Test
    fun `copy preserves untouched fields`() {
        val modified = original.copy(title = "X")

        assertEquals(original.url, modified.url)
        assertEquals(original.sourceId, modified.sourceId)
        assertEquals(original.author, modified.author)
        assertEquals(original.genres, modified.genres)
        assertEquals(original.thumbnailUrl, modified.thumbnailUrl)
        assertEquals(original.initialized, modified.initialized)
    }

    @Test
    fun `genres extension via copy leaves original list intact`() {
        val extended = original.copy(genres = original.genres + "Adventure")

        assertEquals(2, original.genres.size)
        assertEquals(3, extended.genres.size)
        assertEquals(listOf("Action", "Fantasy"), original.genres)
    }

    @Test
    fun `data class equality is structural`() {
        assertEquals(original, original.copy())
        assertEquals(original.hashCode(), original.copy().hashCode())
        assertNotEquals(original, original.copy(title = "Other"))
    }

    @Test
    fun `defaults are applied for minimal instance`() {
        val minimal = SManga(url = "/u", title = "t", sourceId = 7L)

        assertEquals(MangaStatus.UNKNOWN, minimal.status)
        assertEquals(emptyList<String>(), minimal.genres)
        assertFalse(minimal.initialized)
        assertFalse(minimal.nsfw)
        assertNull(minimal.thumbnailUrl)
    }

    private fun assertNotEquals(unexpected: Any?, actual: Any?) =
        assertFalse(unexpected == actual, "expected values to differ")

    private fun <T> assertNull(value: T?) = assertTrue(value == null, "expected null, got $value")
}
