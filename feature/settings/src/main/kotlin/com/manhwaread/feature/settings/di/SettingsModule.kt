package com.manhwaread.feature.settings.di

import com.manhwaread.core.network.HttpClientFactory
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
    // Единый OkHttpClient провайдеров перевода (интерсепторы фазы 3).
    @Provides
    @Singleton
    fun provideProviderHttpClient(): OkHttpClient = HttpClientFactory.create()

    @Provides
    @Singleton
    fun provideTranslationProviderFactory(httpClient: OkHttpClient): TranslationProviderFactory =
        TranslationProviderFactory(httpClient)
}
