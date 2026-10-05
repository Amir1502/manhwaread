package com.manhwaread.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderNavigationTest {
    private val chapters = (1L..10L).map { id -> ReaderTocItem(chapterId = id, title = "Глава $id") }

    @Test
    fun `neighbours follow reading order`() {
        val middle = ReaderNavigation(chapters = chapters, currentChapterId = 5L)
        assertEquals(5L, middle.currentChapter?.chapterId)
        assertEquals(4L, middle.previousChapter?.chapterId)
        assertEquals(6L, middle.nextChapter?.chapterId)

        val first = ReaderNavigation(chapters = chapters, currentChapterId = 1L)
        assertNull(first.previousChapter)
        assertEquals(2L, first.nextChapter?.chapterId)

        val last = ReaderNavigation(chapters = chapters, currentChapterId = 10L)
        assertEquals(9L, last.previousChapter?.chapterId)
        assertNull(last.nextChapter)
    }

    @Test
    fun `unknown chapter has no neighbours and no bookmarks without host`() {
        val unknown = ReaderNavigation(chapters = chapters, currentChapterId = 99L)
        assertNull(unknown.currentChapter)
        assertNull(unknown.previousChapter)
        assertNull(unknown.nextChapter)
        assertTrue(unknown.supportsBookmarks)

        val standalone = ReaderNavigation()
        assertFalse(standalone.supportsBookmarks)
        assertNull(standalone.nextChapter)
    }

    @Test
    fun `subtitle separates volume and chapter`() {
        assertEquals("Том 7 · Глава 302", chapterSubtitle("Том 7 Глава 302"))
        assertEquals("Том 7 · Глава 5", chapterSubtitle("Том 7. Глава 5"))
        assertEquals("ТОМ 2 · ГЛАВА 10", chapterSubtitle("ТОМ 2 - ГЛАВА 10"))
        assertEquals("Vol.3 · Ch.24", chapterSubtitle("Vol.3 Ch.24"))
        assertEquals("Volume 1 · Chapter 2: Start", chapterSubtitle("Volume 1 Chapter 2: Start"))
    }

    @Test
    fun `subtitle without volume is kept and whitespace normalized`() {
        assertEquals("Глава 214 Конец", chapterSubtitle("Глава 214 Конец"))
        assertEquals("Том 1 · Глава 1", chapterSubtitle("  Том 1   Глава 1  "))
        assertEquals("Пролог", chapterSubtitle("Пролог"))
    }

    @Test
    fun `toc opens near current chapter in newest first order`() {
        // Новые сверху: глава 10 — индекс 0, глава 8 — индекс 2, глава 1 — индекс 9.
        assertEquals(0, tocInitialScrollIndex(chapters, currentChapterId = 10L))
        assertEquals(0, tocInitialScrollIndex(chapters, currentChapterId = 8L))
        assertEquals(3, tocInitialScrollIndex(chapters, currentChapterId = 5L))
        assertEquals(7, tocInitialScrollIndex(chapters, currentChapterId = 1L))
        assertEquals(0, tocInitialScrollIndex(chapters, currentChapterId = null))
        assertEquals(0, tocInitialScrollIndex(emptyList(), currentChapterId = 1L))
    }
}
