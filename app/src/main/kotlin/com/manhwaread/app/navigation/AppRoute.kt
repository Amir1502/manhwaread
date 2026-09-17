package com.manhwaread.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.manhwaread.app.R

// Разделы приложения: единый источник правды для NavHost и нижней навигации.
// Полноценные экраны подключаются из :feature:*-модулей в ФАЗЕ 14.
enum class AppRoute(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LIBRARY("library", R.string.nav_library, Icons.Filled.CollectionsBookmark),
    BROWSE("browse", R.string.nav_browse, Icons.Filled.Explore),
    HISTORY("history", R.string.nav_history, Icons.Filled.History),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Filled.Download),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

// Стартовый раздел после запуска приложения.
val startRoute: AppRoute = AppRoute.LIBRARY
