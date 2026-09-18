package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment
import com.manhwaread.feature.reader.BubbleHitArea
import com.manhwaread.feature.reader.ChapterMeta
import com.manhwaread.feature.reader.ChapterMetaJson
import com.manhwaread.feature.reader.OverlaySpecJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Писатель офлайн-архива главы (ФАЗА 15). После скачивания: chapter.json
// с заголовком и пустым списком баблов (глава читается без перевода) и пустой
// overlays.json. После конвейера перевода: chapter.json с областями баблов
// (оригинал + перевод для карточки по тапу) и векторный слой overlays.json.
class ChapterArchiveWriter(
    private val chapterDirs: ChapterDirs,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun writeDownloadedChapter(chapterId: Long, title: String): Unit =
        withContext(ioDispatcher) {
            val dir = chapterDirs.ensureDirFor(chapterId)
            writeMeta(dir, ChapterMeta(title = title, bubbles = emptyList()))
            File(dir, FileOverlayStore.OVERLAYS_FILE_NAME).writeText(OverlaySpecJson.encode(emptyList()))
        }

    suspend fun writeTranslatedChapter(
        chapterId: Long,
        title: String,
        bubbles: List<Bubble>,
        segments: List<TextSegment>,
        specs: List<OverlaySpec>,
    ): Unit =
        withContext(ioDispatcher) {
            val dir = chapterDirs.ensureDirFor(chapterId)
            writeMeta(dir, ChapterMeta(title = title, bubbles = bubbles.map { bubble -> hitArea(bubble, segments, specs) }))
            File(dir, FileOverlayStore.OVERLAYS_FILE_NAME).writeText(OverlaySpecJson.encode(specs))
        }

    // Контент карточки бабла: оригинал — сегменты бабла в порядке чтения,
    // перевод — строки векторного слоя (единственный источник переведённого текста).
    private fun hitArea(bubble: Bubble, segments: List<TextSegment>, specs: List<OverlaySpec>): BubbleHitArea {
        val original = segments
            .filter { segment -> segment.bubbleId == bubble.id }
            .sortedBy { segment -> segment.readingOrder }
            .joinToString(SEPARATOR) { segment -> segment.ocrText }
        val translated = specs
            .firstOrNull { spec -> spec.bubbleId == bubble.id }
            ?.lines
            ?.joinToString(SEPARATOR) { line -> line.text }
        return BubbleHitArea(
            bubbleId = bubble.id,
            pageIndex = bubble.pageIndex,
            bounds = bubble.bounds,
            originalText = original,
            translatedText = translated?.takeIf { text -> text.isNotBlank() },
        )
    }

    private fun writeMeta(dir: File, meta: ChapterMeta) {
        File(dir, META_FILE_NAME).writeText(ChapterMetaJson.encode(meta))
    }

    companion object {
        const val META_FILE_NAME = "chapter.json"
        private const val SEPARATOR = "\n"
    }
}
