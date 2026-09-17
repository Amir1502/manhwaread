package com.manhwaread.core.common

import app.cash.turbine.test
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class CircuitBreakerTest {
    private var nowMs = 0L
    private val clock: () -> Long = { nowMs }

    private fun breaker(threshold: Int = 10, coolDownMs: Long = 1_000L): CircuitBreaker =
        CircuitBreaker(failureThreshold = threshold, coolDownMs = coolDownMs, timeMs = clock)

    private fun failure(): DomainResult<Int> = DomainResult.Failure(AppError.Network(IOException("x")))

    private fun success(value: Int = 1): DomainResult<Int> = DomainResult.Success(value)

    private fun rejected(): DomainResult<Int> = DomainResult.Failure(AppError.SourceUnavailable)

    @Test
    fun `opens after threshold consecutive failures and rejects without calling block`() = runTest {
        val cb = breaker(threshold = 3)
        var blockCalls = 0
        repeat(3) {
            cb.execute {
                blockCalls++
                failure()
            }
        }
        assertEquals(CircuitState.OPEN, cb.state.value)

        val result = cb.execute {
            blockCalls++
            success()
        }
        assertEquals(3, blockCalls)
        assertEquals(rejected(), result)
    }

    @Test
    fun `success resets consecutive failure counter`() = runTest {
        val cb = breaker(threshold = 3)
        cb.execute { failure() }
        cb.execute { failure() }
        cb.execute { success() }
        cb.execute { failure() }
        cb.execute { failure() }
        assertEquals(CircuitState.CLOSED, cb.state.value)
    }

    @Test
    fun `stays open before cooldown and closes on successful probe after it`() = runTest {
        val cb = breaker(threshold = 1, coolDownMs = 1_000L)
        cb.execute { failure() }
        assertEquals(CircuitState.OPEN, cb.state.value)

        nowMs += 999
        assertEquals(rejected(), cb.execute { success() })
        assertEquals(CircuitState.OPEN, cb.state.value)

        nowMs += 1
        assertEquals(success(7), cb.execute { success(7) })
        assertEquals(CircuitState.CLOSED, cb.state.value)
    }

    @Test
    fun `failed probe reopens breaker`() = runTest {
        val cb = breaker(threshold = 1, coolDownMs = 1_000L)
        cb.execute { failure() }
        nowMs += 1_000
        cb.execute { failure() }
        assertEquals(CircuitState.OPEN, cb.state.value)
        assertEquals(rejected(), cb.execute { success() })
    }

    @Test
    fun `half-open admits exactly one concurrent probe`() = runTest {
        val cb = breaker(threshold = 1, coolDownMs = 1_000L)
        cb.execute { failure() }
        nowMs += 1_000

        var started = 0
        val probe = async {
            cb.execute {
                started++
                delay(500)
                success()
            }
        }
        runCurrent()
        assertEquals(1, started)

        val second = cb.execute {
            started++
            success()
        }
        assertEquals(1, started)
        assertTrue(second is DomainResult.Failure)

        probe.await()
        assertEquals(CircuitState.CLOSED, cb.state.value)
    }

    @Test
    fun `exception from block counted as failure`() = runTest {
        val cb = breaker(threshold = 2)
        val io = cb.execute<Int> { throw IOException("io") }
        val ioError = (io as DomainResult.Failure).error
        assertTrue(ioError is AppError.Network)

        val illegal = cb.execute<Int> { throw IllegalStateException("bad") }
        val illegalError = (illegal as DomainResult.Failure).error
        assertTrue(illegalError is AppError.Unknown)

        assertEquals(CircuitState.OPEN, cb.state.value)
    }

    @Test
    fun `state flow emits transitions`() = runTest {
        val cb = breaker(threshold = 1, coolDownMs = 1_000L)
        cb.state.test {
            assertEquals(CircuitState.CLOSED, awaitItem())
            cb.execute { failure() }
            assertEquals(CircuitState.OPEN, awaitItem())
            nowMs += 1_000
            cb.execute {
                delay(10)
                success()
            }
            assertEquals(CircuitState.HALF_OPEN, awaitItem())
            assertEquals(CircuitState.CLOSED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `concurrent failures under load are counted consistently`() = runTest {
        val cb = breaker(threshold = 50)
        coroutineScope {
            repeat(100) {
                launch { cb.execute { failure() } }
            }
        }
        assertEquals(CircuitState.OPEN, cb.state.value)
    }

    @Test
    fun `registry gives independent breakers per key`() = runTest {
        val registry = CircuitBreakerRegistry(failureThreshold = 1, coolDownMs = 1_000L, timeMs = clock)
        val first = registry.forSource("source-1")
        assertSame(first, registry.forSource("source-1"))

        first.execute { failure() }
        assertEquals(CircuitState.OPEN, first.state.value)
        assertEquals(CircuitState.CLOSED, registry.forSource("source-2").state.value)
    }
}
