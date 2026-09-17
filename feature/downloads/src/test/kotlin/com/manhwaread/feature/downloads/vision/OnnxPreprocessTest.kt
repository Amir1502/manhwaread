package com.manhwaread.feature.downloads.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OnnxPreprocessTest {
    @Test
    fun `normalize maps full scale to one with imagenet-half constants`() {
        // (255/255 - 0.5) / 0.5 = 1
        assertEquals(1f, normalizeChannel(255f, 0.5f, 0.5f), EPS)
        // (0/255 - 0.5) / 0.5 = -1
        assertEquals(-1f, normalizeChannel(0f, 0.5f, 0.5f), EPS)
    }

    @Test
    fun `normalize with zero std throws`() {
        assertThrows<IllegalArgumentException> { normalizeChannel(128f, 0.5f, 0f) }
    }

    @Test
    fun `toNchw produces planar layout`() {
        // Два пикселя 2×1: красный (255,0,0) и зелёный (0,255,0).
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val tensor = toNchw(pixels, width = 2, height = 1, mean = 0.5f, std = 0.5f)
        assertEquals(6, tensor.size)
        // План R: пиксель0=255 → 1, пиксель1=0 → -1.
        assertEquals(1f, tensor[0], EPS)
        assertEquals(-1f, tensor[1], EPS)
        // План G: пиксель0=0 → -1, пиксель1=255 → 1.
        assertEquals(-1f, tensor[2], EPS)
        assertEquals(1f, tensor[3], EPS)
        // План B: оба 0 → -1.
        assertEquals(-1f, tensor[4], EPS)
        assertEquals(-1f, tensor[5], EPS)
    }

    @Test
    fun `toNchw rejects mismatched size`() {
        assertThrows<IllegalArgumentException> {
            toNchw(intArrayOf(0, 0, 0), width = 2, height = 2, mean = 0.5f, std = 0.5f)
        }
    }

    @Test
    fun `toNchw rejects non-positive dimensions`() {
        assertThrows<IllegalArgumentException> {
            toNchw(intArrayOf(), width = 0, height = 1, mean = 0.5f, std = 0.5f)
        }
    }

    @Test
    fun `sigmoid of zero is half`() {
        assertEquals(0.5f, sigmoid(0f), EPS)
        assertTrue(sigmoid(10f) > 0.999f)
        assertTrue(sigmoid(-10f) < 0.001f)
    }

    @Test
    fun `decodeMask thresholds logits through sigmoid`() {
        val mask = decodeMask(floatArrayOf(0f, 10f, -10f), width = 3, height = 1, threshold = 0.5f)
        assertTrue(mask[0])
        assertTrue(mask[1])
        assertFalse(mask[2])
    }

    @Test
    fun `decodeMask rejects mismatched size`() {
        assertThrows<IllegalArgumentException> {
            decodeMask(floatArrayOf(0f), width = 2, height = 2, threshold = 0.5f)
        }
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
