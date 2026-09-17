package com.manhwaread.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorContrastTest {
    private val black = Color.Black.toArgb()
    private val white = Color.White.toArgb()

    @Test
    fun `luminance of black and white`() {
        assertEquals(0.0, relativeLuminance(black), 1e-6)
        assertEquals(1.0, relativeLuminance(white), 1e-6)
    }

    @Test
    fun `contrast ratio is symmetric and white-black is 21`() {
        assertEquals(21.0, contrastRatio(white, black), 1e-6)
        assertEquals(21.0, contrastRatio(black, white), 1e-6)
    }

    @Test
    fun `srgbToLinear boundaries and monotonicity`() {
        assertEquals(0.0, srgbToLinear(0), 1e-9)
        assertEquals(1.0, srgbToLinear(255), 1e-9)
        assertTrue(srgbToLinear(128) < srgbToLinear(129))
        assertTrue(srgbToLinear(128) > 0.2 && srgbToLinear(128) < 0.25)
    }

    @Test
    fun `dark text pairs meet WCAG AA`() {
        assertPairsMeet(
            CONTRAST_AA_TEXT,
            "onPrimary/primary" to pair(ManhwareadPalette.DarkOnPrimary, ManhwareadPalette.DarkPrimary),
            "onPrimaryContainer/primaryContainer" to
                pair(ManhwareadPalette.DarkOnPrimaryContainer, ManhwareadPalette.DarkPrimaryContainer),
            "onSecondary/secondary" to pair(ManhwareadPalette.DarkOnSecondary, ManhwareadPalette.DarkSecondary),
            "onSecondaryContainer/secondaryContainer" to
                pair(ManhwareadPalette.DarkOnSecondaryContainer, ManhwareadPalette.DarkSecondaryContainer),
            "onTertiary/tertiary" to pair(ManhwareadPalette.DarkOnTertiary, ManhwareadPalette.DarkTertiary),
            "onTertiaryContainer/tertiaryContainer" to
                pair(ManhwareadPalette.DarkOnTertiaryContainer, ManhwareadPalette.DarkTertiaryContainer),
            "onError/error" to pair(ManhwareadPalette.DarkOnError, ManhwareadPalette.DarkError),
            "onErrorContainer/errorContainer" to
                pair(ManhwareadPalette.DarkOnErrorContainer, ManhwareadPalette.DarkErrorContainer),
            "onBackground/background" to pair(ManhwareadPalette.DarkOnBackground, ManhwareadPalette.DarkBackground),
            "onSurface/surface" to pair(ManhwareadPalette.DarkOnSurface, ManhwareadPalette.DarkSurface),
            "onSurfaceVariant/surfaceVariant" to
                pair(ManhwareadPalette.DarkOnSurfaceVariant, ManhwareadPalette.DarkSurfaceVariant),
        )
    }

    @Test
    fun `light text pairs meet WCAG AA`() {
        assertPairsMeet(
            CONTRAST_AA_TEXT,
            "onPrimary/primary" to pair(ManhwareadPalette.LightOnPrimary, ManhwareadPalette.LightPrimary),
            "onPrimaryContainer/primaryContainer" to
                pair(ManhwareadPalette.LightOnPrimaryContainer, ManhwareadPalette.LightPrimaryContainer),
            "onSecondary/secondary" to pair(ManhwareadPalette.LightOnSecondary, ManhwareadPalette.LightSecondary),
            "onSecondaryContainer/secondaryContainer" to
                pair(ManhwareadPalette.LightOnSecondaryContainer, ManhwareadPalette.LightSecondaryContainer),
            "onTertiary/tertiary" to pair(ManhwareadPalette.LightOnTertiary, ManhwareadPalette.LightTertiary),
            "onTertiaryContainer/tertiaryContainer" to
                pair(ManhwareadPalette.LightOnTertiaryContainer, ManhwareadPalette.LightTertiaryContainer),
            "onError/error" to pair(ManhwareadPalette.LightOnError, ManhwareadPalette.LightError),
            "onErrorContainer/errorContainer" to
                pair(ManhwareadPalette.LightOnErrorContainer, ManhwareadPalette.LightErrorContainer),
            "onBackground/background" to
                pair(ManhwareadPalette.LightOnBackground, ManhwareadPalette.LightBackground),
            "onSurface/surface" to pair(ManhwareadPalette.LightOnSurface, ManhwareadPalette.LightSurface),
            "onSurfaceVariant/surfaceVariant" to
                pair(ManhwareadPalette.LightOnSurfaceVariant, ManhwareadPalette.LightSurfaceVariant),
        )
    }

    @Test
    fun `outline meets UI component threshold on surfaces`() {
        assertTrue(
            contrastRatio(ManhwareadPalette.DarkOutline.toArgb(), ManhwareadPalette.DarkSurface.toArgb()) >= CONTRAST_AA_UI,
            "dark outline on surface must be >= 3:1",
        )
        assertTrue(
            contrastRatio(ManhwareadPalette.LightOutline.toArgb(), ManhwareadPalette.LightSurface.toArgb()) >= CONTRAST_AA_UI,
            "light outline on surface must be >= 3:1",
        )
    }

    private fun pair(foreground: Color, background: Color): Pair<Int, Int> =
        foreground.toArgb() to background.toArgb()

    private fun assertPairsMeet(threshold: Double, vararg pairs: Pair<String, Pair<Int, Int>>) {
        for ((name, colors) in pairs) {
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue(ratio >= threshold, "$name contrast $ratio < $threshold")
        }
    }
}
