package com.manhwaread.feature.downloads.queue

import java.io.File

// Раскладка каталогов глав для офлайн-чтения (ФАЗА 15):
// {base}/chapter_{id}/page*.{png,jpg,webp} + chapter.json + overlays.json —
// формат FileChapterLoader из :feature:reader. Один источник правды о путях:
// очередь пишет, читалка и навигация читают.
class ChapterDirs(private val baseDir: File) {
    fun dirFor(chapterId: Long): File = File(baseDir, "$CHAPTER_DIR_PREFIX$chapterId")

    fun ensureDirFor(chapterId: Long): File = dirFor(chapterId).apply { mkdirs() }

    private companion object {
        const val CHAPTER_DIR_PREFIX = "chapter_"
    }
}
