package com.manhwaread.core.network

import com.manhwaread.core.common.AppError
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Маппинг исключений сетевого слоя в доменные ошибки [AppError].
 * Источники используют его в обёртках вызовов OkHttp.
 */
fun Throwable.asAppError(): AppError = when (this) {
    is CloudflareBlockedException -> AppError.CloudflareBlocked
    // Таймауты (SocketTimeoutException) — подкласс InterruptedIOException.
    is InterruptedIOException -> AppError.Network(this)
    is IOException -> AppError.Network(this)
    else -> AppError.Unknown(this)
}
