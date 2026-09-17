package com.manhwaread.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class ChapterNumberParserTest {

    @ParameterizedTest(name = "parse(\"{0}\") == {1}")
    @MethodSource("numberCases")
    fun parseRecognizedFormats(name: String, expected: Float) {
        assertEquals(expected, ChapterNumberParser.parse(name), FLOAT_DELTA)
    }

    @ParameterizedTest(name = "parseSeason(\"{0}\") == {1}")
    @MethodSource("seasonCases")
    fun parseSeasonFormats(name: String, expected: Int) {
        assertEquals(expected, ChapterNumberParser.parseSeason(name))
    }

    @Test
    fun `parseFull returns season and number together`() {
        assertEquals(
            ChapterNumberParser.Parsed(season = 2, number = 4f),
            ChapterNumberParser.parseFull("Season 2 - Episode 4"),
        )
        assertEquals(
            ChapterNumberParser.Parsed(season = 1, number = 24f),
            ChapterNumberParser.parseFull("Vol.3 Ch.24"),
        )
    }

    @Test
    fun `decimal fractions keep precision`() {
        assertEquals(12.5f, ChapterNumberParser.parse("Ch.12.5"), FLOAT_DELTA)
        assertEquals(10.5f, ChapterNumberParser.parse("Episode 10.5"), FLOAT_DELTA)
        assertEquals(12.5f, ChapterNumberParser.parse("Ch. 12,5"), FLOAT_DELTA)
    }

    @Test
    fun `zero and boundary numbers`() {
        assertEquals(0f, ChapterNumberParser.parse("Chapter 0"), FLOAT_DELTA)
        assertEquals(99999f, ChapterNumberParser.parse("Chapter 99999"), FLOAT_DELTA)
    }

    companion object {
        private const val FLOAT_DELTA = 1e-6f

        @JvmStatic
        fun numberCases(): List<Arguments> = listOf(
            // Форматы из закрепённого списка.
            arguments("Chapter 12", 12f),
            arguments("Ch.12.5", 12.5f),
            arguments("Season 2 - Episode 4", 4f),
            arguments("S2 Chapter 3", 3f),
            arguments("第12話", 12f),
            arguments("第12话", 12f),
            arguments("제12화", 12f),
            arguments("Vol.3 Ch.24", 24f),
            arguments("Epilogue", -1f),
            arguments("Preview", -1f),
            // Границы и специальные значения.
            arguments("Chapter 0", 0f),
            arguments("Chapter 99999", 99999f),
            arguments("Chapter -5", -1f),
            // Регистр и пробелы.
            arguments("chapter 7", 7f),
            arguments("CHAPTER 7", 7f),
            arguments("Chapter    7", 7f),
            arguments("Ep 3", 3f),
            // Дробные и запятая как разделитель.
            arguments("Episode 10.5", 10.5f),
            arguments("Ch. 12,5", 12.5f),
            // Полноширинные цифры.
            arguments("第０１２話", 12f),
            arguments("제７화", 7f),
            // Нераспознанное.
            arguments("Oneshot", -1f),
            arguments("", -1f),
            arguments("   ", -1f),
            arguments("No digits here", -1f),
            // Голый номер в конце заголовка.
            arguments("Solo Leveling 110", 110f),
            arguments("#45", 45f),
        )

        @JvmStatic
        fun seasonCases(): List<Arguments> = listOf(
            arguments("Season 2 - Episode 4", 2),
            arguments("S2 Chapter 3", 2),
            arguments("SEASON 03 Ep 1", 3),
            arguments("Chapter 12", 1),
            arguments("제12화", 1),
            arguments("", 1),
            arguments("Vol.3 Ch.24", 1),
        )
    }
}
