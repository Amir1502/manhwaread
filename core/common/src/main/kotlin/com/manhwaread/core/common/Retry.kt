package com.manhwaread.core.common

import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Выполняет [block] с повторами по [config]. [block] получает номер попытки (от 0)
 * и возвращает [DomainResult]; повторяются только ошибки, подходящие под `retryOn`.
 */
suspend fun <T> withRetry(
    config: RetryConfig = RetryConfig(),
    block: suspend (attempt: Int) -> DomainResult<T>,
): DomainResult<T> {
    var attempt = 0
    var result = block(attempt)
    while (true) {
        val failure = result as? DomainResult.Failure ?: return result
        if (attempt + 1 >= config.maxAttempts || !config.retryOn(failure.error)) return result
        delay(computeDelayMs(failure.error, attempt, config))
        attempt++
        result = block(attempt)
    }
}

/**
 * Расчёт паузы перед повтором: backoff + джиттер, для RateLimited — не меньше
 * `retryAfterMs`. Internal — для прямых unit-тестов без виртуального времени.
 */
internal fun computeDelayMs(error: AppError, attempt: Int, config: RetryConfig): Long {
    val base = (config.initialDelayMs * config.multiplier.pow(attempt)).toLong()
        .coerceIn(0L, config.maxDelayMs)
    val jittered = if (config.jitterFraction > 0.0) {
        val delta = (base * config.jitterFraction).toLong()
        if (delta <= 0L) {
            base
        } else {
            (base - delta + config.random.nextLong(2 * delta + 1)).coerceIn(0L, config.maxDelayMs)
        }
    } else {
        base
    }
    val retryAfter = (error as? AppError.RateLimited)?.retryAfterMs ?: 0L
    return maxOf(jittered, retryAfter.coerceAtLeast(0L))
}
