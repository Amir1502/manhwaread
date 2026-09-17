package com.manhwaread.core.model

// Все координаты относятся к исходной странице, а не к экрану или уменьшенной копии.
data class PointF(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite()) }
}

data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(right > left && bottom > top)
    }
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class MaskRef(val relativePath: String, val originX: Int, val originY: Int, val width: Int, val height: Int) {
    init {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/') && '\\' !in relativePath)
        require(relativePath.split('/').none { it == ".." || it == "." || it.isBlank() })
        require(originX >= 0 && originY >= 0 && width > 0 && height > 0)
    }
}

enum class BubbleKind { SPEECH, THOUGHT, NARRATION_BOX, SHOUT, WHISPER, SFX, OTHER }
enum class DetectedLang { KO, JA, ZH, EN, UNKNOWN }

data class Bubble(
    val id: String,
    val pageIndex: Int,
    val polygon: List<PointF>,
    val bounds: RectF,
    val innerMask: MaskRef,
    val kind: BubbleKind,
    val tailPoints: List<PointF>,
    val fillColor: Int?,
    val zOrder: Int,
) {
    init { require(id.isNotBlank() && pageIndex >= 0 && polygon.size >= 3) }
}

data class TextSegment(
    val id: String,
    val bubbleId: String,
    val pageIndex: Int,
    val ocrText: String,
    val ocrLang: DetectedLang,
    val ocrConfidence: Float,
    val ocrLineBoxes: List<RectF>,
    val readingOrder: Int,
    val translatedText: String?,
    val isSfx: Boolean,
    val isEditedByUser: Boolean,
    val needsRetry: Boolean,
) {
    init {
        require(id.isNotBlank() && bubbleId.isNotBlank() && pageIndex >= 0 && readingOrder >= 0)
        require(ocrConfidence.isFinite() && ocrConfidence in 0f..1f)
    }
}

data class OverlayLine(
    val text: String,
    val baseline: PointF,
    val fontId: String,
    val fontSizePx: Float,
    val color: Int,
    val strokeColor: Int?,
    val strokeWidthPx: Float,
    val letterSpacingEm: Float,
    val textScaleX: Float,
    val rotationDegrees: Float,
) {
    init {
        require(fontId.isNotBlank())
        require(fontSizePx.isFinite() && fontSizePx > 0f)
        require(strokeWidthPx.isFinite() && strokeWidthPx >= 0f)
        require(letterSpacingEm.isFinite() && textScaleX.isFinite() && textScaleX in 0.75f..1f)
        require(rotationDegrees.isFinite())
    }
}

data class BubbleOverlay(val bubbleId: String, val lines: List<OverlayLine>, val overflow: Boolean, val hidden: Boolean = false)

data class OverlaySpec(val bubbles: List<BubbleOverlay>, val version: Int = 1) {
    init {
        require(version > 0)
        require(bubbles.map { it.bubbleId }.distinct().size == bubbles.size)
    }
}

data class TranslatedPage(
    val pageIndex: Int,
    val sourceImageHash: String,
    val bubbles: List<Bubble>,
    val segments: List<TextSegment>,
    val inpaintedImagePath: String?,
    val overlaySpec: OverlaySpec,
    val version: Int,
) {
    init {
        require(pageIndex >= 0 && version > 0)
        require(sourceImageHash.matches(Regex("[a-f0-9]{64}")))
        val bubbleIds = bubbles.map { it.id }.toSet()
        require(bubbleIds.size == bubbles.size)
        require(bubbles.all { it.pageIndex == pageIndex })
        require(segments.map { it.id }.distinct().size == segments.size)
        require(segments.all { it.pageIndex == pageIndex && it.bubbleId in bubbleIds })
        require(overlaySpec.bubbles.all { it.bubbleId in bubbleIds })
    }
}
