package com.manhwaread.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RateLimiterTest {
    @Test
    fun `at most maxConcurrent operations run per domain`() = runTest {
        val limiter = RateLimiter(maxConcurrent = 2, minIntervalMs = 0L)
        var active = 0
        var maxActive = 0
        coroutineScope {
            repeat(6) {
                launch {
                    limiter.withPermit("a") {
                        active++
                        if (active > maxActive) maxActive = active
                        delay(100)
                        active--
                    }
                }
            }
        }
        assertEquals(2, maxActive)
        assertEquals(0, active)
    }

    @Test
    fun `starts are spaced by at least minInterval`() = runTest {
        val limiter = RateLimiter(maxConcurrent = 5, minIntervalMs = 250L)
        val scheduler = testScheduler
        val starts = mutableListOf<Long>()
        coroutineScope {
            repeat(3) {
                launch {
                    limiter.withPermit("d") {
                        starts.add(scheduler.currentTime)
                        delay(10)
                    }
                }
            }
        }
        assertEquals(3, starts.size)
        assertTrue(starts[1] - starts[0] >= 250L, "gap=${starts[1] - starts[0]}")
        assertTrue(starts[2] - starts[1] >= 250L, "gap=${starts[2] - starts[1]}")
    }

    @Test
    fun `different domains do not block each other`() = runTest {
        val limiter = RateLimiter(maxConcurrent = 1, minIntervalMs = 250L)
        var bDone = false
        val jobA = launch { limiter.withPermit("A") { delay(5_000) } }
        launch { limiter.withPermit("B") { bDone = true } }
        advanceTimeBy(300)
        runCurrent()
        assertTrue(bDone, "domain B should not wait for domain A")
        assertTrue(jobA.isActive)
        jobA.cancel()
    }

    @Test
    fun `activeCount reflects in-flight operations`() = runTest {
        val limiter = RateLimiter(maxConcurrent = 2, minIntervalMs = 0L)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch {
            limiter.withPermit("x") {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        runCurrent()
        assertEquals(1, limiter.activeCount("x"))
        release.complete(Unit)
        job.join()
        assertEquals(0, limiter.activeCount("x"))
        assertEquals(0, limiter.activeCount("never-used"))
    }

    @Test
    fun `rejects invalid configuration`() {
        assertThrows<IllegalArgumentException> { RateLimiter(maxConcurrent = 0) }
        assertThrows<IllegalArgumentException> { RateLimiter(minIntervalMs = -1L) }
    }
}
