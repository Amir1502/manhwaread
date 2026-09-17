package com.manhwaread.core

import com.manhwaread.core.model.DetectedLang
import com.manhwaread.core.model.TextSegment
import com.manhwaread.core.pipeline.PagePipeline
import com.manhwaread.core.pipeline.PageStage
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.source.ChapterOrder
import com.manhwaread.core.source.SChapter
import com.manhwaread.core.translation.SegmentTranslation
import com.manhwaread.core.translation.TranslationValidator
import com.manhwaread.core.typeset.PixelMask
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    private fun chapter(name: String, number: Float = -1f) = SChapter(name, name, 0L, number, null)

    @Test fun sortsSeasonsAndFractions() {
        val names = listOf("S2 Chapter 1", "Season 1 Chapter 12.5", "Chapter 2", "Глава 12,4")
        assertEquals(
            listOf("Chapter 2", "Глава 12,4", "Season 1 Chapter 12.5", "S2 Chapter 1"),
            names.map { chapter(it) }.sortedWith(ChapterOrder).map { it.name },
        )
    }

    @Test fun unknownChaptersFollowNumberedChapters() {
        assertTrue(ChapterOrder.compare(chapter("Extra"), chapter("Chapter 99")) > 0)
        assertEquals(0, ChapterOrder.key(chapter("Episode", 12.5f)).number!!.compareTo("12.5".toBigDecimal()))
    }

    @Test fun sourceMetadataDoesNotOverrideExplicitDecimal() {
        assertEquals(0, ChapterOrder.key(chapter("Chapter 12.05", 12f)).number!!.compareTo("12.05".toBigDecimal()))
    }

    @Test fun detectsInkOutsideNonRectangularMask() {
        val bubble = PixelMask(3, 3, booleanArrayOf(false, true, false, true, true, true, false, true, false))
        val dot = PixelMask(1, 1, booleanArrayOf(true))
        assertTrue(bubble.containsInk(dot, 1, 1))
        assertFalse(bubble.containsInk(dot, 0, 0))
        assertFalse(bubble.containsInk(dot, Int.MAX_VALUE, 1))
        assertFalse(bubble.containsInk(dot, -1, 0))
    }

    @Test fun ignoresTransparentInkPixelsAndCopiesInput() {
        val values = BooleanArray(9) { true }
        val bubble = PixelMask(3, 3, values)
        values.fill(false)
        val ink = PixelMask(2, 1, booleanArrayOf(false, true))
        assertTrue(bubble.containsInk(ink, -1, 0))
        assertTrue(bubble[1, 1])
    }

    @Test fun erodesBordersAndHoles() {
        val full = PixelMask(5, 5, BooleanArray(25) { true }).erode(1)
        assertFalse(full[0, 0])
        assertTrue(full[1, 1])
        assertFalse(full[4, 3])
        val hole = BooleanArray(25) { it != 12 }
        assertFalse(PixelMask(5, 5, hole).erode(1)[1, 1])
        assertFalse(full.erode(Int.MAX_VALUE)[2, 2])
    }

    @Test fun pipelineIsOrderedAndIdempotent() {
        val pipeline = PagePipeline.fresh("a".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { pipeline.begin(PageStage.OCR) }
        assertTrue(pipeline.begin(PageStage.DOWNLOAD))
        assertFalse(pipeline.begin(PageStage.DOWNLOAD))
        pipeline.succeed(PageStage.DOWNLOAD)
        pipeline.succeed(PageStage.DOWNLOAD)
        assertFalse(pipeline.begin(PageStage.DOWNLOAD))
        assertEquals(PageStage.PREPROCESS, pipeline.nextStage())
    }

    @Test fun recoversProcessDeathAndInvalidatesDependencies() {
        val pipeline = PagePipeline.fresh("b".repeat(64))
        pipeline.begin(PageStage.DOWNLOAD)
        pipeline.succeed(PageStage.DOWNLOAD)
        pipeline.begin(PageStage.PREPROCESS)
        val restored = PagePipeline(pipeline.snapshot())
        restored.recoverInterrupted()
        assertEquals(StageStatus.PENDING, restored.snapshot().stages.getValue(PageStage.PREPROCESS).status)
        restored.begin(PageStage.PREPROCESS)
        restored.fail(PageStage.PREPROCESS, "Decode failed")
        assertTrue(restored.begin(PageStage.PREPROCESS))
        restored.succeed(PageStage.PREPROCESS)
        restored.invalidateFrom(PageStage.DOWNLOAD)
        assertTrue(restored.snapshot().stages.values.all { it.status == StageStatus.PENDING })
    }

    @Test fun validatesDuplicateMissingAndUnexpectedIds() {
        val result = TranslationValidator.validate(
            listOf("a", "b", "c"),
            listOf(
                SegmentTranslation("a", "Hello", 0.8f),
                SegmentTranslation("b", "First", 0.9f),
                SegmentTranslation("b", "Second", 0.9f),
                SegmentTranslation("unknown", "Other", 0.8f),
            ),
        )
        assertEquals(listOf("a"), result.accepted.map { it.id })
        assertEquals(setOf("b", "c"), result.retryIds)
        assertEquals(setOf("unknown"), result.unexpectedIds)
        assertFalse(result.complete)
    }

    @Test fun rejectsEmptyAndInvalidConfidence() {
        val result = TranslationValidator.validate(
            listOf("a", "b"),
            listOf(SegmentTranslation("a", " ", 1f), SegmentTranslation("b", "Hello", Float.NaN)),
        )
        assertEquals(setOf("a", "b"), result.retryIds)
    }

    @Test fun preservesManualEditsAndMarksMissingSegments() {
        val segment = TextSegment("a", "bubble", 0, "Hello", DetectedLang.EN, 0.9f, emptyList(), 0, "Edited", false, true, false)
        val missing = segment.copy(id = "b", isEditedByUser = false, translatedText = null)
        val result = TranslationValidator.validate(listOf("a", "b"), listOf(SegmentTranslation("a", "Replacement", 1f)))
        val updated = TranslationValidator.apply(listOf(segment, missing), result)
        assertEquals(segment, updated[0])
        assertTrue(updated[1].needsRetry)
    }
}
