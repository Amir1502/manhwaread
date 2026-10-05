package com.manhwaread.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.R

private const val COVER_ASPECT_RATIO = 3f / 4f
private val FallbackIconSize = 36.dp
private const val ICON_ALPHA = 0.5f

/**
 * Обложка тайтла в пропорции 3:4 со скруглением 12.dp, фолбэком и поддержкой наложения бейджей.
 */
@Composable
fun MangaCover(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = ManhwareadShapes.Card,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .then(clickableModifier),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (model != null && !isError) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription ?: stringResource(R.string.ds_manga_cover),
                    contentScale = contentScale,
                    onLoading = {
                        isLoading = true
                        isError = false
                    },
                    onSuccess = {
                        isLoading = false
                        isError = false
                    },
                    onError = {
                        isLoading = false
                        isError = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                )
            }

            if (isLoading && model != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmer(),
                )
            }

            if (isError || model == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ICON_ALPHA),
                        modifier = Modifier.size(FallbackIconSize),
                    )
                }
            }

            overlay?.invoke(this)
        }
    }
}
