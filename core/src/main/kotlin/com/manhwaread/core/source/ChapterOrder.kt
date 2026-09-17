package com.manhwaread.core.source

import java.math.BigDecimal
import java.util.Locale

object ChapterOrder : Comparator<SChapter> {
    private val seasonPattern = Regex("(?:season|сезон|s)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val chapterPattern = Regex("(?:chapter|chap\\.?|ch\\.?|глава|гл\\.?)\\s*(\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE)
    private val numberPattern = Regex("\\d+(?:[.,]\\d+)?")

    data class Key(val season: Int, val number: BigDecimal?, val name: String, val url: String)

    fun key(chapter: SChapter): Key {
        val seasonMatch = seasonPattern.find(chapter.name)
        val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val withoutSeason = seasonPattern.replace(chapter.name, " ")
        val explicit = chapterPattern.find(withoutSeason)?.groupValues?.get(1)
        val fromMetadata = chapter.chapterNumber.takeIf { it.isFinite() && it >= 0f }?.toString()
        val number = (explicit ?: fromMetadata ?: numberPattern.find(withoutSeason)?.value)
            ?.replace(',', '.')?.toBigDecimalOrNull()
        return Key(season, number, chapter.name.lowercase(Locale.ROOT), chapter.url)
    }

    override fun compare(a: SChapter, b: SChapter): Int {
        val left = key(a)
        val right = key(b)
        val season = left.season.compareTo(right.season)
        if (season != 0) return season
        val number = when {
            left.number == null && right.number == null -> 0
            left.number == null -> 1
            right.number == null -> -1
            else -> left.number.compareTo(right.number)
        }
        if (number != 0) return number
        val name = left.name.compareTo(right.name)
        return if (name != 0) name else left.url.compareTo(right.url)
    }
}
