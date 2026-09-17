package com.manhwaread.core.common

/**
 * Нормализация текста после OCR/перевода: невидимые символы, дефисные
 * переносы, markdown-разметка, маркеры говорящего («Name:»), русская типографика.
 */
object TextNormalizer {
    // Невидимые и «нулевой ширины» символы, ломающие метрики текста.
    private val INVISIBLE_CHARS = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00AD')

    // Левая часть переноса, где дефис СОХРАНЯется (частицы/местоимения + латиница).
    private val HYPHEN_KEEP_LEFT = setOf(
        "из", "по", "во", "от", "до", "за", "на", "с", "к", "у", "а", "и", "но", "то",
        "кто", "что", "как", "так", "где", "когда", "почему", "сколько", "какой", "чей",
        "кое", "куда", "зачем", "потому", "поэтому", "вряд", "едва", "бы", "же", "ли", "нет", "да",
    )

    // «пере-\nвод» → «перевод» (дефис теряем), «из-\nза» → «из-за» (дефис жив).
    private val HYPHEN_BREAK = Regex("([\\p{L}]+)-\\n *([\\p{L}])")

    // Одиночный перевод строки внутри предложения → пробел; пустые строки не трогаем.
    private val SOFT_BREAK = Regex("([\\p{L}0-9,.!?;:»\"')])\\n([\\p{L}0-9«\"'(])")

    private val SPACES = Regex("[ \\t\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+")
    private val SPACE_BEFORE_PUNCT = Regex(" +([,.!?;:»…])")
    private val PUNCT_BEFORE_LETTER = Regex("([,.!?;:])(?=[\\p{L}«\"'(])")
    private val INLINE_DASH = Regex(" - ")

    // «Narrator: текст» / «Итан:  текст» — маркер говорящего в начале строки.
    private val SPEAKER_MARKER = Regex("^\\s*([\\p{Lu}][\\p{L} .\\-']{0,40}):\\s*")

    private val BOLD_MARKDOWN = Regex("\\*\\*(.+?)\\*\\*")
    private val UNDER_MARKDOWN = Regex("__(.+?)__")
    private val HEADING_MARKDOWN = Regex("^ {0,3}#{1,6} *")
    private val QUOTE_MARKDOWN = Regex("^ {0,3}> ?")

    /** Полный конвейер нормализации. Пустой вход даёт пустой выход. */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        var text = stripInvisibles(raw)
        text = stripMarkdown(text)
        text = collapseLineBreaks(text)
        text = stripSpeakerMarkers(text)
        text = collapseWhitespace(text)
        text = russianTypography(text)
        return text.trim()
    }

    /** Убирает zero-width символы, soft hyphen; U+00A0 → пробел; CRLF → LF. */
    fun stripInvisibles(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch == '\u00A0' -> sb.append(' ')
                ch in INVISIBLE_CHARS -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("\r\n", "\n").replace('\r', '\n')
    }

    /** Снимает **bold**, __underline__, `код`, ведущие # и > построчно. */
    fun stripMarkdown(input: String): String {
        var text = BOLD_MARKDOWN.replace(input) { m -> m.groupValues[1] }
        text = UNDER_MARKDOWN.replace(text) { m -> m.groupValues[1] }
        text = text.replace("`", "")
        return text.lines().joinToString("\n") { line ->
            QUOTE_MARKDOWN.replace(HEADING_MARKDOWN.replace(line, ""), "")
        }
    }

    /** Склеивает дефисные переносы и одиночные переводы строк внутри предложения. */
    fun collapseLineBreaks(input: String): String {
        val joined = HYPHEN_BREAK.replace(input) { m ->
            val left = m.groupValues[1]
            val keepHyphen = left.any { it in 'a'..'z' || it in 'A'..'Z' } ||
                left.lowercase() in HYPHEN_KEEP_LEFT
            if (keepHyphen) "$left-${m.groupValues[2]}" else "$left${m.groupValues[2]}"
        }
        return SOFT_BREAK.replace(joined) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }
    }

    /** Удаляет маркер говорящего («Name: ») в начале каждой строки. */
    fun stripSpeakerMarkers(input: String): String =
        input.lines().joinToString("\n") { SPEAKER_MARKER.replace(it, "") }

    /** Сжимает пробелы, убирает пробел перед пунктуацией, добавляет после. */
    fun collapseWhitespace(input: String): String {
        var text = SPACES.replace(input, " ")
        text = SPACE_BEFORE_PUNCT.replace(text) { m -> m.groupValues[1] }
        return PUNCT_BEFORE_LETTER.replace(text) { m -> "${m.groupValues[1]} " }
    }

    /** «ёлочки», тире в диалогах и « — » вместо « - ». */
    fun russianTypography(input: String): String {
        val sb = StringBuilder(input.length)
        var quoteOpen = false
        for (ch in input) {
            if (ch == '"') {
                sb.append(if (quoteOpen) '»' else '«')
                quoteOpen = !quoteOpen
            } else {
                sb.append(ch)
            }
        }
        val withDashes = INLINE_DASH.replace(sb.toString(), " — ")
        return withDashes.lines().joinToString("\n") { line ->
            if (line.startsWith("- ")) "—${line.substring(1)}" else line
        }
    }
}
