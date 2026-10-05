package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Канал уведомлений об ошибках очереди (ФАЗА 15 / Этап 2):
 * передаёт критические ошибки перевода (401 ProviderAuth, 402/456 ProviderQuota)
 * в UI для показа Snackbar с быстрым переходом в «Настройки».
 */
@Singleton
class QueueErrorNotifier @Inject constructor() {
    private val _errors = Channel<AppError>(capacity = Channel.BUFFERED)
    val errors: Flow<AppError> = _errors.receiveAsFlow()

    fun notify(error: AppError) {
        _errors.trySend(error)
    }

    internal fun poll(): AppError? = _errors.tryReceive().getOrNull()
}
