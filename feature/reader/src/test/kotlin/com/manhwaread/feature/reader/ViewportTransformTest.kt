package com.manhwaread.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewportTransformTest {
    private val imageWidth = 800
    private val viewWidth = 1080f

    @Test
    fun `fitToWidth maps image width to view width`() {
        val transform = fitToWidth(imageWidth, viewWidth)
        assertEquals(viewWidth / imageWidth, transform.scale, EPS)
        assertEquals(viewWidth, transform.toScreenX(imageWidth.toFloat()), EPS)
        assertEquals(0f, transform.offsetX, EPS)
        assertEquals(0f, transform.offsetY, EPS)
    }

    @Test
    fun `screen and image coordinates roundtrip`() {
        val transform = ViewportTransform(scale = 1.7f, offsetX = -40f, offsetY = 25f)
        assertEquals(123f, transform.toImageX(transform.toScreenX(123f)), EPS)
        assertEquals(456f, transform.toImageY(transform.toScreenY(456f)), EPS)
    }

    @Test
    fun `zoomAround keeps image point under focus`() {
        val base = baseScaleFor(imageWidth, viewWidth)
        val start = fitToWidth(imageWidth, viewWidth)
        val focusX = 300f
        val focusY = 800f
        val zoomed = start.zoomAround(
            factor = 2f,
            focusScreenX = focusX,
            focusScreenY = focusY,
            baseScale = base,
        )
        assertEquals(start.toImageX(focusX), zoomed.toImageX(focusX), EPS)
        assertEquals(start.toImageY(focusY), zoomed.toImageY(focusY), EPS)
        assertEquals(start.scale * 2f, zoomed.scale, EPS)
    }

    @Test
    fun `zoom clamps at five times base scale`() {
        val base = baseScaleFor(imageWidth, viewWidth)
        var transform = fitToWidth(imageWidth, viewWidth)
        repeat(20) {
            transform = transform.zoomAround(factor = 2f, focusScreenX = 100f, focusScreenY = 100f, baseScale = base)
        }
        assertEquals(base * ViewportTransform.MAX_ZOOM, transform.scale, EPS)
    }

    @Test
    fun `zoom out never goes below base scale`() {
        val base = baseScaleFor(imageWidth, viewWidth)
        val transform = fitToWidth(imageWidth, viewWidth)
            .zoomAround(factor = 0.1f, focusScreenX = 0f, focusScreenY = 0f, baseScale = base)
        assertEquals(base, transform.scale, EPS)
    }

    @Test
    fun `clamped centers content smaller than view`() {
        val transform = ViewportTransform(scale = 0.5f, offsetX = 999f, offsetY = -999f)
        val result = transform.clamped(
            imageWidthPx = 100,
            imageHeightPx = 100,
            viewWidthPx = 1000f,
            viewHeightPx = 1000f,
        )
        assertEquals((1000f - 50f) / 2f, result.offsetX, EPS)
        assertEquals((1000f - 50f) / 2f, result.offsetY, EPS)
    }

    @Test
    fun `clamped keeps zoomed image covering the view`() {
        val transform = ViewportTransform(scale = 4f, offsetX = 5000f, offsetY = -5000f)
        val result = transform.clamped(
            imageWidthPx = 800,
            imageHeightPx = 1000,
            viewWidthPx = 1080f,
            viewHeightPx = 2400f,
        )
        // Ширина контента 3200 > 1080: offsetX в [1080 - 3200, 0].
        assertTrue(result.offsetX <= 0f)
        assertTrue(result.offsetX >= 1080f - 3200f)
        // Высота контента 4000 > 2400: offsetY в [2400 - 4000, 0].
        assertTrue(result.offsetY <= 0f)
        assertTrue(result.offsetY >= 2400f - 4000f)
    }

    @Test
    fun `panBy shifts offsets`() {
        val transform = ViewportTransform(scale = 2f, offsetX = 10f, offsetY = 20f).panBy(5f, -7f)
        assertEquals(15f, transform.offsetX, EPS)
        assertEquals(13f, transform.offsetY, EPS)
    }

    @Test
    fun `fitScreen fits whole page and centers`() {
        val transform = fitScreen(
            imageWidthPx = 400,
            imageHeightPx = 800,
            viewWidthPx = 1080f,
            viewHeightPx = 2400f,
        )
        // scale = min(1080/400, 2400/800) = 2.7; высота 2160 < 2400 — центрирование.
        assertEquals(2.7f, transform.scale, EPS)
        assertEquals(0f, transform.offsetX, EPS)
        assertEquals((2400f - 800 * 2.7f) / 2f, transform.offsetY, EPS)
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
