package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.pipeline.OverlayStore
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.feature.reader.OverlaySpecJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Файловое хранилище векторных оверлеев (ФАЗА 15): overlays.json в каталоге
// главы — формат FileChapterLoader из :feature:reader. Повреждённый файл
// читается как пустой слой: читалка показывает оригинал вместо падения.
class FileOverlayStore(
    private val chapterDirs: ChapterDirs,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OverlayStore {
    override suspend fun saveChapter(chapterId: Long, specs: List<OverlaySpec>): Unit =
        withContext(ioDispatcher) {
            val dir = chapterDirs.ensureDirFor(chapterId)
            File(dir, OVERLAYS_FILE_NAME).writeText(OverlaySpecJson.encode(specs))
        }

    override suspend fun loadChapter(chapterId: Long): List<OverlaySpec> =
        withContext(ioDispatcher) {
            val file = File(chapterDirs.dirFor(chapterId), OVERLAYS_FILE_NAME)
            if (!file.isFile) {
                emptyList()
            } else {
                runCatching { OverlaySpecJson.decode(file.readText()) }.getOrDefault(emptyList())
            }
        }

    override suspend fun clearChapter(chapterId: Long): Unit =
        withContext(ioDispatcher) {
            File(chapterDirs.dirFor(chapterId), OVERLAYS_FILE_NAME).delete()
        }

    companion object {
        const val OVERLAYS_FILE_NAME = "overlays.json"
    }
}
