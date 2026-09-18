package com.manhwaread.app.di

import com.manhwaread.app.cloudflare.WebViewChallengeSolver
import com.manhwaread.core.network.CloudflareChallengeSolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudflareModule {
    // CloudflareInterceptor получает WebView-решатель вместо Noop (ФАЗА 13).
    @Binds
    @Singleton
    abstract fun bindCloudflareChallengeSolver(solver: WebViewChallengeSolver): CloudflareChallengeSolver
}
