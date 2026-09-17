package com.manhwaread.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.manhwaread.core.source.Source
import com.manhwaread.sources.RateLimitedJsonTransport
import com.manhwaread.sources.mangadex.MangaDexSource
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class ManhwareadApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.httpClient)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder().directory(File(cacheDir, "covers"))
                .maxSizeBytes(128L * 1024 * 1024).build()
        }
        .crossfade(true)
        .build()
}

// Область жизни зависимостей совпадает с процессом; ViewModel получает только контракт источника.
class AppContainer(application: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .cache(Cache(File(application.cacheDir, "http"), 16L * 1024 * 1024))
        .build()

    val source: Source = MangaDexSource(
        RateLimitedJsonTransport(httpClient, "api.mangadex.org"),
    )
}
