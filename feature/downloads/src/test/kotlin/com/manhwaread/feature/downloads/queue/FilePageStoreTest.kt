package com.manhwaread.feature.downloads.queue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FilePageStoreTest {
    @TempDir
    lateinit var tempDir: File

    private val dirs by lazy { ChapterDirs(tempDir) }

    private fun store() = FilePageStore(dirs, Dispatchers.Unconfined)

    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1)
    private val webpBytes = buildWebpBytes()

    private fun buildWebpBytes(): ByteArray {
        val header = "RIFF".toByteArray(Charsets.US_ASCII)
        val size = byteArrayOf(0x24, 0, 0, 0)
        val marker = "WEBP".toByteArray(Charsets.US_ASCII)
        return header + size + marker + byteArrayOf(1, 2)
    }

    @Test
    fun `save and load roundtrip`() = runTest {
        store().savePage(chapterId = 10L, pageIndex = 0, bytes = pngBytes)
        assertArrayEquals(pngBytes, store().loadPage(10L, 0))
    }

    @Test
    fun `extension detected from magic bytes`() = runTest {
        store().savePage(chapterId = 10L, pageIndex = 0, bytes = pngBytes)
        store().savePage(chapterId = 10L, pageIndex = 1, bytes = jpegBytes)
        store().savePage(chapterId = 10L, pageIndex = 2, bytes = webpBytes)
        val names = dirs.dirFor(10L).listFiles()?.map { file -> file.name }?.sorted().orEmpty()
        assertEquals(listOf("page001.png", "page002.jpg", "page003.webp"), names)
    }

    @Test
    fun `unknown format saved as bin`() = runTest {
        store().savePage(chapterId = 10L, pageIndex = 0, bytes = byteArrayOf(1, 2))
        assertTrue(File(dirs.dirFor(10L), "page001.bin").isFile)
    }

    @Test
    fun `load missing page returns null`() = runTest {
        assertNull(store().loadPage(99L, 0))
        store().savePage(chapterId = 10L, pageIndex = 0, bytes = pngBytes)
        assertNull(store().loadPage(10L, 1))
    }

    @Test
    fun `resave replaces old file regardless of extension`() = runTest {
        store().savePage(chapterId = 10L, pageIndex = 0, bytes = jpegBytes)
        store().savePage(chapterId = 10L, pageIndex = 0, bytes = pngBytes)
        val names = dirs.dirFor(10L).listFiles()?.map { file -> file.name }.orEmpty()
        assertEquals(listOf("page001.png"), names)
        assertArrayEquals(pngBytes, store().loadPage(10L, 0))
    }
}
