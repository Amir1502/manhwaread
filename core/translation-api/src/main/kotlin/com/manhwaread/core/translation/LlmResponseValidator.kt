package com.manhwaread.core.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Валидация ответа LLM (КОНТРАКТ ФАЗЫ 6 — сигнатуры закреплены в AGENTS.md).
 * Принимает JSON-массив [{"id","text"}] (или объект-обёртку, или ответ в markdown-ограждении).
 *
 * Разбирает сырой ответ и сопоставляет его с [expectedIds]:
 * полный состав → Valid; расхождение → Partial (в segments только совпавшие,
 * в порядке expectedIds); структурный брак → Invalid.
 */
fun validate(rawJson: String, expectedIds: List<String>): ValidationResult = try {
    val root = Json.parseToJsonElement(stripCodeFences(rawJson))
    val array = extractArray(root) ?: throw ValidationException("root is not an array of segments")
    matchSegments(parseSegments(array), expectedIds)
} catch (e: ValidationException) {
    ValidationResult.Invalid(e.reason)
} catch (e: IllegalArgumentException) {
    ValidationResult.Invalid("invalid JSON: ${e.message}")
}

// Служебное исключение с причиной для ветки Invalid (не SwallowedException: reason используется).
private class ValidationException(val reason: String) : Exception(reason)

// Ответы LLM часто приходят в markdown-ограждении ```json ... ``` — снимаем его.
private fun stripCodeFences(raw: String): String {
    var text = raw.trim()
    if (text.startsWith("```")) {
        text = text.removePrefix("```json").removePrefix("```")
        val end = text.lastIndexOf("```")
        if (end >= 0) text = text.substring(0, end)
    }
    return text.trim()
}

private fun extractArray(root: JsonElement): JsonArray? = when (root) {
    is JsonArray -> root
    is JsonObject -> listOf("segments", "results", "translations", "data")
        .firstNotNullOfOrNull { key -> root[key] as? JsonArray }
    else -> null
}

private fun parseSegments(array: JsonArray): List<TranslatedSegment> {
    val segments = mutableListOf<TranslatedSegment>()
    val seen = mutableSetOf<String>()
    for ((index, element) in array.withIndex()) {
        val segment = parseSegment(element)
            ?: throw ValidationException("segment #$index is not an object with string id and text")
        requireUnique(seen, segment)
        if (segment.text.isNotBlank()) segments += segment
    }
    return segments
}

private fun parseSegment(element: JsonElement): TranslatedSegment? {
    val obj = element as? JsonObject ?: return null
    val id = stringField(obj, "id")
    val text = stringField(obj, "text")
    if (id == null || text == null) return null
    return TranslatedSegment(id, text)
}

private fun stringField(obj: JsonObject, key: String): String? {
    val primitive = obj[key] as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    return primitive.content
}

private fun requireUnique(seen: MutableSet<String>, segment: TranslatedSegment) {
    if (!seen.add(segment.id)) throw ValidationException("duplicate segment id: ${segment.id}")
}

// Пустой текст — сегмент считается отсутствующим (missing): LLM не перевёл его.
private fun matchSegments(segments: List<TranslatedSegment>, expectedIds: List<String>): ValidationResult {
    val byId = segments.associateBy { it.id }
    val matched = expectedIds.mapNotNull { byId[it] }
    val missing = expectedIds.filterNot { byId.containsKey(it) }
    val unexpected = segments.map { it.id }.filterNot { expectedIds.contains(it) }
    return if (missing.isEmpty() && unexpected.isEmpty()) {
        ValidationResult.Valid(matched)
    } else {
        ValidationResult.Partial(matched, missing, unexpected)
    }
}
