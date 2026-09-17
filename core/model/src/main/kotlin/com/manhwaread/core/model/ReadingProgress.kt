package com.manhwaread.core.model

/**
 * Индекс страницы: неотрицательное целое. Value-класс защищает
 * от случайного смешивания с другими Int в сигнатурах.
 */
@JvmInline
value class PageIndex(val value: Int) : Comparable<PageIndex> {
    init {
        require(value >= 0) { "pageIndex must be >= 0, got $value" }
    }

    override fun compareTo(other: PageIndex): Int = value.compareTo(other.value)

    override fun toString(): String = "PageIndex($value)"
}

/**
 * Смещение скролла в пикселях внутри страницы (режим вебтуна): >= 0.
 * Позиция чтения сохраняется «до пикселя».
 */
@JvmInline
value class ScrollOffsetPx(val value: Int) : Comparable<ScrollOffsetPx> {
    init {
        require(value >= 0) { "scrollOffsetPx must be >= 0, got $value" }
    }

    override fun compareTo(other: ScrollOffsetPx): Int = value.compareTo(other.value)

    override fun toString(): String = "ScrollOffsetPx($value)"
}

/**
 * Точная позиция чтения: страница + пиксельное смещение.
 * Переживает перезапуск приложения (хранится в истории).
 */
data class ReadingProgress(
    val pageIndex: PageIndex,
    val scrollOffsetPx: ScrollOffsetPx,
) {
    companion object {
        /** Начало главы. */
        val START: ReadingProgress = ReadingProgress(PageIndex(0), ScrollOffsetPx(0))

        /** Фабрика с валидацией аргументов (отрицательные значения → IllegalArgumentException). */
        fun of(pageIndex: Int, scrollOffsetPx: Int = 0): ReadingProgress =
            ReadingProgress(PageIndex(pageIndex), ScrollOffsetPx(scrollOffsetPx))
    }
}
