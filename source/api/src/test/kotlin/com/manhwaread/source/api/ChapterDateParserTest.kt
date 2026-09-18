package com.manhwaread.source.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ChapterDateParserTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `chapter number prefers keyword suffix`() {
        assertEquals(12f, parseChapterNumber("Chapter 12"))
        assertEquals(12.5f, parseChapterNumber("Chapter 12.5"))
        assertEquals(5f, parseChapterNumber("Season 2 - Chapter 5"))
        assertEquals(7f, parseChapterNumber("Vol.2 Ch.7"))
        assertEquals(3f, parseChapterNumber("Ep. 3"))
        assertEquals(4f, parseChapterNumber("Episode 4 - Final"))
    }

    @Test
    fun `chapter number falls back to first number`() {
        assertEquals(10f, parseChapterNumber("10"))
        assertEquals(4f, parseChapterNumber("4 - Beginning"))
    }

    @Test
    fun `chapter number without digits is minus one`() {
        assertEquals(-1f, parseChapterNumber("Oneshot"))
        assertEquals(-1f, parseChapterNumber(""))
    }

    @Test
    fun `relative dates subtract from now`() {
        assertEquals(now - 5 * 3_600_000L, parseChapterDate("5 hours ago", now))
        assertEquals(now - 86_400_000L, parseChapterDate("1 day ago", now))
        assertEquals(now - 14 * 86_400_000L, parseChapterDate("2 weeks ago", now))
        assertEquals(now - 3 * 30 * 86_400_000L, parseChapterDate("3 months ago", now))
        assertEquals(now - 30_000L, parseChapterDate("30 seconds ago", now))
        assertEquals(now - 45 * 60_000L, parseChapterDate("45 minutes ago", now))
        assertEquals(now - 365 * 86_400_000L, parseChapterDate("1 year ago", now))
    }

    @Test
    fun `absolute dates parse to utc midnight`() {
        val expected = LocalDate.of(2024, 3, 3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, parseChapterDate("March 3, 2024", now))
        assertEquals(expected, parseChapterDate("Mar 3, 2024", now))
        assertEquals(expected, parseChapterDate("3 March 2024", now))
        assertEquals(expected, parseChapterDate("2024-03-03", now))
    }

    @Test
    fun `unrecognized dates are zero`() {
        assertEquals(0L, parseChapterDate("just now", now))
        assertEquals(0L, parseChapterDate("", now))
        assertEquals(0L, parseChapterDate(null, now))
    }

    @Test
    fun `status keywords map to enum`() {
        assertEquals(MangaStatus.ONGOING, parseMangaStatusText("Ongoing"))
        assertEquals(MangaStatus.ONGOING, parseMangaStatusText(" OnGoing! "))
        assertEquals(MangaStatus.COMPLETED, parseMangaStatusText("Completed"))
        assertEquals(MangaStatus.HIATUS, parseMangaStatusText("On Hiatus"))
        assertEquals(MangaStatus.CANCELLED, parseMangaStatusText("Cancelled"))
        assertEquals(MangaStatus.CANCELLED, parseMangaStatusText("Canceled"))
        assertEquals(MangaStatus.UNKNOWN, parseMangaStatusText(null))
        assertEquals(MangaStatus.UNKNOWN, parseMangaStatusText("something else"))
    }
}
