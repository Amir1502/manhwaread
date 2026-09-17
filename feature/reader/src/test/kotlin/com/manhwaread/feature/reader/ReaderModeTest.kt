package com.manhwaread.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderModeTest {
    @Test
    fun `webtoon is the only continuous mode`() {
        assertTrue(ReaderMode.WEBTOON.isContinuousWebtoon)
        assertFalse(ReaderMode.VERTICAL.isContinuousWebtoon)
        assertFalse(ReaderMode.LTR.isContinuousWebtoon)
        assertFalse(ReaderMode.RTL.isContinuousWebtoon)
    }

    @Test
    fun `vertical modes are webtoon and vertical pager`() {
        assertTrue(ReaderMode.WEBTOON.isVertical)
        assertTrue(ReaderMode.VERTICAL.isVertical)
        assertFalse(ReaderMode.LTR.isVertical)
        assertFalse(ReaderMode.RTL.isVertical)
    }

    @Test
    fun `only rtl reads right to left`() {
        assertTrue(ReaderMode.RTL.isRtl)
        ReaderMode.entries.filter { it != ReaderMode.RTL }.forEach { mode ->
            assertFalse(mode.isRtl, "$mode must not be RTL")
        }
    }

    @Test
    fun `every mode has distinct non-zero label resource`() {
        val labels = ReaderMode.entries.map { it.labelRes() }
        labels.forEach { label -> assertTrue(label != 0) }
        assertEquals(labels.size, labels.distinct().size)
    }
}
