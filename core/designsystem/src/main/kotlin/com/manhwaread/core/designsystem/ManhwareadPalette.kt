package com.manhwaread.core.designsystem

import androidx.compose.ui.graphics.Color

// Фирменная палитра Material3, dark-first: тёмная тема основная для читалки.
// Все текстовые пары (On*/соответствующий фон) выдерживают WCAG AA (4.5:1) —
// проверяется ColorContrastTest; обводка — порог UI 3:1.
object ManhwareadPalette {
    // Тёмная тема (основная тема приложения в стиле Mangalib).
    val DarkPrimary = Color(0xFFE55A36)
    val DarkOnPrimary = Color(0xFF121214)
    val DarkPrimaryContainer = Color(0xFF3D160A)
    val DarkOnPrimaryContainer = Color(0xFFFFDACF)
    val DarkSecondary = Color(0xFF8A8A93)
    val DarkOnSecondary = Color(0xFF121214)
    val DarkSecondaryContainer = Color(0xFF2C2C2E)
    val DarkOnSecondaryContainer = Color(0xFFE5E5EA)
    val DarkTertiary = Color(0xFFFF8A65)
    val DarkOnTertiary = Color(0xFF1A0800)
    val DarkTertiaryContainer = Color(0xFF501D11)
    val DarkOnTertiaryContainer = Color(0xFFFFDBD1)
    val DarkError = Color(0xFFFF453A)
    val DarkOnError = Color(0xFF1A0001)
    val DarkErrorContainer = Color(0xFF4D0E0B)
    val DarkOnErrorContainer = Color(0xFFFFD7D5)
    val DarkBackground = Color(0xFF121214)
    val DarkOnBackground = Color(0xFFF2F2F7)
    val DarkSurface = Color(0xFF1C1C1E)
    val DarkOnSurface = Color(0xFFF2F2F7)
    val DarkSurfaceVariant = Color(0xFF262629)
    val DarkOnSurfaceVariant = Color(0xFFC7C7CC)
    val DarkOutline = Color(0xFF8E9099)

    // Светлая тема.
    val LightPrimary = Color(0xFFC43C16)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFFFDACF)
    val LightOnPrimaryContainer = Color(0xFF380C03)
    val LightSecondary = Color(0xFF636366)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFE5E5EA)
    val LightOnSecondaryContainer = Color(0xFF1C1C1E)
    val LightTertiary = Color(0xFFA6381C)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFFFDBD1)
    val LightOnTertiaryContainer = Color(0xFF3B0900)
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)
    val LightBackground = Color(0xFFF9F9FB)
    val LightOnBackground = Color(0xFF121214)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF121214)
    val LightSurfaceVariant = Color(0xFFE5E5EA)
    val LightOnSurfaceVariant = Color(0xFF545458)
    val LightOutline = Color(0xFF74777F)

    // Дополнительные статусные и типовые токены Mangalib.
    val Success = Color(0xFF32D74B)
    val OnSuccess = Color(0xFF002106)
    val SuccessContainer = Color(0xFF0C3B14)
    val OnSuccessContainer = Color(0xFF97F5A5)

    val LightSuccess = Color(0xFF1B6B2F)
    val LightOnSuccess = Color(0xFFFFFFFF)
    val LightSuccessContainer = Color(0xFFD5F5DA)
    val LightOnSuccessContainer = Color(0xFF002106)

    val StarRating = Color(0xFFFFB800)
    val AgeRating18 = Color(0xFFFF453A)
    val AgeRating16 = Color(0xFFFF9F0A)

    val TypeManhwa = Color(0xFFE55A36)
    val TypeManga = Color(0xFF0A84FF)
    val TypeManhua = Color(0xFFBF5AF2)
    val TypeOther = Color(0xFF8A8A93)

    val StatusReading = Color(0xFF0A84FF)
    val StatusPlanned = Color(0xFFFF9F0A)
    val StatusCompleted = Color(0xFF32D74B)
    val StatusDropped = Color(0xFFFF453A)
    val StatusOnHold = Color(0xFF8A8A93)
}
