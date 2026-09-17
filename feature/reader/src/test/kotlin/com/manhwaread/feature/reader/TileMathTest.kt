package com.manhwaread.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileMathTest {
    @Test
    fun `tile count for huge webtoon page`() {
        // cols = ceil(800/512) = 2, rows = ceil(20000/512) = 40.
        assertEquals(80, tileCount(imageWidthPx = 800, imageHeightPx = 20000))
    }

    @Test
    fun `visible tiles bounded by viewport not image size`() {
        val transform = fitToWidth(800, 1080f)
        val visible = visibleImageRect(transform, 1080f, 2400f, 800, 20000)
        assertNotNull(visible)
        val tiles = visibleTiles(visible!!, 800, 20000)
        // Видимая высота ~1778 px изображения: 4 ряда по 2 колонки.
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size <= 10, "expected at most 10 tiles, got ${tiles.size}")
    }

    @Test
    fun `decoded pixels per frame bounded for 800x20000 page`() {
        val transform = fitToWidth(800, 1080f)
        val visible = visibleImageRect(transform, 1080f, 2400f, 800, 20000)!!
        val totalPixels = visibleTiles(visible, 800, 20000).sumOf { tile -> tile.width.toLong() * tile.height.toLong() }
        // Страница целиком — 16 млн px; за кадр декодируется только вьюпорт с запасом.
        assertTrue(totalPixels < 800L * 20000L / 4, "decoded $totalPixels px is unbounded")
    }

    @Test
    fun `edge tiles are clamped to image bounds`() {
        val tiles = visibleTiles(TileRect(700, 19900, 800, 20000), imageWidthPx = 800, imageHeightPx = 20000)
        assertTrue(tiles.isNotEmpty())
        tiles.forEach { tile ->
            assertTrue(tile.right <= 800)
            assertTrue(tile.bottom <= 20000)
            assertTrue(tile.width > 0)
            assertTrue(tile.height > 0)
        }
    }

    @Test
    fun `viewport fully outside image yields null`() {
        val transform = ViewportTransform(scale = 1f, offsetX = 5000f, offsetY = 0f)
        assertNull(visibleImageRect(transform, 1080f, 2400f, 800, 20000))
    }

    @Test
    fun `visible rect spans full width at fit scale`() {
        val transform = fitToWidth(800, 1080f)
        val visible = visibleImageRect(transform, 1080f, 2400f, 800, 20000)!!
        assertEquals(0, visible.left)
        assertEquals(0, visible.top)
        assertEquals(800, visible.right)
    }

    @Test
    fun `sample size grows when zoomed out`() {
        assertEquals(1, sampleSizeFor(1f))
        assertEquals(1, sampleSizeFor(2.5f))
        assertEquals(2, sampleSizeFor(0.4f))
        assertEquals(4, sampleSizeFor(0.2f))
    }

    @Test
    fun `frame pixel estimate positive and bounded`() {
        val estimate = maxDecodedPixelsPerFrame(1080f, 2400f, 1.35f)
        assertTrue(estimate > 0)
        assertTrue(estimate < 4_000_000L)
    }
}
