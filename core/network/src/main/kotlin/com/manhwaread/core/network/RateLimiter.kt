package com.manhwaread.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Ограничитель запросов по доменам: не более [maxConcurrent] одновременных
 * операций и пауза не менее [minIntervalMs] между стартами в пределах домена.
 *
 * Все ожидания — через [delay], поэтому тесты работают на виртуальном времени
 * kotlinx-coroutines-test без Thread.sleep и реальных пауз.
 */
class RateLimiter(
    private val maxConcurrent: Int = 2,
    private val minIntervalMs: Long = 250L,
) {
    init {
        require(maxConcurrent >= 1) { "maxConcurrent must be >= 1, got $maxConcurrent" }
        require(minIntervalMs >= 0L) { "minIntervalMs must be >= 0, got $minIntervalMs" }
    }

    private class Gate(maxConcurrent: Int) {
        val slots = Semaphore(maxConcurrent)
        val starter = Mutex()
    }

    private val gates = ConcurrentHashMap<String, Gate>()

    private fun gateFor(domain: String): Gate = gates.getOrPut(domain) { Gate(maxConcurrent) }

    /** Сколько операций сейчас выполняется в домене (для тестов/диагностики). */
    fun activeCount(domain: String): Int =
        gates[domain]?.let { maxConcurrent - it.slots.availablePermits } ?: 0

    /**
     * Выполняет [block] под разрешением для [domain]:
     * слот семафора ограничивает параллелизм, стартовый mutex под [Mutex]
     * разносит начала запросов не ближе чем [minIntervalMs].
     */
    suspend fun <T> withPermit(domain: String, block: suspend () -> T): T {
        val gate = gateFor(domain)
        gate.slots.acquire()
        try {
            gate.starter.withLock { delay(minIntervalMs) }
            return block()
        } finally {
            gate.slots.release()
        }
    }
}
