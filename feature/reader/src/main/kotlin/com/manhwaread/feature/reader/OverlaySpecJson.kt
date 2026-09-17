package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlayAlign
import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// OverlaySpec <-> JSON: ручной кодек, так как :core:vision-model — закреплённый
// JVM-модуль без плагина serialization. Файл overlays.json хранит векторный
// слой перевода главы (перевод НЕ запекается в растр — ключевое решение продукта).
object OverlaySpecJson {
    fun encode(specs: List<OverlaySpec>): String =
        buildJsonArray { specs.forEach { spec -> add(specToJson(spec)) } }.toString()

    fun decode(raw: String): List<OverlaySpec> =
        parseArray(raw).map { element -> specFromJson(element.jsonObject) }

    private fun parseArray(raw: String): JsonArray =
        runCatching { Json.parseToJsonElement(raw).jsonArray }
            .getOrElse { throw IllegalArgumentException("overlays.json: expected JSON array", it) }

    private fun specToJson(spec: OverlaySpec): JsonObject = buildJsonObject {
        put("bubbleId", spec.bubbleId)
        put("pageIndex", spec.pageIndex)
        put("sizePx", spec.sizePx)
        put("lineSpacingMult", spec.lineSpacingMult)
        put("letterSpacing", spec.letterSpacing)
        put("scaleX", spec.scaleX)
        spec.fontFamily?.let { family -> put("fontFamily", family) }
        put("colorArgb", spec.colorArgb)
        put("align", spec.align.name)
        putJsonArray("lines") {
            spec.lines.forEach { line ->
                addJsonObject {
                    put("text", line.text)
                    put("x", line.baselineStart.x)
                    put("y", line.baselineStart.y)
                    put("widthPx", line.widthPx)
                }
            }
        }
    }

    private fun specFromJson(json: JsonObject): OverlaySpec = OverlaySpec(
        bubbleId = json.stringOf("bubbleId"),
        pageIndex = json.intOf("pageIndex"),
        lines = json.valueOf("lines").jsonArray.map { element -> lineFromJson(element.jsonObject) },
        sizePx = json.floatOf("sizePx"),
        lineSpacingMult = json.floatOf("lineSpacingMult"),
        letterSpacing = json.floatOf("letterSpacing"),
        scaleX = json.floatOf("scaleX"),
        fontFamily = json["fontFamily"]?.jsonPrimitive?.contentOrNull,
        colorArgb = json.intOf("colorArgb"),
        align = OverlayAlign.valueOf(json.stringOf("align")),
    )

    private fun lineFromJson(json: JsonObject): OverlayLine = OverlayLine(
        text = json.stringOf("text"),
        baselineStart = PointF(x = json.floatOf("x"), y = json.floatOf("y")),
        widthPx = json.floatOf("widthPx"),
    )

    // Явное чтение полей: отсутствующее поле — IllegalArgumentException с именем поля.
    private fun JsonObject.valueOf(key: String) =
        this[key] ?: throw IllegalArgumentException("overlays.json: missing field '$key'")

    private fun JsonObject.stringOf(key: String): String = valueOf(key).jsonPrimitive.content

    private fun JsonObject.intOf(key: String): Int = valueOf(key).jsonPrimitive.int

    private fun JsonObject.floatOf(key: String): Float = valueOf(key).jsonPrimitive.float
}
