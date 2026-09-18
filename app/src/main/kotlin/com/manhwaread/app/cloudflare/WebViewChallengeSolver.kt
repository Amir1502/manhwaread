package com.manhwaread.app.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import com.manhwaread.core.network.CfClearance
import com.manhwaread.core.network.CloudflareChallengeSolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// Решатель challenge-страниц Cloudflare на WebView (ФАЗА 13): загружает
// страницу, ждёт появления cookie cf_clearance и возвращает его вместе
// с User-Agent WebView — Cloudflare привязывает clearance к UA.
// Таймаут → null → CloudflareInterceptor бросает CloudflareBlockedException.
@Singleton
class WebViewChallengeSolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : CloudflareChallengeSolver {
    override suspend fun solve(url: String): CfClearance? =
        solveWithTimeout(url, SOLVE_TIMEOUT_MS, Dispatchers.Main)

    // Таймаут и диспетчер параметризованы для тестов: в проде WebView живёт
    // на главном потоке (Dispatchers.Main), в Robolectric главный looper
    // приостановлен, поэтому тесты передают другой диспетчер.
    // Публичный (не internal): K2-lint анализирует unit-test source set без
    // friend-модуля и падает при разрешении internal-ссылок из тестов.
    @VisibleForTesting
    suspend fun solveWithTimeout(
        url: String,
        timeoutMs: Long,
        solverDispatcher: CoroutineDispatcher,
    ): CfClearance? = withTimeoutOrNull(timeoutMs) {
        withContext(solverDispatcher) { awaitClearance(url) }
    }

    @SuppressLint("SetJavaScriptEnabled") // Challenge Cloudflare требует исполнения JS на странице.
    private suspend fun awaitClearance(url: String): CfClearance? = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        val userAgent = webView.settings.userAgentString
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                val clearance = parseCfClearance(CookieManager.getInstance().getCookie(url))
                if (clearance != null && continuation.isActive) {
                    view.destroy()
                    continuation.resume(CfClearance(clearance, userAgent))
                }
            }
        }
        // Отмена (в т.ч. по таймауту) уничтожает WebView — утечек окна нет.
        continuation.invokeOnCancellation { webView.destroy() }
        webView.loadUrl(url)
    }

    companion object {
        const val SOLVE_TIMEOUT_MS = 20_000L
    }
}

// Чистая функция: извлечение значения cf_clearance из строки Cookie-заголовка.
fun parseCfClearance(cookieHeader: String?): String? =
    cookieHeader?.split(';')
        ?.map { pair -> pair.trim() }
        ?.firstOrNull { pair -> pair.startsWith(CF_CLEARANCE_PREFIX) }
        ?.substringAfter('=')
        ?.takeIf { value -> value.isNotBlank() }

private const val CF_CLEARANCE_PREFIX = "cf_clearance="
