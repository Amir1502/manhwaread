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
    fun `sample size grows when page shown downscaled`() {
        // Абсолютный масштаб: 1x и крупнее — полномерное декодирование.
        assertEquals(1, sampleSizeForScale(1f))
        assertEquals(1, sampleSizeForScale(2.5f))
        assertEquals(1, sampleSizeForScale(2f))
        // 0.5: 1*2*0.5=1 <= 1 → 2; 2*2*0.5=2 > 1 → стоп.
        assertEquals(2, sampleSizeForScale(0.5f))
        assertEquals(2, sampleSizeForScale(0.4f))
        // 0.25: 1*2*0.25=0.5 → 2; 2*2*0.25=1 <= 1 → 4; 4*2*0.25=2 > 1 → стоп.
        assertEquals(4, sampleSizeForScale(0.25f))
        assertEquals(4, sampleSizeForScale(0.2f))
    }

    @Test
    fun `band fully inside image maps to image rect`() {
        val transform = ViewportTransform.IDENTITY
        val rect = visibleImageRectForBand(
            transform = transform,
            bandLeftPx = 10f,
            bandTopPx = 20f,
            bandRightPx = 300f,
            bandBottomPx = 400f,
            imageWidthPx = 800,
            imageHeightPx = 20000,
        )
        assertEquals(TileRect(10, 20, 301, 401), rect)
    }

    @Test
    fun `band partially outside image is clamped`() {
        val transform = ViewportTransform.IDENTITY
        val rect = visibleImageRectForBand(
            transform = transform,
            bandLeftPx = -100f,
            bandTopPx = -50f,
            bandRightPx = 500f,
            bandBottomPx = 300f,
            imageWidthPx = 800,
            imageHeightPx = 20000,
        )
        assertEquals(TileRect(0, 0, 501, 301), rect)
    }

    @Test
    fun `band fully outside image yields null`() {
        // Изображение на экране занимает [-5000, -4200): полоса вью его не касается.
        val transform = ViewportTransform(scale = 1f, offsetX = 5000f, offsetY = 0f)
        assertNull(
            visibleImageRectForBand(
                transform = transform,
                bandLeftPx = 0f,
                bandTopPx = 0f,
                bandRightPx = 400f,
                bandBottomPx = 800f,
                imageWidthPx = 800,
                imageHeightPx = 20000,
            ),
        )
    }

    @Test
    fun `visible rect for webtoon band limited to band not full strip`() {
        // Страница 800×20000 показана fit-to-width (scale=1.35); видимая
        // полоса — нижняя половина вью 1080×2400: в координатах изображения
        // это ~889 px, а не все 20000.
        val transform = fitToWidth(800, 1080f)
        val rect = visibleImageRectForBand(
            transform = transform,
            bandLeftPx = 0f,
            bandTopPx = 1200f,
            bandRightPx = 1080f,
            bandBottomPx = 2400f,
            imageWidthPx = 800,
            imageHeightPx = 20000,
        )!!
        assertTrue(rect.height < 1000, "band height ${rect.height} must stay near band, not full strip")
    }

    @Test
    fun `frame pixel estimate positive and bounded`() {
        val estimate = maxDecodedPixelsPerFrame(1080f, 2400f, 1.35f)
        assertTrue(estimate > 0)
        assertTrue(estimate < 4_000_000L)
    }
}
