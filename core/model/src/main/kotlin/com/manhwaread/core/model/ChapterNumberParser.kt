package com.manhwaread.core.model

/**
 * Разбор номера главы (и сезона) из заголовка в свободной форме.
 *
 * Распознаваемые форматы:
 * - латиница: "Chapter 12", "Ch.12.5", "Episode 4", "Ep 10.5", "Cap 3";
 * - сезон: "Season 2 - Episode 4", "S2 Chapter 3";
 * - CJK: "第12話", "第12话", "제12화" (в т.ч. полноширинные цифры "第０１２話");
 * - том+глава: "Vol.3 Ch.24";
 * - одиночное число в конце заголовка: "Solo Leveling 110".
 *
 * Нераспознанный вход (включая "Epilogue", "Preview", пустую строку и
 * отрицательные номера) → [UNKNOWN] (-1f). "Chapter 0" — валидный ноль.
 */
object ChapterNumberParser {
    /** Нераспознанный номер главы. */
    const val UNKNOWN: Float = -1f

    /** Сезон по умолчанию, если в заголовке не указан явно. */
    const val DEFAULT_SEASON: Int = 1

    // Сезон: "Season 2", "SEASON 02", "S2". Граница слова \b защищает от "Scan", "Story" и т.п.
    private val SEASON_REGEX = Regex("""(?i)\b(?:season|s)\s*\.?\s*(\d{1,3})\b""")

    // Японский/китайский: 第12話 / 第12话 / 第12回 / 第12節 (суффикс необязателен).
    private val CJK_REGEX = Regex("""第\s*([0-9０-９]+(?:[.．][0-9０-９]+)?)""")

    // Корейский: 제12화 (суффикс 화 необязателен).
    private val KOREAN_REGEX = Regex("""제\s*([0-9０-９]+(?:[.．][0-9０-９]+)?)""")

    // Латиница: chapter/chap/ch/episode/ep/cap + номер (дробный через . или ,).
    private val LATIN_REGEX = Regex("""(?i)\b(?:chapter|chap|ch|episode|ep|cap)\s*\.?\s*(-?[0-9]+(?:[.,][0-9]+)?)""")

    // Одиночное число в конце заголовка: "Solo Leveling 110", "#12".
    private val TRAILING_REGEX = Regex("""[#№]?\s*(-?[0-9]+(?:[.,][0-9]+)?)\s*$""")

    // Порядок важен: CJK и корейский раньше латиницы, «голое число» — последний резерв.
    private val NUMBER_REGEXES = listOf(CJK_REGEX, KOREAN_REGEX, LATIN_REGEX, TRAILING_REGEX)

    /** Результат полного разбора: сезон (>= 1) и номер главы (или [UNKNOWN]). */
    data class Parsed(val season: Int, val number: Float)

    /** Номер главы из заголовка; нераспознанный → [UNKNOWN]. */
    fun parse(name: String): Float = parseFull(name).number

    /** Сезон из заголовка; не указан → [DEFAULT_SEASON]. */
    fun parseSeason(name: String): Int = parseFull(name).season

    /** Полный разбор заголовка: сезон + номер. */
    fun parseFull(name: String): Parsed {
        val normalized = toAsciiDigits(name).trim()
        if (normalized.isEmpty()) return Parsed(DEFAULT_SEASON, UNKNOWN)

        val season = SEASON_REGEX.find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_SEASON
        val number = findFirstNumber(normalized)
        // Отрицательный номер считаем невалидным — контракт требует -1f для нераспознанного.
        return Parsed(season, number?.takeIf { it >= 0f } ?: UNKNOWN)
    }

    /** Номер из первого сработавшего шаблона: CJK → корейский → латиница → «голое число». */
    private fun findFirstNumber(normalized: String): Float? {
        for (regex in NUMBER_REGEXES) {
            val captured = regex.find(normalized)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull()
            if (captured != null) return captured
        }
        return null
    }

    /** Заменяет полноширинные цифры (０-９) и точку (．) на ASCII-эквиваленты. */
    private fun toAsciiDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                in '０'..'９' -> append('0' + (ch - '０'))
                '．' -> append('.')
                else -> append(ch)
            }
        }
    }
}
