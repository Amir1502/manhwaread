package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError

// Идентификаторы поддерживаемых провайдеров перевода.
object ProviderIds {
    const val OPENAI_COMPAT = "openai-compat"
    const val GEMINI = "gemini"
    const val DEEPL = "deepl"
}

/**
 * Настройки подключения провайдера. [apiKey] маскируется в toString —
 * секрет не должен попадать в логи (DoD «ключи не в логах»).
 */
data class ProviderConfig(
    val id: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val model: String? = null,
) {
    override fun toString(): String = "ProviderConfig(id=$id, apiKey=***, baseUrl=$baseUrl, model=$model)"
}

// HTTP-код ответа провайдера → доменная ошибка.
// 456 — «quota exhausted» у DeepL; 429 — rate limit у всех.
fun httpErrorFor(statusCode: Int, retryAfterHeader: String?): AppError =
    when {
        statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> AppError.ProviderAuth
        statusCode == HTTP_PAYMENT_REQUIRED || statusCode == DEEPL_QUOTA_STATUS -> AppError.ProviderQuota
        statusCode == HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited(parseRetryAfterMs(retryAfterHeader))
        else -> AppError.ProviderBadResponse("HTTP $statusCode")
    }

// Retry-After в секундах (HTTP-date формат не поддерживаем — null).
fun parseRetryAfterMs(headerValue: String?): Long? = headerValue?.trim()?.toLongOrNull()?.times(MILLIS_IN_SECOND)

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_PAYMENT_REQUIRED = 402
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DEEPL_QUOTA_STATUS = 456
private const val MILLIS_IN_SECOND = 1_000L
