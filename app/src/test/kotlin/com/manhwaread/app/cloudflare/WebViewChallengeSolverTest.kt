package com.manhwaread.app.cloudflare

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// HiltTestApplication вместо manifest-класса: ФАЗА 15 стартует очередь в
// ManhwareadApp.onCreate, что eagerly строит DI-граф (ML Kit вне Robolectric
// не инициализирован); тест-приложение граф не трогает.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class WebViewChallengeSolverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `parseCfClearance extracts value from cookie header`() {
        assertEquals("abc123", parseCfClearance("cf_clearance=abc123"))
        assertEquals("abc123", parseCfClearance("other=1; cf_clearance=abc123; tail=2"))
    }

    @Test
    fun `parseCfClearance rejects missing and blank values`() {
        assertNull(parseCfClearance(null))
        assertNull(parseCfClearance(""))
        assertNull(parseCfClearance("session=xyz"))
        assertNull(parseCfClearance("cf_clearance="))
    }

    @Test
    fun `solve returns null on timeout when no cookie appears`() {
        val solver = WebViewChallengeSolver(context)
        // Unconfined вместо Dispatchers.Main: looper Robolectric приостановлен,
        // реальный Main заблокировал бы тест навсегда. Таймаут срабатывает
        // на event loop runBlocking, WebView уничтожается по отмене.
        val result = runBlocking {
            solver.solveWithTimeout(
                url = "https://challenge.invalid.example/cdn-cgi/challenge",
                timeoutMs = 200L,
                solverDispatcher = Dispatchers.Unconfined,
            )
        }
        assertNull(result)
    }
}
