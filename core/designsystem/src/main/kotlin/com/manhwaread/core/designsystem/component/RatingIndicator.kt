package com.manhwaread.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manhwaread.core.designsystem.ManhwareadTheme
import com.manhwaread.core.designsystem.R
import java.util.Locale

private val StarIconSize = 14.dp
private val StarSpacing = 4.dp

/**
 * Индикатор рейтинга тайтла (звезда + числовая оценка).
 */
@Composable
fun RatingIndicator(
    rating: Float,
    modifier: Modifier = Modifier,
    iconSize: Dp = StarIconSize,
) {
    val formattedRating = String.format(Locale.US, "%.1f", rating)
    val description = stringResource(R.string.ds_rating_description, formattedRating)

    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = ManhwareadTheme.extraColors.starRating,
            modifier = Modifier.size(iconSize),
        )
        Spacer(modifier = Modifier.width(StarSpacing))
        Text(
            text = formattedRating,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
