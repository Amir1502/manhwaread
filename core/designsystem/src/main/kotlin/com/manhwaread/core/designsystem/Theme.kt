package com.manhwaread.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Дополнительные семантические цвета, расширяющие MaterialTheme (успех, рейтинг, типы тайтлов).
@Immutable
data class ManhwareadExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val starRating: Color,
    val ageRating18: Color,
    val ageRating16: Color,
    val typeManhwa: Color,
    val typeManga: Color,
    val typeManhua: Color,
    val typeOther: Color,
    val statusReading: Color,
    val statusPlanned: Color,
    val statusCompleted: Color,
    val statusDropped: Color,
    val statusOnHold: Color,
)

val LocalManhwareadExtraColors = staticCompositionLocalOf {
    ManhwareadExtraColors(
        success = ManhwareadPalette.Success,
        onSuccess = ManhwareadPalette.OnSuccess,
        successContainer = ManhwareadPalette.SuccessContainer,
        onSuccessContainer = ManhwareadPalette.OnSuccessContainer,
        starRating = ManhwareadPalette.StarRating,
        ageRating18 = ManhwareadPalette.AgeRating18,
        ageRating16 = ManhwareadPalette.AgeRating16,
        typeManhwa = ManhwareadPalette.TypeManhwa,
        typeManga = ManhwareadPalette.TypeManga,
        typeManhua = ManhwareadPalette.TypeManhua,
        typeOther = ManhwareadPalette.TypeOther,
        statusReading = ManhwareadPalette.StatusReading,
        statusPlanned = ManhwareadPalette.StatusPlanned,
        statusCompleted = ManhwareadPalette.StatusCompleted,
        statusDropped = ManhwareadPalette.StatusDropped,
        statusOnHold = ManhwareadPalette.StatusOnHold,
    )
}

object ManhwareadTheme {
    val colorScheme: ColorScheme
        @Composable
        get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        get() = MaterialTheme.shapes

    val extraColors: ManhwareadExtraColors
        @Composable
        get() = LocalManhwareadExtraColors.current
}

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

// Фирменная тема: тёмная по умолчанию (dark-first для читалки).
// dynamicColor намеренно не используется — палитра каталога/читалки
// должна быть строгой и соответствовать брендбуку Mangalib.
@Composable
fun ManhwareadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extraColors = remember(darkTheme) {
        ManhwareadExtraColors(
            success = if (darkTheme) ManhwareadPalette.Success else ManhwareadPalette.LightSuccess,
            onSuccess = if (darkTheme) ManhwareadPalette.OnSuccess else ManhwareadPalette.LightOnSuccess,
            successContainer = if (darkTheme) {
                ManhwareadPalette.SuccessContainer
            } else {
                ManhwareadPalette.LightSuccessContainer
            },
            onSuccessContainer = if (darkTheme) {
                ManhwareadPalette.OnSuccessContainer
            } else {
                ManhwareadPalette.LightOnSuccessContainer
            },
            starRating = ManhwareadPalette.StarRating,
            ageRating18 = ManhwareadPalette.AgeRating18,
            ageRating16 = ManhwareadPalette.AgeRating16,
            typeManhwa = ManhwareadPalette.TypeManhwa,
            typeManga = ManhwareadPalette.TypeManga,
            typeManhua = ManhwareadPalette.TypeManhua,
            typeOther = ManhwareadPalette.TypeOther,
            statusReading = ManhwareadPalette.StatusReading,
            statusPlanned = ManhwareadPalette.StatusPlanned,
            statusCompleted = ManhwareadPalette.StatusCompleted,
            statusDropped = ManhwareadPalette.StatusDropped,
            statusOnHold = ManhwareadPalette.StatusOnHold,
        )
    }

    CompositionLocalProvider(
        LocalManhwareadExtraColors provides extraColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) ColorSchemes.Dark else ColorSchemes.Light,
            typography = ManhwareadTypography,
            shapes = ManhwareadShapes.MaterialShapes,
            content = content,
        )
    }
}
