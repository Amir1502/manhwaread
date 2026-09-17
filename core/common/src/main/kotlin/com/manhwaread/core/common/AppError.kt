package com.manhwaread.core.common

/**
 * Доменные ошибки приложения.
 * Закреплённый контракт (AGENTS.md) — не менять.
 */
sealed interface AppError {
    data class Network(val cause: Throwable) : AppError
    data object SourceUnavailable : AppError
    data object SourceLayoutChanged : AppError
    data object CloudflareBlocked : AppError
    data class RateLimited(val retryAfterMs: Long?) : AppError
    data object ProviderAuth : AppError
    data object ProviderQuota : AppError
    data class ProviderBadResponse(val reason: String) : AppError
    data object OcrFailed : AppError
    data object VisionFailed : AppError
    data class TypesetOverflow(val bubbleId: String) : AppError
    data object StorageFull : AppError
    data class Unknown(val cause: Throwable) : AppError
}
