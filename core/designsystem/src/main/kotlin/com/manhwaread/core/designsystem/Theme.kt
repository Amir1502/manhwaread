package com.manhwaread.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Схемы собраны из палитры; private object уходит от правила детекта
// TopLevelPropertyNaming (private val в UpperCamelCase) и группирует их.
private object ColorSchemes {
    val Dark = darkColorScheme(
        primary = ManhwareadPalette.DarkPrimary,
        onPrimary = ManhwareadPalette.DarkOnPrimary,
        primaryContainer = ManhwareadPalette.DarkPrimaryContainer,
        onPrimaryContainer = ManhwareadPalette.DarkOnPrimaryContainer,
        secondary = ManhwareadPalette.DarkSecondary,
        onSecondary = ManhwareadPalette.DarkOnSecondary,
        secondaryContainer = ManhwareadPalette.DarkSecondaryContainer,
        onSecondaryContainer = ManhwareadPalette.DarkOnSecondaryContainer,
        tertiary = ManhwareadPalette.DarkTertiary,
        onTertiary = ManhwareadPalette.DarkOnTertiary,
        tertiaryContainer = ManhwareadPalette.DarkTertiaryContainer,
        onTertiaryContainer = ManhwareadPalette.DarkOnTertiaryContainer,
        error = ManhwareadPalette.DarkError,
        onError = ManhwareadPalette.DarkOnError,
        errorContainer = ManhwareadPalette.DarkErrorContainer,
        onErrorContainer = ManhwareadPalette.DarkOnErrorContainer,
        background = ManhwareadPalette.DarkBackground,
        onBackground = ManhwareadPalette.DarkOnBackground,
        surface = ManhwareadPalette.DarkSurface,
        onSurface = ManhwareadPalette.DarkOnSurface,
        surfaceVariant = ManhwareadPalette.DarkSurfaceVariant,
        onSurfaceVariant = ManhwareadPalette.DarkOnSurfaceVariant,
        outline = ManhwareadPalette.DarkOutline,
    )

    val Light = lightColorScheme(
        primary = ManhwareadPalette.LightPrimary,
        onPrimary = ManhwareadPalette.LightOnPrimary,
        primaryContainer = ManhwareadPalette.LightPrimaryContainer,
        onPrimaryContainer = ManhwareadPalette.LightOnPrimaryContainer,
        secondary = ManhwareadPalette.LightSecondary,
        onSecondary = ManhwareadPalette.LightOnSecondary,
        secondaryContainer = ManhwareadPalette.LightSecondaryContainer,
        onSecondaryContainer = ManhwareadPalette.LightOnSecondaryContainer,
        tertiary = ManhwareadPalette.LightTertiary,
        onTertiary = ManhwareadPalette.LightOnTertiary,
        tertiaryContainer = ManhwareadPalette.LightTertiaryContainer,
        onTertiaryContainer = ManhwareadPalette.LightOnTertiaryContainer,
        error = ManhwareadPalette.LightError,
        onError = ManhwareadPalette.LightOnError,
        errorContainer = ManhwareadPalette.LightErrorContainer,
        onErrorContainer = ManhwareadPalette.LightOnErrorContainer,
        background = ManhwareadPalette.LightBackground,
        onBackground = ManhwareadPalette.LightOnBackground,
        surface = ManhwareadPalette.LightSurface,
        onSurface = ManhwareadPalette.LightOnSurface,
        surfaceVariant = ManhwareadPalette.LightSurfaceVariant,
        onSurfaceVariant = ManhwareadPalette.LightOnSurfaceVariant,
        outline = ManhwareadPalette.LightOutline,
    )
}

// Фирменная тема: тёмная по системному setting; dynamicColor намеренно не
// используется — палитра каталога/читалки должна быть предсказуемой и не
// зависеть от цвета обоев пользователя.
@Composable
fun ManhwareadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ColorSchemes.Dark else ColorSchemes.Light,
        typography = ManhwareadTypography,
        content = content,
    )
}
