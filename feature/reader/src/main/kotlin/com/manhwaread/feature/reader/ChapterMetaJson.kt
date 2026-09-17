package com.manhwaread.feature.reader

import com.manhwaread.core.vision.RectF
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Метаданные главы: chapter.json = { "title": ..., "bubbles": [...] }.
// Области баблов хранят пары текстов (оригинал + перевод) — контент
// карточки по тапу (DoD: «тап по баблу -> оригинал + перевод»).
data class ChapterMeta(
    val title: String,
    val bubbles: List<BubbleHitArea>,
)

object ChapterMetaJson {
    private const val EMPTY_ARRAY = "[]"

    fun encode(meta: ChapterMeta): String = buildJsonObject {
        put("title", meta.title)
        putJsonArray("bubbles") {
            meta.bubbles.forEach { bubble -> add(bubbleToJson(bubble)) }
        }
    }.toString()

    fun decode(raw: String): ChapterMeta {
        if (raw.isBlank() || raw == EMPTY_ARRAY) {
            return ChapterMeta(title = "", bubbles = emptyList())
        }
        val json = runCatching { Json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw IllegalArgumentException("chapter.json: expected JSON object", it) }
        val bubbles = json["bubbles"]?.jsonArray
            ?.map { element -> bubbleFromJson(element.jsonObject) }
            .orEmpty()
        return ChapterMeta(title = json["title"]?.jsonPrimitive?.content.orEmpty(), bubbles = bubbles)
    }

    private fun bubbleToJson(bubble: BubbleHitArea): JsonObject = buildJsonObject {
        put("bubbleId", bubble.bubbleId)
        put("pageIndex", bubble.pageIndex)
        putJsonObject("bounds") {
            put("left", bubble.bounds.left)
            put("top", bubble.bounds.top)
            put("right", bubble.bounds.right)
            put("bottom", bubble.bounds.bottom)
        }
        put("originalText", bubble.originalText)
        bubble.translatedText?.let { translated -> put("translatedText", translated) }
    }

    private fun bubbleFromJson(json: JsonObject): BubbleHitArea {
        val bounds = json.valueOf("bounds").jsonObject
        return BubbleHitArea(
            bubbleId = json.stringOf("bubbleId"),
            pageIndex = json.intOf("pageIndex"),
            bounds = RectF(
                left = bounds.floatOf("left"),
                top = bounds.floatOf("top"),
                right = bounds.floatOf("right"),
                bottom = bounds.floatOf("bottom"),
            ),
            originalText = json.stringOf("originalText"),
            translatedText = json["translatedText"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun JsonObject.valueOf(key: String) =
        this[key] ?: throw IllegalArgumentException("chapter.json: missing field '$key'")

    private fun JsonObject.stringOf(key: String): String = valueOf(key).jsonPrimitive.content

    private fun JsonObject.intOf(key: String): Int = valueOf(key).jsonPrimitive.int

    private fun JsonObject.floatOf(key: String): Float = valueOf(key).jsonPrimitive.float
}
