package com.manhwaread.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.ManhwareadTheme
import com.manhwaread.core.designsystem.R
import com.manhwaread.source.api.MangaType

private val BadgePaddingHorizontal = 6.dp
private val BadgePaddingVertical = 2.dp

/**
 * Базовый компактный бейдж (радиус скругления 6.dp) для метаданных тайтла.
 */
@Composable
fun MangaBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(ManhwareadShapes.Badge)
            .background(containerColor)
            .padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * Бейдж типа манги (Манхва, Манга, Маньхуа, Другое) с брендовыми цветами Mangalib.
 */
@Composable
fun MangaTypeBadge(
    type: MangaType,
    modifier: Modifier = Modifier,
) {
    val (labelRes, containerColor) = when (type) {
        MangaType.MANHWA -> R.string.ds_type_manhwa to ManhwareadTheme.extraColors.typeManhwa
        MangaType.MANGA -> R.string.ds_type_manga to ManhwareadTheme.extraColors.typeManga
        MangaType.MANHUA -> R.string.ds_type_manhua to ManhwareadTheme.extraColors.typeManhua
        MangaType.OTHER -> R.string.ds_type_other to ManhwareadTheme.extraColors.typeOther
    }

    MangaBadge(
        text = stringResource(labelRes).uppercase(),
        containerColor = containerColor,
        contentColor = Color.White,
        modifier = modifier,
    )
}

/**
 * Бейдж возрастного рейтинга (18+, 16+).
 */
@Composable
fun AgeRatingBadge(
    ageRating: String,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (ageRating.contains("18")) {
        ManhwareadTheme.extraColors.ageRating18
    } else {
        ManhwareadTheme.extraColors.ageRating16
    }

    MangaBadge(
        text = ageRating,
        containerColor = containerColor,
        contentColor = Color.White,
        modifier = modifier,
    )
}
