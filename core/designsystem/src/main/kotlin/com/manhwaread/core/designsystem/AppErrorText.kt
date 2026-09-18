package com.manhwaread.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.manhwaread.core.common.AppError

// Общий маппер AppError → локализованный текст (ФАЗА 14). Экраны каталога,
// карточки и остальных разделов показывают ошибки через него, чтобы формулировки
// и переводы (values-ru) были едиными во всём приложении.

/** Строковый ресурс для ошибки без аргументов (используется в превью и тестах). */
@StringRes
fun appErrorTextRes(error: AppError): Int = when (error) {
    is AppError.Network -> R.string.ds_error_network
    is AppError.SourceUnavailable -> R.string.ds_error_source_unavailable
    is AppError.SourceLayoutChanged -> R.string.ds_error_source_layout_changed
    is AppError.CloudflareBlocked -> R.string.ds_error_cloudflare_blocked
    is AppError.RateLimited -> R.string.ds_error_rate_limited
    is AppError.ProviderAuth -> R.string.ds_error_provider_auth
    is AppError.ProviderQuota -> R.string.ds_error_provider_quota
    is AppError.ProviderBadResponse -> R.string.ds_error_provider_bad_response
    is AppError.OcrFailed -> R.string.ds_error_ocr_failed
    is AppError.VisionFailed -> R.string.ds_error_vision_failed
    is AppError.TypesetOverflow -> R.string.ds_error_typeset_overflow
    is AppError.StorageFull -> R.string.ds_error_storage_full
    is AppError.Unknown -> R.string.ds_error_unknown
}

/** Пользовательский текст ошибки с подстановкой аргументов, где они есть. */
@Composable
fun appErrorText(error: AppError): String = when (error) {
    is AppError.ProviderBadResponse ->
        stringResource(R.string.ds_error_provider_bad_response, error.reason)
    is AppError.TypesetOverflow ->
        stringResource(R.string.ds_error_typeset_overflow, error.bubbleId)
    else -> stringResource(appErrorTextRes(error))
}
