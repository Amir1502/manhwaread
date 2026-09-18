package com.manhwaread.feature.settings.di

import com.manhwaread.core.translation.TranslationProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    // OkHttpClient приходит из DI приложения (:app SourceModule, ФАЗА 13) —
    // единый клиент с Cloudflare-решателем и дисковым кэшем.
    @Provides
    @Singleton
    fun provideTranslationProviderFactory(httpClient: OkHttpClient): TranslationProviderFactory =
        TranslationProviderFactory(httpClient)
}
