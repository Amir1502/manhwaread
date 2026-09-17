package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.contains

// Порядок чтения и привязка строк к баблам — чистые функции.

// Ширина вертикальной полосы для колоночного чтения (доля ширины страницы).
private const val BAND_WIDTH_FRACTION = 0.25f

// Максимальная длина звукоподражания (SFX) без пробелов.
private const val SFX_MAX_LENGTH = 8

// Доля катаканы (в сотых), при которой короткое слово считается SFX.
private const val KATAKANA_PERCENT = 60

// Номер Unicode-блока катаканы: старшая граница.
private val KATAKANA_RANGE = '\u30A0'..'\u30FF'

// Индекс вертикальной полосы, в которой находится центр строки.
private fun bandOf(line: OcrLine, pageWidthPx: Int): Int {
    val bandWidth = (pageWidthPx * BAND_WIDTH_FRACTION).coerceAtLeast(1f)
    val centerX = (line.bounds.left + line.bounds.right) / 2f
    return (centerX / bandWidth).toInt()
}

// Присвоение readingOrder: вебтун — сверху вниз (в строке слева направо),
// манга RTL — полосы справа налево, LTR — слева направо.
fun assignReadingOrder(
    lines: List<OcrLine>,
    pageWidthPx: Int,
    direction: ReadingDirection,
): List<OcrLine> {
    if (lines.isEmpty()) {
        return lines
    }
    val comparator = when (direction) {
        ReadingDirection.VERTICAL_WEBTOON ->
            compareBy<OcrLine> { line -> line.bounds.top }
                .thenBy { line -> line.bounds.left }
        ReadingDirection.LTR_PAGES ->
            compareBy<OcrLine> { line -> bandOf(line, pageWidthPx) }
                .thenBy { line -> line.bounds.top }
                .thenBy { line -> line.bounds.left }
        ReadingDirection.RTL_PAGES ->
            compareByDescending<OcrLine> { line -> bandOf(line, pageWidthPx) }
                .thenBy { line -> line.bounds.top }
                .thenBy { line -> line.bounds.left }
    }
    return lines.sortedWith(comparator).mapIndexed { index, line -> line.copy(readingOrder = index) }
}

// Эвристика звукоподражания: короткое слово без пробелов, состоящее
// преимущественно из катаканы либо из заглавных латинских букв.
fun isSfx(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > SFX_MAX_LENGTH) {
        return false
    }
    if (trimmed.any { character -> character.isWhitespace() }) {
        return false
    }
    val katakanaCount = trimmed.count { character -> character in KATAKANA_RANGE }
    if (katakanaCount * HUNDRED_PERCENT >= trimmed.length * KATAKANA_PERCENT) {
        return true
    }
    val hasLetters = trimmed.any { character -> character.isLetter() }
    val allCaps = trimmed.all { character -> character.isUpperCase() || !character.isLetter() }
    return hasLetters && allCaps
}

// Привязка строк к баблам по центру строки; null — строка вне баблов
// (нарратив на фоне): продукт вписывает перевод только в баблы.
fun assignLinesToBubbles(lines: List<OcrLine>, bubbles: List<Bubble>): List<Pair<OcrLine, Bubble?>> =
    lines.map { line ->
        val centerX = (line.bounds.left + line.bounds.right) / 2f
        val centerY = (line.bounds.top + line.bounds.bottom) / 2f
        line to bubbles.lastOrNull { bubble -> bubble.bounds.contains(centerX, centerY) }
    }

private const val HUNDRED_PERCENT = 100
