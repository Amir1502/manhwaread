package com.manhwaread.core.vision

/**
 * Доменные модели зрения (закреплённый контракт ФАЗЫ 5 — AGENTS.md).
 * Геометрия своя, НЕ android.graphics.*.
 */
enum class BubbleKind { SPEECH, THOUGHT, NARRATION_BOX, SHOUT, WHISPER, SFX, OTHER }

/** Язык, распознанный в сегменте текста (влияет на выбор OCR-модели и промта). */
enum class DetectedLang { KO, JA, ZH, EN, UNKNOWN }

/**
 * Бабл (облачко реплики): полигон внутренней области + ограничивающий прямоугольник.
 * [fillColor] — доминирующий цвет фона для отрисовки подложки, [tailPoints] — хвост.
 */
data class Bubble(
    val id: String,
    val pageIndex: Int,
    val polygon: List<PointF>,
    val bounds: RectF,
    val kind: BubbleKind,
    val tailPoints: List<PointF> = emptyList(),
    val fillColor: Int? = null,
    val zOrder: Int = 0,
)

/**
 * Сегмент текста из OCR, привязанный к баблу. [translatedText] заполняется
 * пайплайном перевода; [isEditedByUser] защищает ручные правки от перезаписи.
 */
data class TextSegment(
    val id: String,
    val bubbleId: String,
    val pageIndex: Int,
    val ocrText: String,
    val ocrLang: DetectedLang,
    val ocrConfidence: Float,
    val readingOrder: Int,
    val isSfx: Boolean = false,
    val translatedText: String? = null,
    val isEditedByUser: Boolean = false,
    val needsRetry: Boolean = false,
)
