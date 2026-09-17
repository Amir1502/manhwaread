package com.manhwaread.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Точка входа приложения: инициализирует Hilt-граф. */
@HiltAndroidApp
class ManhwareadApp : Application()
