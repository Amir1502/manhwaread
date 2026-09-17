package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.vision.DetectedLang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// DeepL API (free/pro): пакетный перевод массива строк с сохранением порядка.
// Ответ {"translations":[{"text":...}]} проецируется обратно на id сегментов
// и упаковывается в формат validate() — JSON-массив [{"id","text"}].
class DeepLProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
) : TranslationProvider {
    override val id: String = ProviderIds.DEEPL

    override val displayName: String = "DeepL"

    // DeepL переводит на фиксированный набор языков; продукт пишет по-русски.
    override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = targetLang == TARGET_LANG_RU

    override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> {
        if (config.apiKey.isBlank()) {
            return DomainResult.failure(AppError.ProviderAuth)
        }
        if (request.segments.isEmpty()) {
            return DomainResult.failure(AppError.ProviderBadResponse("empty batch for $id"))
        }
        val baseUrl = (config.baseUrl?.takeIf { value -> value.isNotBlank() } ?: DEFAULT_BASE_URL).trimEnd('/')
        val body = buildJsonObject {
            put(
                "text",
                buildJsonArray {
                    for (segment in request.segments) {
                        add(segment.text)
                    }
                },
            )
            put("target_lang", request.targetLang.uppercase())
            // UNKNOWN → source_lang не передаём: DeepL определит язык сам.
            sourceLangCode(request.sourceLang)?.let { code -> put("source_lang", code) }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val http = Request.Builder()
            .url("$baseUrl/translate")
            .addHeader(HEADER_AUTHORIZATION, "DeepL-Auth-Key ${config.apiKey}")
            .post(body)
            .build()
        val bodyResult = executeForBody(client, http)
        val rawResponse = bodyResult.getOrNull() ?: return bodyResult.propagateError()
        return try {
            val texts = extractTranslations(rawResponse)
            val ids = request.segments.map { segment -> segment.id }
            DomainResult.success(TranslationResponse(zipToSegmentsJson(ids, texts)))
        } catch (error: MalformedProviderResponseException) {
            DomainResult.failure(AppError.ProviderBadResponse(error.message.orEmpty()))
        } catch (error: IllegalArgumentException) {
            DomainResult.failure(AppError.ProviderBadResponse("malformed JSON: ${error.message}"))
        }
    }

    // translations[*].text — в порядке запроса.
    internal fun extractTranslations(responseJson: String): List<String> {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val translations = root["translations"]?.jsonArray
            ?: throw MalformedProviderResponseException("no translations array in response")
        return translations.map { element ->
            element.jsonObject["text"]?.jsonPrimitive?.content
                ?: throw MalformedProviderResponseException("translation entry without text")
        }
    }

    private fun sourceLangCode(lang: DetectedLang): String? =
        when (lang) {
            DetectedLang.KO -> "KO"
            DetectedLang.JA -> "JA"
            DetectedLang.ZH -> "ZH"
            DetectedLang.EN -> "EN"
            DetectedLang.UNKNOWN -> null
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api-free.deepl.com/v2"
    }
}
