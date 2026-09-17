package com.manhwaread.core.translation

import com.manhwaread.core.vision.DetectedLang
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Строитель промтов для пакетного перевода через LLM.
 * Ожидаемый формат ответа — JSON-массив [{"id","text"}], поэтому промт
 * явно требует вернуть только массив с теми же id.
 */
object TranslationPromptBuilder {
    /** Системный промт: роль переводчика манхвы, стиль, формат ответа. */
    fun systemPrompt(targetLang: String = TARGET_LANG_RU): String = buildString {
        append("You are a professional manhwa and manga translator. ")
        append("Translate every segment into ${languageName(targetLang)}, keeping tone, register and speech quirks. ")
        append("Keep segments short enough to fit inside speech bubbles. ")
        append("For SFX prefer a short expressive equivalent or transliteration. ")
        append("Do not add comments, explanations or markdown. ")
        append("Reply with a strict JSON array of objects {\"id\": string, \"text\": string} and nothing else.")
    }

    /** Пользовательский промт: язык оригинала, требуемые id, контекст и payload сегментов. */
    fun userPrompt(request: TranslationRequest): String = buildString {
        append("Source language: ")
        append(sourceLangName(request.sourceLang))
        append(".\n")
        append("Translate the segments below.\n")
        append("Return ONLY a JSON array with exactly these ids: ")
        append(request.segments.joinToString(", ") { it.id })
        append(".\n")
        val hint = request.contextHint
        if (!hint.isNullOrBlank()) {
            append("Context: ")
            append(hint)
            append('\n')
        }
        append(encodeSegments(request.segments))
    }

    /** Компактный JSON payload сегментов; флаг "sfx" добавляется только для звукоподражаний. */
    fun encodeSegments(segments: List<TranslatableSegment>): String =
        buildJsonArray {
            for (segment in segments) {
                add(segmentJson(segment))
            }
        }.toString()

    private fun segmentJson(segment: TranslatableSegment) = buildJsonObject {
        put("id", segment.id)
        put("text", segment.text)
        if (segment.isSfx) put("sfx", true)
    }

    private fun languageName(code: String): String = when (code) {
        "ru" -> "Russian"
        "en" -> "English"
        "ko" -> "Korean"
        "ja" -> "Japanese"
        "zh" -> "Chinese"
        else -> code
    }

    private fun sourceLangName(lang: DetectedLang): String = when (lang) {
        DetectedLang.KO -> "Korean"
        DetectedLang.JA -> "Japanese"
        DetectedLang.ZH -> "Chinese"
        DetectedLang.EN -> "English"
        DetectedLang.UNKNOWN -> "unknown (detect it automatically)"
    }
}
