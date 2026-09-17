package com.manhwaread.feature.reader

import com.manhwaread.core.vision.RectF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterMetaJsonTest {
    private val meta = ChapterMeta(
        title = "Глава 12",
        bubbles = listOf(
            BubbleHitArea(
                bubbleId = "b1",
                pageIndex = 0,
                bounds = RectF(left = 1f, top = 2f, right = 30f, bottom = 40f),
                originalText = "안녕",
                translatedText = "Привет",
            ),
            BubbleHitArea(
                bubbleId = "b2",
                pageIndex = 1,
                bounds = RectF(left = 0f, top = 0f, right = 10f, bottom = 10f),
                originalText = "효과음",
                translatedText = null,
            ),
        ),
    )

    @Test
    fun `roundtrip preserves title and bubbles`() {
        val decoded = ChapterMetaJson.decode(ChapterMetaJson.encode(meta))
        assertEquals(meta, decoded)
    }

    @Test
    fun `null translation survives roundtrip`() {
        val decoded = ChapterMetaJson.decode(ChapterMetaJson.encode(meta))
        assertNull(decoded.bubbles[1].translatedText)
    }

    @Test
    fun `empty and blank input decode to empty meta`() {
        assertEquals(ChapterMeta(title = "", bubbles = emptyList()), ChapterMetaJson.decode("[]"))
        assertEquals(ChapterMeta(title = "", bubbles = emptyList()), ChapterMetaJson.decode("  "))
    }

    @Test
    fun `malformed json throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { ChapterMetaJson.decode("[[[") }
    }

    @Test
    fun `bubble without bounds throws IllegalArgumentException`() {
        val raw = """{"title":"t","bubbles":[{"bubbleId":"b","pageIndex":0,"originalText":"x"}]}"""
        val error = assertThrows(IllegalArgumentException::class.java) { ChapterMetaJson.decode(raw) }
        assertTrue(error.message!!.contains("bounds"))
    }
}
