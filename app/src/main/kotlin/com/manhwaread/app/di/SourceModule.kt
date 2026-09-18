package com.manhwaread.app.di

import android.content.Context
import com.manhwaread.core.network.CloudflareChallengeSolver
import com.manhwaread.core.network.HttpClientConfig
import com.manhwaread.core.network.HttpClientFactory
import com.manhwaread.source.api.InMemorySourceRegistry
import com.manhwaread.source.api.SourceRegistry
import com.manhwaread.source.asura.AsuraSource
import com.manhwaread.source.madara.MadaraSource
import com.manhwaread.source.madara.manga18fxConfig
import com.manhwaread.source.mangadex.MangaDexSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {
    // Единый HTTP-клиент приложения: дисковый кэш и WebView-решатель
    // Cloudflare (один экземпляр на источники и провайдеров перевода).
    @Provides
    @Singleton
    fun provideSourceHttpClient(
        @ApplicationContext context: Context,
        cloudflareSolver: CloudflareChallengeSolver,
    ): OkHttpClient = HttpClientFactory.create(
        HttpClientConfig(
            cacheDirectory = File(context.cacheDir, HTTP_CACHE_DIR),
            cloudflareSolver = cloudflareSolver,
        ),
    )

    // Подключённые источники (ФАЗА 13): MangaDex, manga18fx (Madara), Asura.
    @Provides
    @Singleton
    fun provideSourceRegistry(client: OkHttpClient): SourceRegistry {
        val registry = InMemorySourceRegistry()
        registry.register(MangaDexSource(client))
        registry.register(MadaraSource(manga18fxConfig(), client))
        registry.register(AsuraSource(client))
        return registry
    }

    private const val HTTP_CACHE_DIR = "http_cache"
}
