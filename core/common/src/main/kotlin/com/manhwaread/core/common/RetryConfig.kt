package com.manhwaread.core.common

import kotlin.random.Random

/**
 * Параметры повторов: экспоненциальный backoff с джиттером.
 *
 * - базовая задержка: `initialDelayMs * multiplier^attempt`, не выше `maxDelayMs`;
 * - джиттер: ±[jitterFraction] от базовой задержки (с фиксированным [random] детерминирован);
 * - [AppError.RateLimited] с `retryAfterMs`: фактическая пауза не меньше запрошенной сервером;
 * - [retryOn]: предикат повторяемых ошибок (по умолчанию Network и RateLimited).
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 8_000L,
    val multiplier: Double = 2.0,
    val jitterFraction: Double = 0.2,
    val retryOn: (AppError) -> Boolean = { it is AppError.Network || it is AppError.RateLimited },
    val random: Random = Random.Default,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, got $maxAttempts" }
        require(jitterFraction in 0.0..1.0) { "jitterFraction must be in 0.0..1.0" }
    }
}
