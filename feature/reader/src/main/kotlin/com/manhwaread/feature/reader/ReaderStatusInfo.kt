package com.manhwaread.feature.reader

import kotlin.math.roundToInt

// Чистая логика нижних элементов читалки (инфо-полоса, слайдер страниц):
// вынесена из Compose, чтобы проверяться JVM-тестами без эмулятора.

/** Процент заряда из ACTION_BATTERY_CHANGED: `level * 100 / scale`; некорректные значения → null. */
fun batteryPercent(level: Int, scale: Int): Int? =
    if (level < 0 || scale <= 0) null else (level * PERCENT_MAX / scale).coerceIn(0, PERCENT_MAX)

/** Задержка до ближайшей границы минуты: часы инфо-полосы обновляются ровно в hh:mm:00. */
fun millisUntilNextMinute(nowMs: Long): Long = MINUTE_MS - Math.floorMod(nowMs, MINUTE_MS)

/**
 * Текст инфо-полосы вебтуна: «12 / 86 · 74% · 20:41». Страница считается
 * с единицы и зажимается в диапазон; неизвестный заряд и пустое время опускаются.
 */
fun formatInfoStrip(currentPage: Int, pageCount: Int, batteryPercent: Int?, time: String?): String {
    val total = pageCount.coerceAtLeast(1)
    val page = (currentPage + 1).coerceIn(1, total)
    val parts = buildList {
        add("$page / $total")
        if (batteryPercent != null) add("$batteryPercent%")
        if (!time.isNullOrBlank()) add(time)
    }
    return parts.joinToString(separator = INFO_SEPARATOR)
}

/** Слайдер показывается только при двух и более страницах: иначе диапазон значений пуст. */
fun isPageSliderVisible(pageCount: Int): Boolean = pageCount > 1

/** Число промежуточных шагов слайдера: значения 0..pageCount-1, шаг — одна страница. */
fun pageSliderSteps(pageCount: Int): Int = (pageCount - 2).coerceAtLeast(0)

/** Значение слайдера → индекс страницы: округление (а не отбрасывание) защищает от 4.9999 → 4. */
fun sliderValueToPage(value: Float, pageCount: Int): Int =
    value.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))

private const val PERCENT_MAX = 100
private const val MINUTE_MS = 60_000L
private const val INFO_SEPARATOR = " · "
