package com.manhwaread.feature.reader

import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * Определяет индекс текущей видимой страницы в вебтун-ленте по элементу,
 * находящемуся под вертикальным центром видимой области вьюпорта.
 *
 * Для сверхдлинных страниц (до 20 000 px) определение по первому видимому элементу
 * приводит к запаздыванию или преждевременному переключению прогресса.
 * Элемент, покрывающий центр экрана, гарантирует естественную индикацию страницы.
 */
fun findCenterVisibleItemIndex(layoutInfo: LazyListLayoutInfo): Int {
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return 0
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val centerItem = items.find { item ->
        val itemStart = item.offset
        val itemEnd = item.offset + item.size
        viewportCenter in itemStart..itemEnd
    } ?: items.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2
        kotlin.math.abs(itemCenter - viewportCenter)
    }
    return centerItem?.index ?: 0
}
