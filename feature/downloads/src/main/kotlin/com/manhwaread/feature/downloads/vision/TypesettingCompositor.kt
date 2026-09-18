package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.EllipseMask
import com.manhwaread.core.vision.Mask
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PolygonMask
import com.manhwaread.core.vision.RectMask
import com.manhwaread.core.vision.TextMeasurer
import com.manhwaread.core.vision.TextSegment
import com.manhwaread.core.vision.buildOverlay
import com.manhwaread.core.vision.fit

// Пригодность перевода сегмента: целевой язык — русский, поэтому текст без
// кириллицы (транслитерация OCR-мусора) и эхо оригинала не используются.
internal fun isUsableTranslation(ocrText: String, translated: String): Boolean {
    if (translated.isBlank()) return false
    if (normalizeForEchoCheck(ocrText).equals(normalizeForEchoCheck(translated), ignoreCase = true)) return false
    return translated.any { char -> char in CYRILLIC_RANGE }
}

// Нормализация для сравнения на эхо: trim + схлопывание пробельных серий.
private fun normalizeForEchoCheck(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

private val CYRILLIC_RANGE = '\u0400'..'\u04FF'
private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Стадия COMPOSITING (ФАЗА 15): перевод сегментов главы → векторный оверлей.
 * Перевод НЕ запекается в растр (ключевое решение продукта): по геометрии
 * бабла строится маска, [fit] подбирает кегль и переносы, [buildOverlay]
 * даёт OverlaySpec, который рисует рендер-слой читалки. Баблики без перевода
 * пропускаются; переполнение фиксируется деградацией CLIPPED внутри FitResult.
 */
class TypesettingCompositor(
    private val bubbleStore: BubbleStore,
    private val measurer: TextMeasurer,
) : OverlayCompositor {
    override suspend fun composite(
        job: ChapterJob,
        segments: List<TextSegment>,
        translated: List<TranslatedSegment>,
    ): DomainResult<List<OverlaySpec>> {
        val bubbles = bubbleStore.loadBubbles(job.ref.chapterId)
        if (bubbles.isEmpty()) return DomainResult.success(emptyList())
        val translatedById = translated.associate { item -> item.id to item.text }
        val segmentsByBubble = segments.groupBy { segment -> segment.bubbleId }
        val specs = bubbles.mapNotNull { bubble ->
            bubbleSpec(bubble, segmentsByBubble[bubble.id].orEmpty(), translatedById)
        }
        return DomainResult.success(specs)
    }

    private fun bubbleSpec(
        bubble: Bubble,
        bubbleSegments: List<TextSegment>,
        translatedById: Map<String, String>,
    ): OverlaySpec? {
        if (bubbleSegments.isEmpty()) return null
        // Сегменты бабла в порядке чтения; непригодные переводы (эхо оригинала,
        // текст без кириллицы) исключаются из джойна — мусор не попадает в оверлей.
        val text = bubbleSegments
            .sortedBy { segment -> segment.readingOrder }
            .mapNotNull { segment ->
                translatedById[segment.id]
                    ?.takeIf { translated -> isUsableTranslation(segment.ocrText, translated) }
            }
            .joinToString(SEGMENT_SEPARATOR)
        if (text.isBlank()) return null
        val mask = maskFor(bubble)
        val result = fit(text, mask, measurer)
        return buildOverlay(bubbleId = bubble.id, mask = mask, result = result, measurer = measurer)
    }

    // Выбор маски: полигон с достаточным числом точек → PolygonMask;
    // нарративные боксы → прямоугольник; остальные баблы → эллипс.
    internal fun maskFor(bubble: Bubble): Mask = when {
        bubble.polygon.size >= MIN_POLYGON_POINTS -> PolygonMask(bubble.pageIndex, bubble.polygon)
        bubble.kind == BubbleKind.NARRATION_BOX -> RectMask(bubble.pageIndex, bubble.bounds)
        else -> EllipseMask(bubble.pageIndex, bubble.bounds)
    }

    private companion object {
        const val MIN_POLYGON_POINTS = 3
        const val SEGMENT_SEPARATOR = "\n"
    }
}
