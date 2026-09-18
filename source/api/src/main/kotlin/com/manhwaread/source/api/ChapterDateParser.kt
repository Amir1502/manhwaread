package com.manhwaread.source.api

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Общие утилиты парсинга глав для HTML-источников (Madara, Asura):
// номер главы из названия и дата из относительного/абсолютного текста.
// Реализация одна на все источники (ФАЗА 13).

private val AFTER_CHAPTER_REGEX =
    Regex("""(?:chapter|ch|ep|episode)\s*\.?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
private val ANY_NUMBER_REGEX = Regex("""(\d+(?:[.,]\d+)?)""")
private val RELATIVE_REGEX =
    Regex("""(\d+)\s*(second|minute|hour|day|week|month|year)s?\s*ago""", RegexOption.IGNORE_CASE)
private val ABSOLUTE_FORMATS = listOf(
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
)

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND
private const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
private const val MILLIS_PER_WEEK = 7L * MILLIS_PER_DAY
private const val MILLIS_PER_MONTH = 30L * MILLIS_PER_DAY
private const val MILLIS_PER_YEAR = 365L * MILLIS_PER_DAY

/**
 * Номер главы из названия: приоритет у числа после «chapter/ch/ep/episode»,
 * иначе первое число в строке. Не распознан → -1f (дефолт контракта SChapter).
 */
fun parseChapterNumber(name: String): Float {
    val afterKeyword = AFTER_CHAPTER_REGEX.find(name)
        ?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
    if (afterKeyword != null) return afterKeyword
    return ANY_NUMBER_REGEX.find(name)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() ?: -1f
}

/**
 * Дата главы из текста сайта: относительная («5 hours ago») или абсолютная
 * («March 3, 2024», «2024-03-03»). Не распознан → 0L (дата неизвестна).
 * [nowMillis] — точка отсчёта для относительных дат (инжектируется в тестах).
 */
fun parseChapterDate(text: String?, nowMillis: Long): Long {
    val value = text?.trim().orEmpty()
    if (value.isEmpty()) return 0L
    RELATIVE_REGEX.find(value)?.let { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: return@let
        val unitMillis = when (match.groupValues[2].lowercase(Locale.ENGLISH)) {
            "second" -> MILLIS_PER_SECOND
            "minute" -> MILLIS_PER_MINUTE
            "hour" -> MILLIS_PER_HOUR
            "day" -> MILLIS_PER_DAY
            "week" -> MILLIS_PER_WEEK
            "month" -> MILLIS_PER_MONTH
            "year" -> MILLIS_PER_YEAR
            else -> return@let
        }
        return (nowMillis - amount * unitMillis).coerceAtLeast(0L)
    }
    for (format in ABSOLUTE_FORMATS) {
        val date = runCatching { LocalDate.parse(value, format) }.getOrNull() ?: continue
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    return 0L
}

/** Статус тайтла из текста сайта (регистронезависимо, по ключевому слову). */
fun parseMangaStatusText(text: String?): MangaStatus {
    val normalized = text?.trim()?.lowercase(Locale.ENGLISH).orEmpty()
    return when {
        "ongoing" in normalized -> MangaStatus.ONGOING
        "completed" in normalized -> MangaStatus.COMPLETED
        "hiatus" in normalized -> MangaStatus.HIATUS
        "cancel" in normalized -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }
}
