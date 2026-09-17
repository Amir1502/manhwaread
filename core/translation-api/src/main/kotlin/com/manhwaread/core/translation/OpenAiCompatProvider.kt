package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.network.asAppError
import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// OpenAI-совместимый провайдер (OpenAI, OpenRouter, локальные llama.cpp/vLLM
// и любые /chat/completions-эндпоинты). Ответ модели — JSON-массив
// [{"id","text"}], который ниже по конвейеру проходит validate().
class OpenAiCompatProvider(
    private val config: ProviderConfig,
    private val client: OkHttpClient,
) : TranslationProvider {
    override val id: String = ProviderIds.OPENAI_COMPAT

    override val displayName: String = "OpenAI-compatible"

    // LLM не привязана к языковой паре — поддерживаем любую комбинацию.
    override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = true

    override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> {
        val baseUrl = config.baseUrl?.trimEnd('/')
        if (baseUrl.isNullOrBlank()) {
            return DomainResult.failure(AppError.ProviderBadResponse("baseUrl is required for $id"))
        }
        if (config.apiKey.isBlank()) {
            return DomainResult.failure(AppError.ProviderAuth)
        }
        val body = buildJsonObject {
            put("model", config.model?.takeIf { model -> model.isNotBlank() } ?: DEFAULT_MODEL)
            put("temperature", TEMPERATURE)
            putMessages(request)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val http = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader(HEADER_AUTHORIZATION, "Bearer ${config.apiKey}")
            .post(body)
            .build()
        val bodyResult = executeForBody(client, http)
        val rawResponse = bodyResult.getOrNull() ?: return bodyResult.propagateError()
        return try {
            DomainResult.success(TranslationResponse(extractChatContent(rawResponse)))
        } catch (error: MalformedProviderResponseException) {
            DomainResult.failure(AppError.ProviderBadResponse(error.message.orEmpty()))
        } catch (error: IllegalArgumentException) {
            DomainResult.failure(AppError.ProviderBadResponse("malformed JSON: ${error.message}"))
        }
    }

    private fun JsonObjectBuilder.putMessages(request: TranslationRequest) {
        put(
            "messages",
            buildJsonArray {
                addJsonObject {
                    put("role", "system")
                    put("content", TranslationPromptBuilder.systemPrompt(request.targetLang))
                }
                addJsonObject {
                    put("role", "user")
                    put("content", TranslationPromptBuilder.userPrompt(request))
                }
            },
        )
    }

    // choices[0].message.content — сырой JSON перевода от модели.
    internal fun extractChatContent(responseJson: String): String {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val content = root["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
        return content ?: throw MalformedProviderResponseException("no choices[0].message.content in response")
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val TEMPERATURE = 0.2
    }
}

// Структурный брак ответа провайдера: отображается в ProviderBadResponse.
class MalformedProviderResponseException(reason: String) : RuntimeException(reason)

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
internal const val HEADER_AUTHORIZATION = "Authorization"
internal const val HEADER_RETRY_AFTER = "Retry-After"

// Универсальная сборка JSON-массива [{"id","text"}] из параллельных списков
// (DeepL возвращает переводы в порядке запроса — восстанавливаем id).
internal fun zipToSegmentsJson(ids: List<String>, texts: List<String>): String =
    buildJsonArray {
        for (index in ids.indices) {
            addJsonObject {
                put("id", ids[index])
                put("text", texts.getOrElse(index) { "" })
            }
        }
    }.toString()

// Общий HTTP-вызов провайдеров: код != 2xx → httpErrorFor, IO → asAppError.
internal suspend fun executeForBody(client: OkHttpClient, request: Request): DomainResult<String> =
    withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    DomainResult.success(text)
                } else {
                    DomainResult.failure(httpErrorFor(response.code, response.header(HEADER_RETRY_AFTER)))
                }
            }
        } catch (io: IOException) {
            DomainResult.failure(io.asAppError())
        }
    }

// Провал получения тела пробрасывается в тип результата провайдера.
// Вызывается только когда getOrNull() == null, т.е. ветка всегда Failure.
internal fun DomainResult<String>.propagateError(): DomainResult<TranslationResponse> =
    DomainResult.failure((this as DomainResult.Failure).error)
