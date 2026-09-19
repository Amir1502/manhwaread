package com.manhwaread.feature.reader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Глава из каталога на диске: файлы страниц (page*.png/jpg/webp) в числовом
// порядке, chapter.json (заголовок и области баблов), overlays.json
// (векторный слой перевода). Формат каталога — результат скачивания глав
// (ФАЗА 15); используется для офлайн-чтения и self-check.
class FileChapterLoader(
    private val imageSizeReader: ImageSizeReader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReaderContentLoader {
    override suspend fun loadChapter(chapterDir: File): ReaderChapter = withContext(ioDispatcher) {
        require(chapterDir.isDirectory) { "not a directory: ${chapterDir.path}" }
        val meta = ChapterMetaJson.decode(readIfExists(File(chapterDir, META_FILE)))
        // Битый overlays.json не должен мешать чтению оригинала: слой
        // перевода теряется, но глава открывается.
        val overlays = runCatching { OverlaySpecJson.decode(readIfExists(File(chapterDir, OVERLAY_FILE))) }
            .getOrDefault(emptyList())
        val overlaysByPage = overlays.groupBy { it.pageIndex }
        val bubblesByPage = meta.bubbles.groupBy { it.pageIndex }
        val pages = pageFiles(chapterDir).mapIndexed { index, file ->
            // Битый файл страницы не убивает главу: страница без читаемых
            // размеров помечается corrupted — читалка покажет заглушку.
            val size = runCatching { imageSizeReader.read(file) }.getOrNull()
            ReaderPage(
                index = index,
                imageFile = file,
                widthPx = size?.first ?: 0,
                heightPx = size?.second ?: 0,
                overlays = overlaysByPage[index].orEmpty(),
                bubbles = bubblesByPage[index].orEmpty(),
                isCorrupted = size == null,
            )
        }
        ReaderChapter(title = meta.title, pages = pages)
    }

    private fun readIfExists(file: File): String = if (file.isFile) file.readText() else "[]"

    private fun pageFiles(dir: File): List<File> =
        dir.listFiles { file -> file.isFile && file.extension.lowercase() in PAGE_EXTENSIONS }
            ?.sortedBy { pageIndexFromName(it.nameWithoutExtension) }
            .orEmpty()

    private companion object {
        const val META_FILE = "chapter.json"
        const val OVERLAY_FILE = "overlays.json"
        val PAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        // page001 → 1; имя без числа сортируется в конец.
        fun pageIndexFromName(name: String): Int = name.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
    }
}
