package com.manhwaread.core.common

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.random.Random

class RetryTest {
    private fun networkError() = AppError.Network(IOException("boom"))

    @Test
    fun `returns immediately on first success without delay`() = runTest {
        var calls = 0
        val result = withRetry {
            calls++
            DomainResult.Success("ok")
        }
        assertEquals(DomainResult.Success("ok"), result)
        assertEquals(1, calls)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `retries network error with exponential backoff`() = runTest {
        val config = RetryConfig(maxAttempts = 4, initialDelayMs = 500L, multiplier = 2.0, jitterFraction = 0.0)
        var calls = 0
        val result = withRetry(config) {
            calls++
            if (calls < 4) DomainResult.Failure(networkError()) else DomainResult.Success(calls)
        }
        assertEquals(DomainResult.Success(4), result)
        assertEquals(4, calls)
        // Паузы без джиттера: 500 + 1000 + 2000.
        assertEquals(3500L, currentTime)
    }

    @Test
    fun `gives up after maxAttempts returning last failure`() = runTest {
        val config = RetryConfig(maxAttempts = 3, jitterFraction = 0.0)
        var calls = 0
        val result = withRetry(config) {
            calls++
            DomainResult.Failure(networkError())
        }
        assertEquals(3, calls)
        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is AppError.Network)
    }

    @Test
    fun `does not retry non-retryable errors`() = runTest {
        var calls = 0
        val result = withRetry(RetryConfig(maxAttempts = 5)) {
            calls++
            DomainResult.Failure(AppError.ProviderAuth)
        }
        assertEquals(1, calls)
        assertEquals(DomainResult.Failure(AppError.ProviderAuth), result)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `respects retryAfterMs of RateLimited`() = runTest {
        val config = RetryConfig(maxAttempts = 2, initialDelayMs = 500L, jitterFraction = 0.0)
        var calls = 0
        val result = withRetry(config) {
            calls++
            if (calls == 1) DomainResult.Failure(AppError.RateLimited(5000L)) else DomainResult.Success("ok")
        }
        assertEquals(2, calls)
        assertTrue(result.isSuccess)
        // max(backoff=500, retryAfter=5000).
        assertEquals(5000L, currentTime)
    }

    @Test
    fun `custom retryOn predicate controls retries`() = runTest {
        val config = RetryConfig(
            maxAttempts = 3,
            jitterFraction = 0.0,
            retryOn = { it is AppError.CloudflareBlocked },
        )
        var calls = 0
        withRetry(config) {
            calls++
            DomainResult.Failure(networkError())
        }
        assertEquals(1, calls)
    }

    @Test
    fun `jitter stays within fraction bounds`() {
        val config = RetryConfig(
            initialDelayMs = 500L,
            maxDelayMs = 8_000L,
            multiplier = 2.0,
            jitterFraction = 0.2,
            random = Random(42),
        )
        repeat(100) {
            val delayMs = computeDelayMs(networkError(), attempt = 0, config = config)
            assertTrue(delayMs in 400L..600L, "delay=$delayMs out of [400, 600]")
        }
    }

    @Test
    fun `backoff capped by maxDelayMs`() {
        val config = RetryConfig(
            initialDelayMs = 500L,
            maxDelayMs = 8_000L,
            multiplier = 2.0,
            jitterFraction = 0.0,
            random = Random(1),
        )
        val delayMs = computeDelayMs(networkError(), attempt = 10, config = config)
        assertEquals(8_000L, delayMs)
    }

    @Test
    fun `rejects invalid configuration`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { RetryConfig(maxAttempts = 0) }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> { RetryConfig(jitterFraction = 1.5) }
    }
}
