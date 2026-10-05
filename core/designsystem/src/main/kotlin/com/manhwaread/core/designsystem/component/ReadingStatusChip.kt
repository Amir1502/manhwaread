package com.manhwaread.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.manhwaread.core.model.ReadingStatus

private val ChipPaddingHorizontal = 10.dp
private val ChipPaddingVertical = 6.dp
private val StatusDotSize = 8.dp
private val DotSpacing = 6.dp
private val BorderWidth = 1.dp
private const val SELECTED_ALPHA = 0.2f

/**
 * Чип статуса чтения в библиотеке (Читаю, В планах, Прочитано, Брошено, Отложено).
 * Поддерживает как отображение, так и кликабельный режим для выбора статуса.
 */
@Composable
fun ReadingStatusChip(
    status: ReadingStatus,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val statusColor = statusColor(status)
    val label = stringResource(statusTextRes(status))

    val backgroundColor = if (isSelected) {
        statusColor.copy(alpha = SELECTED_ALPHA)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isSelected) {
        statusColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(ManhwareadShapes.Chip)
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.border(BorderWidth, statusColor, ManhwareadShapes.Chip)
                } else {
                    Modifier
                },
            )
            .then(clickableModifier)
            .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(StatusDotSize)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(modifier = Modifier.width(DotSpacing))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun statusColor(status: ReadingStatus): Color = when (status) {
    ReadingStatus.READING -> ManhwareadTheme.extraColors.statusReading
    ReadingStatus.PLANNED -> ManhwareadTheme.extraColors.statusPlanned
    ReadingStatus.COMPLETED -> ManhwareadTheme.extraColors.statusCompleted
    ReadingStatus.DROPPED -> ManhwareadTheme.extraColors.statusDropped
    ReadingStatus.ON_HOLD -> ManhwareadTheme.extraColors.statusOnHold
}

fun statusTextRes(status: ReadingStatus): Int = when (status) {
    ReadingStatus.READING -> R.string.ds_status_reading
    ReadingStatus.PLANNED -> R.string.ds_status_planned
    ReadingStatus.COMPLETED -> R.string.ds_status_completed
    ReadingStatus.DROPPED -> R.string.ds_status_dropped
    ReadingStatus.ON_HOLD -> R.string.ds_status_on_hold
}
