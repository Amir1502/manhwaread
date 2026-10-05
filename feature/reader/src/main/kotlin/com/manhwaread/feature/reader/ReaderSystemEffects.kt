package com.manhwaread.feature.reader

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.util.Date

// Системные эффекты читалки: immersive-режим, синхронный с панелями,
// заряд батареи и часы для инфо-полосы вебтуна.

/**
 * Системные бары показываются вместе с панелями читалки и скрываются вместе
 * с ними (immersive; свайп от края показывает их временно). Иконки баров —
 * светлые (фон читалки тёмный); при уходе с экрана прежнее состояние окна
 * восстанавливается.
 */
@Composable
fun ImmersiveSystemBarsEffect(barsVisible: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(window, view) {
        val controller = window?.let { activityWindow -> WindowCompat.getInsetsController(activityWindow, view) }
        val previousBehavior = controller?.systemBarsBehavior
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose {
            controller?.apply {
                show(WindowInsetsCompat.Type.systemBars())
                previousBehavior?.let { behavior -> systemBarsBehavior = behavior }
                previousLightStatusBars?.let { light -> isAppearanceLightStatusBars = light }
                previousLightNavigationBars?.let { light -> isAppearanceLightNavigationBars = light }
            }
        }
    }
    LaunchedEffect(window, view, barsVisible) {
        val controller = window?.let { activityWindow -> WindowCompat.getInsetsController(activityWindow, view) }
            ?: return@LaunchedEffect
        if (barsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Заряд батареи в процентах: начальное значение — из «липкого»
 * ACTION_BATTERY_CHANGED, дальше — обновления ресивера, живущего, пока
 * composable в композиции (инфо-полоса видима). null — заряд неизвестен.
 */
@Composable
fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    var percent by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                percent = intent.batteryPercentOrNull()
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        percent = sticky?.batteryPercentOrNull()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

/**
 * Текущее время в системном формате (12/24 ч), обновляется ровно на границе
 * минуты; таймер работает, только пока composable в композиции.
 */
@Composable
fun rememberClockText(): String {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    var text by remember(formatter) { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(formatter) {
        while (true) {
            val now = System.currentTimeMillis()
            text = formatter.format(Date(now))
            delay(millisUntilNextMinute(now))
        }
    }
    return text
}

private fun Intent.batteryPercentOrNull(): Int? =
    batteryPercent(
        level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
    )

// Compose-контекст может быть обёрткой (тема, локаль): поднимаемся до Activity.
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
