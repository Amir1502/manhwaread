package com.manhwaread.feature.downloads.queue

import java.io.File

// Раскладка каталогов глав для офлайн-чтения (ФАЗА 15):
// {base}/chapter_{id}/page*.{png,jpg,webp} + chapter.json + overlays.json —
// формат FileChapterLoader из :feature:reader. Один источник правды о путях:
// очередь пишет, читалка и навигация читают.
class ChapterDirs(private val baseDir: File) {
    fun dirFor(chapterId: Long): File = File(baseDir, "$CHAPTER_DIR_PREFIX$chapterId")

    fun ensureDirFor(chapterId: Long): File = dirFor(chapterId).apply { mkdirs() }

    /** Очистка осиротевших временных файлов (*.tmp) во всех каталогах глав. */
    fun sweepTempFiles(): Int {
        if (!baseDir.isDirectory) return 0
        var cleaned = 0
        baseDir.listFiles()?.filter { it.isDirectory && it.name.startsWith(CHAPTER_DIR_PREFIX) }?.forEach { chapterDir ->
            chapterDir.listFiles { file -> file.isFile && file.name.endsWith(".tmp") }?.forEach { tmpFile ->
                if (tmpFile.delete()) cleaned++
            }
        }
        return cleaned
    }

    private companion object {
        const val CHAPTER_DIR_PREFIX = "chapter_"
    }
}
