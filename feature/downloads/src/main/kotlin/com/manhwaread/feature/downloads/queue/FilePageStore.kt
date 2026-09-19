package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.pipeline.PageStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

// Дисковое хранилище страниц (ФАЗА 15): файлы каталога главы — офлайн-кэш,
// который читалка читает напрямую (FileChapterLoader принимает page*.png/jpg/webp).
// Расширение определяется по магическим байтам: контракт PageStore несёт только
// байты, без URL и content-type. Неизвестный формат пишется .bin и игнорируется читалкой.
class FilePageStore(
    private val chapterDirs: ChapterDirs,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PageStore {
    override suspend fun savePage(chapterId: Long, pageIndex: Int, bytes: ByteArray): Unit =
        withContext(ioDispatcher) {
            val dir = chapterDirs.ensureDirFor(chapterId)
            val target = File(dir, "${pageName(pageIndex)}.${extensionFor(bytes)}")
            // Атомарная запись: сначала временный файл, затем rename. Смерть процесса
            // посреди записи больше не оставляет усечённую страницу в кэше главы.
            val tmp = File(dir, "${target.name}$TMP_SUFFIX")
            tmp.writeBytes(bytes)
            // Повторная загрузка страницы заменяет файл: удаляем старые расширения
            // и временный мусор прежних прогонов (rename в Windows не перезаписывает цель).
            pageFiles(dir, pageIndex).forEach { file -> file.delete() }
            tmpFiles(dir, pageIndex).filter { file -> file != tmp }.forEach { file -> file.delete() }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("page $pageIndex of chapter $chapterId: rename to ${target.name} failed")
            }
        }

    override suspend fun loadPage(chapterId: Long, pageIndex: Int): ByteArray? =
        withContext(ioDispatcher) {
            pageFiles(chapterDirs.dirFor(chapterId), pageIndex).firstOrNull()?.readBytes()
        }

    private fun pageFiles(dir: File, pageIndex: Int): List<File> {
        if (!dir.isDirectory) return emptyList()
        val prefix = pageName(pageIndex)
        return dir.listFiles { file -> file.isFile && file.nameWithoutExtension == prefix }
            ?.toList()
            .orEmpty()
            .sortedBy { file -> file.name }
    }

    // Временные файлы записи («page001.png.tmp»): pageFiles их не видит
    // (nameWithoutExtension — «page001.png»), поэтому чистятся отдельно.
    private fun tmpFiles(dir: File, pageIndex: Int): List<File> {
        if (!dir.isDirectory) return emptyList()
        val prefix = "${pageName(pageIndex)}."
        return dir.listFiles { file -> file.isFile && file.name.startsWith(prefix) && file.name.endsWith(TMP_SUFFIX) }
            ?.toList()
            .orEmpty()
    }

    // page001 — нумерация с 1 и три цифры: FileChapterLoader сортирует по числу в имени.
    private fun pageName(pageIndex: Int): String = String.format(Locale.US, "page%03d", pageIndex + 1)

    private fun extensionFor(bytes: ByteArray): String = when {
        bytes.startsWith(PNG_MAGIC) -> "png"
        bytes.startsWith(JPEG_MAGIC) -> "jpg"
        isWebp(bytes) -> "webp"
        else -> "bin"
    }

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size > WEBP_HEADER_END &&
            bytes.startsWith(RIFF_MAGIC) &&
            String(bytes, WEBP_MARKER_OFFSET, WEBP_MARKER_LENGTH, Charsets.US_ASCII) == WEBP_MARKER

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private companion object {
        const val TMP_SUFFIX = ".tmp"
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
        const val WEBP_MARKER = "WEBP"
        const val WEBP_MARKER_OFFSET = 8
        const val WEBP_MARKER_LENGTH = 4
        const val WEBP_HEADER_END = 12
    }
}
