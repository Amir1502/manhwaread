package com.manhwaread.core.translation

import com.manhwaread.core.model.TextSegment

data class SegmentTranslation(val id: String, val text: String, val confidence: Float, val notes: String = "")

data class ValidatedTranslation(
    val accepted: List<SegmentTranslation>,
    val retryIds: Set<String>,
    val unexpectedIds: Set<String>,
) {
    val complete: Boolean get() = retryIds.isEmpty() && unexpectedIds.isEmpty()
}

object TranslationValidator {
    fun validate(expectedIds: List<String>, response: List<SegmentTranslation>): ValidatedTranslation {
        require(expectedIds.all { it.isNotBlank() })
        require(expectedIds.distinct().size == expectedIds.size)
        val expected = expectedIds.toSet()
        val grouped = response.groupBy { it.id }
        val accepted = expectedIds.mapNotNull { id ->
            grouped[id]?.singleOrNull()?.takeIf {
                it.text.isNotBlank() && it.confidence.isFinite() && it.confidence in 0f..1f
            }
        }
        return ValidatedTranslation(
            accepted = accepted,
            retryIds = expected - accepted.map { it.id }.toSet(),
            unexpectedIds = grouped.keys - expected,
        )
    }

    // Ручные правки сохраняются даже при успешном повторном переводе всей главы.
    fun apply(segments: List<TextSegment>, response: ValidatedTranslation): List<TextSegment> {
        val byId = response.accepted.associateBy { it.id }
        return segments.map { segment ->
            if (segment.isEditedByUser) segment
            else {
                val translated = byId[segment.id]
                when {
                    translated != null -> segment.copy(translatedText = translated.text, needsRetry = false)
                    segment.id in response.retryIds -> segment.copy(needsRetry = true)
                    else -> segment
                }
            }
        }
    }
}
