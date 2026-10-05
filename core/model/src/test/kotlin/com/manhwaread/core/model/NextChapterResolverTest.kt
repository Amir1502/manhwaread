package com.manhwaread.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NextChapterResolverTest {
    private fun ch(
        id: Long,
        name: String = "Ch $id",
        season: Int = 1,
        number: Float = id.toFloat(),
        read: Boolean = false,
    ) = Chapter(
        id = id,
        mangaId = 1L,
        url = "/ch/$id",
        name = name,
        season = season,
        chapterNumber = number,
        read = read,
    )

    @Test
    fun `resolve returns None for empty chapters`() {
        val target = NextChapterResolver.resolve(emptyList())
        assertEquals(NextChapterTarget.None, target)
        assertNull(target.chapterOrNull)
    }

    @Test
    fun `resolve returns Start with first chronological chapter when none read`() {
        val chapters = listOf(
            ch(id = 3L, number = 3f, read = false),
            ch(id = 1L, number = 1f, read = false),
            ch(id = 2L, number = 2f, read = false),
        )

        val target = NextChapterResolver.resolve(chapters)
        assertTrue(target is NextChapterTarget.Start)
        assertEquals(1L, (target as NextChapterTarget.Start).chapter.id)
        assertEquals(1L, target.chapterOrNull?.id)
    }

    @Test
    fun `resolve returns ReRead with first chronological chapter when all read`() {
        val chapters = listOf(
            ch(id = 2L, number = 2f, read = true),
            ch(id = 1L, number = 1f, read = true),
        )

        val target = NextChapterResolver.resolve(chapters)
        assertTrue(target is NextChapterTarget.ReRead)
        assertEquals(1L, (target as NextChapterTarget.ReRead).chapter.id)
        assertEquals(1L, target.chapterOrNull?.id)
    }

    @Test
    fun `resolve returns Resume with next unread after lastReadChapterId`() {
        val chapters = listOf(
            ch(id = 1L, number = 1f, read = true),
            ch(id = 2L, number = 2f, read = true),
            ch(id = 3L, number = 3f, read = false),
            ch(id = 4L, number = 4f, read = false),
        )

        val target = NextChapterResolver.resolve(chapters, lastReadChapterId = 2L)
        assertTrue(target is NextChapterTarget.Resume)
        assertEquals(3L, (target as NextChapterTarget.Resume).chapter.id)
    }

    @Test
    fun `resolve returns Resume with earliest unread when lastRead was skipped ahead`() {
        // Прочитана 1 и 3, последняя прочитанная — 3. После 3 непрочитанных нет, возвращаем главу 2.
        val chapters = listOf(
            ch(id = 1L, number = 1f, read = true),
            ch(id = 2L, number = 2f, read = false),
            ch(id = 3L, number = 3f, read = true),
        )

        val target = NextChapterResolver.resolve(chapters, lastReadChapterId = 3L)
        assertTrue(target is NextChapterTarget.Resume)
        assertEquals(2L, (target as NextChapterTarget.Resume).chapter.id)
    }

    @Test
    fun `resolve returns Resume with first unread when lastReadChapterId is null`() {
        val chapters = listOf(
            ch(id = 1L, number = 1f, read = true),
            ch(id = 2L, number = 2f, read = false),
        )

        val target = NextChapterResolver.resolve(chapters, lastReadChapterId = null)
        assertTrue(target is NextChapterTarget.Resume)
        assertEquals(2L, (target as NextChapterTarget.Resume).chapter.id)
    }

    @Test
    fun `comparator sorts seasons numbers unnumbered and tie breaks by id`() {
        val c1 = ch(id = 10L, season = 1, number = 1f)
        val c2 = ch(id = 20L, season = 1, number = 2f)
        val cUnknown1 = ch(id = 30L, season = 1, number = -1f)
        val cUnknown2 = ch(id = 40L, season = 1, number = -1f)
        val cSeason2 = ch(id = 5L, season = 2, number = 1f)
        val cEqualNumOlder = ch(id = 6L, season = 1, number = 1f)

        val unordered = listOf(cSeason2, cUnknown2, c2, cUnknown1, c1, cEqualNumOlder)
        val sorted = unordered.sortedWith(ChapterReadingOrderComparator)

        // Сезон 1, глава 1: id=6 перед id=10 (tie-break)
        assertEquals(6L, sorted[0].id)
        assertEquals(10L, sorted[1].id)
        // Сезон 1, глава 2
        assertEquals(20L, sorted[2].id)
        // Сезон 1, неизвестный номер (-1f): id=30 перед id=40
        assertEquals(30L, sorted[3].id)
        assertEquals(40L, sorted[4].id)
        // Сезон 2
        assertEquals(5L, sorted[5].id)
    }
}
