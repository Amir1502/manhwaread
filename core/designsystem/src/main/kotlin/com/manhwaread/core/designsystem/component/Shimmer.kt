package com.manhwaread.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private const val SHIMMER_DURATION_MS = 1200
private const val SHIMMER_TRANSLATION_RANGE = 1000f
private const val SHIMMER_BASE_ALPHA = 0.6f
private const val SHIMMER_HIGHLIGHT_ALPHA = 0.25f

/**
 * Модификатор мерцания (шиммер) для плейсхолдеров загрузки карточек и элементов списков.
 */
fun Modifier.shimmer(
    baseColor: Color? = null,
    highlightColor: Color? = null,
): Modifier = composed {
    val background = baseColor ?: MaterialTheme.colorScheme.surfaceVariant
    val highlight = highlightColor ?: MaterialTheme.colorScheme.surface

    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_TRANSLATION_RANGE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            background.copy(alpha = SHIMMER_BASE_ALPHA),
            highlight.copy(alpha = SHIMMER_HIGHLIGHT_ALPHA),
            background.copy(alpha = SHIMMER_BASE_ALPHA),
        ),
        start = Offset(translateAnimation - SHIMMER_TRANSLATION_RANGE / 2f, 0f),
        end = Offset(translateAnimation, translateAnimation / 2f),
    )

    background(brush)
}
