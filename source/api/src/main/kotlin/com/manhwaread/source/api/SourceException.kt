package com.manhwaread.source.api

import com.manhwaread.core.common.AppError
import java.io.IOException

/**
 * Единое исключение источников: переносит доменную ошибку [AppError].
 * Контракт Source возвращает значения напрямую, поэтому сбои передаются
 * этим исключением; вызывающий слой извлекает [error] и не парсит строки.
 * [cause] сохраняет исходное исключение для диагностики.
 */
class SourceException(
    val error: AppError,
    message: String = error.toString(),
    cause: Throwable? = null,
) : IOException(message, cause)
