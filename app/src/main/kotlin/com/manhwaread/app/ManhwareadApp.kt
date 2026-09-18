package com.manhwaread.app

import android.app.Application
import com.manhwaread.feature.downloads.queue.QueueProcessor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Точка входа приложения: инициализирует Hilt-граф и запускает фоновые
 * воркеры очереди скачивания/перевода (ФАЗА 15).
 */
@HiltAndroidApp
class ManhwareadApp : Application() {
    @Inject
    lateinit var queueProcessor: QueueProcessor

    override fun onCreate() {
        super.onCreate()
        queueProcessor.start()
    }
}
