package com.manhwaread.app.navigation

import com.manhwaread.feature.reader.ReaderMode
import com.manhwaread.source.api.MangaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaderModePolicyTest {
    @Test
    fun `manga reads right to left, manhwa and manhua as webtoon`() {
        assertEquals(ReaderMode.RTL, defaultReaderModeFor(MangaType.MANGA))
        assertEquals(ReaderMode.WEBTOON, defaultReaderModeFor(MangaType.MANHWA))
        assertEquals(ReaderMode.WEBTOON, defaultReaderModeFor(MangaType.MANHUA))
    }

    @Test
    fun `unknown type falls back to webtoon`() {
        assertEquals(ReaderMode.WEBTOON, defaultReaderModeFor(MangaType.OTHER))
        assertEquals(ReaderMode.WEBTOON, defaultReaderModeFor(null))
    }

    @Test
    fun `saved mode name parses back and unknown names are ignored`() {
        ReaderMode.entries.forEach { mode -> assertEquals(mode, readerModeOf(mode.name)) }
        assertNull(readerModeOf("PAGED_DOUBLE"))
        assertNull(readerModeOf(null))
    }
}
