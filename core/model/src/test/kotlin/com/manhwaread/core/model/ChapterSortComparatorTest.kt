package com.manhwaread.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterSortComparatorTest {
    private fun chapter(name: String, number: Float, season: Int = 1): Chapter = Chapter(
        mangaId = 1L,
        url = "/s$season/$number",
        name = name,
        season = season,
        chapterNumber = number,
    )

    @Test
    fun `sorts by chapter number descending`() {
        val input = listOf(chapter("a", 1f), chapter("b", 3f), chapter("c", 2f))

        assertEquals(listOf("b", "c", "a"), input.sortedWith(ChapterSortComparator).map { it.name })
    }

    @Test
    fun `sorts by season descending first`() {
        val input = listOf(
            chapter("s1c99", 99f, season = 1),
            chapter("s2c1", 1f, season = 2),
            chapter("s2c5", 5f, season = 2),
            chapter("s1c100", 100f, season = 1),
        )

        assertEquals(
            listOf("s2c5", "s2c1", "s1c100", "s1c99"),
            input.sortedWith(ChapterSortComparator).map { it.name },
        )
    }

    @Test
    fun `unnumbered chapters go last`() {
        val input = listOf(chapter("epilogue", -1f), chapter("c5", 5f), chapter("preview", -1f))

        val sorted = input.sortedWith(ChapterSortComparator)

        assertEquals("c5", sorted.first().name)
        assertEquals(setOf("epilogue", "preview"), sorted.drop(1).map { it.name }.toSet())
    }

    @Test
    fun `zero chapter sorts above unnumbered`() {
        val input = listOf(chapter("epilogue", -1f), chapter("c0", 0f))

        assertEquals(listOf("c0", "epilogue"), input.sortedWith(ChapterSortComparator).map { it.name })
    }

    @Test
    fun `stable for equal keys preserves input order`() {
        val input = listOf(
            chapter("first", 5f),
            chapter("second", 5f),
            chapter("third", 6f),
            chapter("fourth", 5f),
        )

        assertEquals(
            listOf("third", "first", "second", "fourth"),
            input.sortedWith(ChapterSortComparator).map { it.name },
        )
    }

    @Test
    fun `empty and single element lists`() {
        assertEquals(emptyList<Chapter>(), emptyList<Chapter>().sortedWith(ChapterSortComparator))

        val single = listOf(chapter("x", 1f))
        assertEquals(single, single.sortedWith(ChapterSortComparator))
    }

    @Test
    fun `isNumbered reflects parser contract`() {
        assertEquals(true, chapter("c1", 1f).isNumbered)
        assertEquals(true, chapter("c0", 0f).isNumbered)
        assertEquals(false, chapter("epilogue", -1f).isNumbered)
    }
}
