package com.manhwaread.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.manhwaread.feature.downloads.queue.QueueProcessor
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Точка входа приложения: инициализирует Hilt-граф, конфигурирует Coil
 * с общим HTTP-клиентом и запускает фоновые воркеры очереди скачивания/перевода.
 */
@HiltAndroidApp
class ManhwareadApp : Application(), ImageLoaderFactory {
    @Inject
    lateinit var queueProcessor: QueueProcessor

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        queueProcessor.start()
    }
}
