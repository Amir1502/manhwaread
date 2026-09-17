package com.manhwaread.core.designsystem

import androidx.compose.ui.graphics.Color

// Фирменная палитра Material3, dark-first: тёмная тема основная для читалки.
// Все текстовые пары (On*/соответствующий фон) выдерживают WCAG AA (4.5:1) —
// проверяется ColorContrastTest; обводка — порог UI 3:1.
object ManhwareadPalette {
    // Тёмная тема.
    val DarkPrimary = Color(0xFFAAC7FF)
    val DarkOnPrimary = Color(0xFF0A2F5C)
    val DarkPrimaryContainer = Color(0xFF264573)
    val DarkOnPrimaryContainer = Color(0xFFD6E3FF)
    val DarkSecondary = Color(0xFFC3C6DD)
    val DarkOnSecondary = Color(0xFF2C3042)
    val DarkSecondaryContainer = Color(0xFF434759)
    val DarkOnSecondaryContainer = Color(0xFFDFE2F9)
    val DarkTertiary = Color(0xFFE2BAD7)
    val DarkOnTertiary = Color(0xFF42263E)
    val DarkTertiaryContainer = Color(0xFF5B3C55)
    val DarkOnTertiaryContainer = Color(0xFFFFD7F3)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)
    val DarkBackground = Color(0xFF111318)
    val DarkOnBackground = Color(0xFFE2E2E9)
    val DarkSurface = Color(0xFF111318)
    val DarkOnSurface = Color(0xFFE2E2E9)
    val DarkSurfaceVariant = Color(0xFF44474E)
    val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
    val DarkOutline = Color(0xFF8E9099)

    // Светлая тема.
    val LightPrimary = Color(0xFF2F5DA8)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFD6E3FF)
    val LightOnPrimaryContainer = Color(0xFF001B3D)
    val LightSecondary = Color(0xFF5B5E71)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFE0E2F9)
    val LightOnSecondaryContainer = Color(0xFF181B2C)
    val LightTertiary = Color(0xFF74546D)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFFFD7F3)
    val LightOnTertiaryContainer = Color(0xFF2B1228)
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)
    val LightBackground = Color(0xFFFDFBFF)
    val LightOnBackground = Color(0xFF1A1B20)
    val LightSurface = Color(0xFFFDFBFF)
    val LightOnSurface = Color(0xFF1A1B20)
    val LightSurfaceVariant = Color(0xFFE1E2EC)
    val LightOnSurfaceVariant = Color(0xFF44474E)
    val LightOutline = Color(0xFF74777F)
}
