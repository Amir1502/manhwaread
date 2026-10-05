package com.manhwaread.feature.reader

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebtoonPositionHelperTest {
    private fun mockItem(index: Int, offset: Int, size: Int): LazyListItemInfo {
        val item = mockk<LazyListItemInfo>()
        every { item.index } returns index
        every { item.offset } returns offset
        every { item.size } returns size
        return item
    }

    private fun mockLayoutInfo(
        items: List<LazyListItemInfo>,
        startOffset: Int = 0,
        endOffset: Int = 2000,
    ): LazyListLayoutInfo {
        val info = mockk<LazyListLayoutInfo>()
        every { info.visibleItemsInfo } returns items
        every { info.viewportStartOffset } returns startOffset
        every { info.viewportEndOffset } returns endOffset
        return info
    }

    @Test
    fun `empty visible items returns 0`() {
        val layout = mockLayoutInfo(emptyList())
        assertEquals(0, findCenterVisibleItemIndex(layout))
    }

    @Test
    fun `tall item spanning across center returns its index`() {
        // Viewport center is (0 + 2000) / 2 = 1000
        // Item 0 is tall: offset = -5000, size = 15000 (ends at 10000, spans across 1000)
        val item0 = mockItem(index = 0, offset = -5000, size = 15000)
        val layout = mockLayoutInfo(listOf(item0))
        assertEquals(0, findCenterVisibleItemIndex(layout))
    }

    @Test
    fun `item spanning viewport center is preferred over top item`() {
        // Viewport center is 1000
        // Item 0: offset = -500, size = 1200 (ends at 700) -> before center
        // Item 1: offset = 700, size = 1500 (ends at 2200) -> contains center (1000)
        val item0 = mockItem(index = 0, offset = -500, size = 1200)
        val item1 = mockItem(index = 1, offset = 700, size = 1500)
        val layout = mockLayoutInfo(listOf(item0, item1))
        assertEquals(1, findCenterVisibleItemIndex(layout))
    }

    @Test
    fun `when no item directly covers center, chooses closest to center`() {
        // Viewport center is 1000
        // Item 0: offset = 0, size = 600 (center = 300, dist = 700)
        // Item 1: offset = 1400, size = 600 (center = 1700, dist = 700)
        // Item 2: offset = 1200, size = 200 (center = 1300, dist = 300) -> closest!
        val item0 = mockItem(index = 0, offset = 0, size = 600)
        val item1 = mockItem(index = 1, offset = 1400, size = 600)
        val item2 = mockItem(index = 2, offset = 1200, size = 200)
        val layout = mockLayoutInfo(listOf(item0, item1, item2))
        assertEquals(2, findCenterVisibleItemIndex(layout))
    }
}
