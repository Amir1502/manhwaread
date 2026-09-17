package com.manhwaread.core.model

/**
 * Сортировка глав: по сезону убыв., затем по номеру главы убыв.
 * Главы с нераспознанным номером (-1f) естественно оказываются в конце.
 * Для полностью равных ключей возвращает 0 — порядок входа сохраняется
 * при стабильной сортировке (List.sortedWith в Kotlin стабилен).
 */
object ChapterSortComparator : Comparator<Chapter> {
    override fun compare(a: Chapter, b: Chapter): Int {
        val bySeason = b.season.compareTo(a.season)
        if (bySeason != 0) return bySeason
        return b.chapterNumber.compareTo(a.chapterNumber)
    }
}
