package com.manhwaread.feature.downloads.queue

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Валидация растра настоящим BitmapFactory (Robolectric): на чистой JVM
// decodeByteArray — заглушка, поэтому боевой критерий проверяется только здесь.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImageValidationTest {
    @Test
    fun `real png passes validation`() {
        assertTrue(isDecodableImage(onePixelPngBytes))
    }

    @Test
    fun `html body fails validation`() {
        assertFalse(isDecodableImage("<html><body>Anti-bot check</body></html>".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `magic bytes alone are not enough`() {
        // Сигнатура PNG цела, но данных изображения нет — усечённый файл после смерти процесса.
        val truncated = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
        assertFalse(isDecodableImage(truncated))
    }

    @Test
    fun `empty bytes fail validation`() {
        assertFalse(isDecodableImage(ByteArray(0)))
    }
}
