package com.manhwaread.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingProgressTest {
    @Test
    fun `START is page zero and offset zero`() {
        assertEquals(0, ReadingProgress.START.pageIndex.value)
        assertEquals(0, ReadingProgress.START.scrollOffsetPx.value)
    }

    @Test
    fun `of constructs valid progress`() {
        val progress = ReadingProgress.of(pageIndex = 3, scrollOffsetPx = 120)

        assertEquals(3, progress.pageIndex.value)
        assertEquals(120, progress.scrollOffsetPx.value)
    }

    @Test
    fun `negative page index throws`() {
        assertThrows(IllegalArgumentException::class.java) { ReadingProgress.of(pageIndex = -1) }
    }

    @Test
    fun `negative scroll offset throws`() {
        assertThrows(IllegalArgumentException::class.java) { ReadingProgress.of(pageIndex = 0, scrollOffsetPx = -5) }
    }

    @Test
    fun `raw value classes reject negatives`() {
        assertThrows(IllegalArgumentException::class.java) { PageIndex(-1) }
        assertThrows(IllegalArgumentException::class.java) { ScrollOffsetPx(-1) }
    }

    @Test
    fun `value equality and ordering`() {
        assertEquals(PageIndex(2), PageIndex(2))
        assertTrue(PageIndex(1) < PageIndex(2))
        assertTrue(ScrollOffsetPx(10) < ScrollOffsetPx(11))
        assertEquals(ReadingProgress.of(1, 2), ReadingProgress.of(1, 2))
    }

    @Test
    fun `copy semantics keep validation`() {
        val progress = ReadingProgress.of(5, 10)
        val next = progress.copy(pageIndex = PageIndex(6))

        assertEquals(6, next.pageIndex.value)
        assertEquals(10, next.scrollOffsetPx.value)
        assertThrows(IllegalArgumentException::class.java) { progress.copy(pageIndex = PageIndex(-2)) }
    }

    @Test
    fun `toString is readable`() {
        assertEquals("PageIndex(7)", PageIndex(7).toString())
        assertEquals("ScrollOffsetPx(9)", ScrollOffsetPx(9).toString())
    }
}
