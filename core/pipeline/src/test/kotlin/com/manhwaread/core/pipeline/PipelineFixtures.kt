package com.manhwaread.core.pipeline

import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.TextSegment

// Общие фикстуры тестов модуля :core:pipeline.
internal object PipelineFixtures {
    fun segment(id: String) = TextSegment(
        id = id,
        bubbleId = "b-$id",
        pageIndex = 0,
        ocrText = "안녕",
        ocrLang = DetectedLang.KO,
        ocrConfidence = 0.9f,
        readingOrder = 0,
    )

    fun overlay(bubbleId: String) = OverlaySpec(
        bubbleId = bubbleId,
        pageIndex = 0,
        lines = listOf(OverlayLine("привет", PointF(1f, 2f), 30f)),
        sizePx = 20f,
        lineSpacingMult = 1f,
        letterSpacing = 0f,
        scaleX = 1f,
    )

    fun testJob(
        id: String = "job-1",
        priority: Int = 0,
        createdAt: Long = 0L,
        state: JobState = JobState(),
    ) = ChapterJob(
        id = id,
        ref = ChapterRef(sourceId = 1L, mangaId = 10L, chapterId = 100L, chapterUrl = "/ch/100"),
        priority = priority,
        createdAt = createdAt,
        state = state,
    )
}
