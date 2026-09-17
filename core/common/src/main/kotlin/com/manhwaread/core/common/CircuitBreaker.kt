package com.manhwaread.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Состояние автомата предохранителя. */
enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

/**
 * Предохранитель (circuit breaker) «на источник»:
 * - CLOSED: пропускает все вызовы; [failureThreshold] последовательных ошибок → OPEN;
 * - OPEN: вызовы отклоняются сразу с [AppError.SourceUnavailable]; после [coolDownMs] → HALF_OPEN;
 * - HALF_OPEN: пропускает ровно один пробный вызов; успех → CLOSED, ошибка → снова OPEN.
 *
 * Потокобезопасен: переходы состояния под [Mutex]. Время инжектируется через [timeMs]
 * (в тестах — управляемые часы).
 */
class CircuitBreaker(
    private val failureThreshold: Int = 10,
    private val coolDownMs: Long = 60_000L,
    private val timeMs: () -> Long = System::currentTimeMillis,
) {
    init {
        require(failureThreshold >= 1) { "failureThreshold must be >= 1" }
        require(coolDownMs >= 0L) { "coolDownMs must be >= 0" }
    }

    private val mutex = Mutex()
    private var consecutiveFailures = 0
    private var openedAtMs = 0L
    private var probeInFlight = false
    private val _state = MutableStateFlow(CircuitState.CLOSED)

    /** Текущее состояние для UI/диагностики. */
    val state: StateFlow<CircuitState> = _state.asStateFlow()

    /**
     * Выполняет [block] через предохранитель.
     * При OPEN возвращает [AppError.SourceUnavailable], не вызывая [block].
     * Исключение из [block] трактуется как ошибка ([AppError.Unknown]).
     */
    suspend fun <T> execute(block: suspend () -> DomainResult<T>): DomainResult<T> {
        val probe = mutex.withLock { beforeCall() }
            ?: return DomainResult.Failure(AppError.SourceUnavailable)
        val result = try {
            block()
        } catch (e: IOException) {
            DomainResult.Failure(AppError.Network(e))
        } catch (e: Exception) {
            DomainResult.Failure(AppError.Unknown(e))
        }
        mutex.withLock { afterCall(probe = probe, success = result.isSuccess) }
        return result
    }

    // Вызывается под mutex. Возвращает: null — вызов отклонён,
    // true — это пробный вызов HALF_OPEN, false — обычный вызов CLOSED.
    private fun beforeCall(): Boolean? = when (_state.value) {
        CircuitState.CLOSED -> false
        CircuitState.OPEN -> {
            if (timeMs() - openedAtMs >= coolDownMs) {
                _state.value = CircuitState.HALF_OPEN
                probeInFlight = true
                true
            } else {
                null
            }
        }
        CircuitState.HALF_OPEN -> {
            if (probeInFlight) {
                null
            } else {
                probeInFlight = true
                true
            }
        }
    }

    // Вызывается под mutex после завершения вызова.
    private fun afterCall(probe: Boolean, success: Boolean) {
        if (probe) {
            probeInFlight = false
            if (success) {
                consecutiveFailures = 0
                _state.value = CircuitState.CLOSED
            } else {
                openedAtMs = timeMs()
                _state.value = CircuitState.OPEN
            }
            return
        }
        if (success) {
            consecutiveFailures = 0
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= failureThreshold) {
                openedAtMs = timeMs()
                _state.value = CircuitState.OPEN
            }
        }
    }
}

/**
 * Набор предохранителей «по источнику»: на каждый ключ — отдельный [CircuitBreaker].
 */
class CircuitBreakerRegistry(
    private val failureThreshold: Int = 10,
    private val coolDownMs: Long = 60_000L,
    private val timeMs: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val byKey = HashMap<String, CircuitBreaker>()

    /** Возвращает (или создаёт) предохранитель для [key] — например, sourceId. */
    fun forSource(key: String): CircuitBreaker = synchronized(lock) {
        byKey.getOrPut(key) { CircuitBreaker(failureThreshold, coolDownMs, timeMs) }
    }
}
