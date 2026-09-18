package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.RectF

// Чистое сопоставление результатов OCR в OcrLine: тестируется на JVM
// без ML Kit-нативных библиотек (тонкий адаптер — в MlKitOcrEngine).

// Значение по умолчанию, если распознаватель не сообщил уверенность.
const val DEFAULT_OCR_CONFIDENCE = 0.5f

// Порог IoU, при котором две строки считаются дубликатами.
const val DEDUPE_IOU_THRESHOLD = 0.8f

// BCP-47 код языка ML Kit → доменный язык.
fun mapRecognizedLanguage(code: String?): DetectedLang =
    when (code?.lowercase()?.take(LANG_PREFIX_LENGTH)) {
        "ja" -> DetectedLang.JA
        "ko" -> DetectedLang.KO
        "zh" -> DetectedLang.ZH
        "en" -> DetectedLang.EN
        else -> DetectedLang.UNKNOWN
    }

// Элемент распознанного текста → OcrLine; пустой текст и нулевой бокс
// отбрасываются, координаты нормализуются (left ≤ right, top ≤ bottom).
fun mapOcrElement(
    text: String?,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    confidence: Float?,
    lang: DetectedLang,
): OcrLine? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    val normalizedLeft = minOf(left, right).toFloat()
    val normalizedRight = maxOf(left, right).toFloat()
    val normalizedTop = minOf(top, bottom).toFloat()
    val normalizedBottom = maxOf(top, bottom).toFloat()
    return OcrLine(
        text = trimmed,
        bounds = RectF(normalizedLeft, normalizedTop, normalizedRight, normalizedBottom),
        confidence = confidence?.coerceIn(0f, 1f) ?: DEFAULT_OCR_CONFIDENCE,
        lang = lang,
    )
}

// Площадь прямоугольника (0 для вырожденных).
private fun areaOf(rect: RectF): Float {
    val width = rect.right - rect.left
    val height = rect.bottom - rect.top
    return if (width <= 0f || height <= 0f) 0f else width * height
}

// Intersection-over-union двух прямоугольников.
fun iou(first: RectF, second: RectF): Float {
    val interLeft = maxOf(first.left, second.left)
    val interTop = maxOf(first.top, second.top)
    val interRight = minOf(first.right, second.right)
    val interBottom = minOf(first.bottom, second.bottom)
    val interWidth = maxOf(0f, interRight - interLeft)
    val interHeight = maxOf(0f, interBottom - interTop)
    val intersection = interWidth * interHeight
    val union = areaOf(first) + areaOf(second) - intersection
    return if (union <= 0f) 0f else intersection / union
}

// Жадная дедупликация: строки сортируются по уверенности, перекрывающиеся
// (> threshold) с уже взятыми отбрасываются. Нужна для мультязыкового OCR,
// где одна и та же строка распознаётся двумя моделями.
fun dedupeOcrLines(lines: List<OcrLine>, iouThreshold: Float = DEDUPE_IOU_THRESHOLD): List<OcrLine> {
    val sorted = lines.sortedByDescending { line -> line.confidence }
    val kept = mutableListOf<OcrLine>()
    for (line in sorted) {
        val overlaps = kept.any { existing -> iou(existing.bounds, line.bounds) > iouThreshold }
        if (!overlaps) {
            kept += line
        }
    }
    return kept
}

// CJK-фильтр мультязыкового OCR: на латинских страницах (например, испанский
// сканлейт) движки KO/JA выдают мусор — строки без CJK-символов отбрасываются.
private val HANGUL_JAMO_RANGE = '\u1100'..'\u11FF'
private val HANGUL_COMPAT_JAMO_RANGE = '\u3130'..'\u318F'
private val HANGUL_SYLLABLES_RANGE = '\uAC00'..'\uD7AF'
private val KANA_RANGE = '\u3040'..'\u30FF'
private val CJK_UNIFIED_RANGE = '\u4E00'..'\u9FFF'

private val CJK_RANGES =
    listOf(HANGUL_JAMO_RANGE, HANGUL_COMPAT_JAMO_RANGE, HANGUL_SYLLABLES_RANGE, KANA_RANGE, CJK_UNIFIED_RANGE)

// Языки «CJK-движков»: их строки достоверны только при наличии CJK-символов.
private val CJK_ENGINE_LANGS = setOf(DetectedLang.KO, DetectedLang.JA)

// Содержит ли текст хотя бы один CJK-символ (хангыль, кана, унифицированные иероглифы).
fun containsCjk(text: String): Boolean = text.any { char -> CJK_RANGES.any { range -> char in range } }

// Строки движков KO/JA без CJK-символов отбрасываются; строки EN/UNKNOWN не трогаются.
fun dropCjkEngineJunk(lines: List<OcrLine>): List<OcrLine> =
    lines.filter { line -> line.lang !in CJK_ENGINE_LANGS || containsCjk(line.text) }

private const val LANG_PREFIX_LENGTH = 2
