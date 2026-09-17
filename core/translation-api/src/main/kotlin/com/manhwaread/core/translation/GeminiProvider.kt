package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.vision.DetectedLang
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Google Gemini (generativelanguage.googleapis.com, REST generateContent).
// Ключ передаётся заголовком x-goog-api-key; ответ — текст первой part
// первого кандидата, содержащий JSON-массив [{"id","text"}].
class GeminiProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
) : TranslationProvider {
    override val id: String = ProviderIds.GEMINI

    override val displayName: String = "Google Gemini"

    // LLM не привязана к языковой паре — поддерживаем любую комбинацию.
    override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = true

    override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> {
        if (config.apiKey.isBlank()) {
            return DomainResult.failure(AppError.ProviderAuth)
        }
        val model = config.model?.takeIf { value -> value.isNotBlank() } ?: DEFAULT_MODEL
        // baseUrl необязателен (официальный API), но поддерживает прокси/тесты.
        val baseUrl = (config.baseUrl?.takeIf { value -> value.isNotBlank() } ?: BASE_URL).trimEnd('/')
        val body = buildJsonObject {
            putJsonObjectSystemInstruction(request.targetLang)
            put(
                "contents",
                buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put(
                            "parts",
                            buildJsonArray {
                                addJsonObject { put("text", TranslationPromptBuilder.userPrompt(request)) }
                            },
                        )
                    }
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", TEMPERATURE)
                    // Просим модель отвечать чистым JSON — меньше срабатываний stripCodeFences.
                    put("responseMimeType", "application/json")
                },
            )
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val http = Request.Builder()
            .url("$baseUrl/models/$model:generateContent")
            .addHeader(HEADER_API_KEY, config.apiKey)
            .post(body)
            .build()
        val bodyResult = executeForBody(client, http)
        val rawResponse = bodyResult.getOrNull() ?: return bodyResult.propagateError()
        return try {
            DomainResult.success(TranslationResponse(extractCandidateText(rawResponse)))
        } catch (error: MalformedProviderResponseException) {
            DomainResult.failure(AppError.ProviderBadResponse(error.message.orEmpty()))
        } catch (error: IllegalArgumentException) {
            DomainResult.failure(AppError.ProviderBadResponse("malformed JSON: ${error.message}"))
        }
    }

    private fun JsonObjectBuilder.putJsonObjectSystemInstruction(targetLang: String) {
        put(
            "system_instruction",
            buildJsonObject {
                put(
                    "parts",
                    buildJsonArray {
                        addJsonObject { put("text", TranslationPromptBuilder.systemPrompt(targetLang)) }
                    },
                )
            },
        )
    }

    // candidates[0].content.parts[0].text — JSON перевода от модели.
    internal fun extractCandidateText(responseJson: String): String {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val text = root["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        return text ?: throw MalformedProviderResponseException("no candidates[0].content.parts[0].text")
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        // VERIFY-API: имя модели задаёт пользователь в настройках; значение по
        // умолчанию может устареть — Gemini возвращает 404 для снятых моделей.
        const val DEFAULT_MODEL = "gemini-1.5-flash"
        const val TEMPERATURE = 0.2
        private const val HEADER_API_KEY = "x-goog-api-key"
    }
}
