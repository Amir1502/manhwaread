package com.manhwaread.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manhwaread.core.designsystem.ManhwareadPalette
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.ManhwareadTypography

private val TocHorizontalPadding = 16.dp
private val TocHeaderBottomPadding = 8.dp
private val TocBottomPadding = 24.dp
private val TocItemMinHeight = 48.dp
private val TocAccentWidth = 3.dp
private val TocAccentHeight = 24.dp
private val TocAccentSpacing = 12.dp
private val TocCheckSize = 18.dp
private const val READ_CHAPTER_ALPHA = 0.55f

/**
 * Оглавление в нижней шторке: главы от новых к старым, текущая подсвечена
 * акцентной полосой и цветом, прочитанные приглушены и отмечены галочкой.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocSheet(
    chapters: List<ReaderTocItem>,
    currentChapterId: Long?,
    onSelectChapter: (chapterId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ManhwareadPalette.DarkSurface,
        contentColor = ManhwareadPalette.DarkOnSurface,
        shape = ManhwareadShapes.BottomSheet,
    ) {
        Column(modifier = Modifier.padding(bottom = TocBottomPadding)) {
            Text(
                text = stringResource(R.string.reader_toc),
                style = ManhwareadTypography.titleMedium,
                color = ManhwareadPalette.DarkOnSurface,
                modifier = Modifier
                    .padding(horizontal = TocHorizontalPadding)
                    .padding(bottom = TocHeaderBottomPadding),
            )
            if (chapters.isEmpty()) {
                Text(
                    text = stringResource(R.string.reader_toc_empty),
                    style = ManhwareadTypography.bodyMedium,
                    color = ManhwareadPalette.DarkOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TocHorizontalPadding),
                )
            } else {
                TocList(chapters = chapters, currentChapterId = currentChapterId, onSelectChapter = onSelectChapter)
            }
        }
    }
}

// Список оглавления без шторки: отдельно — для проверки в Compose-тестах.
@Composable
internal fun TocList(
    chapters: List<ReaderTocItem>,
    currentChapterId: Long?,
    onSelectChapter: (chapterId: Long) -> Unit,
) {
    val newestFirst = remember(chapters) { chapters.asReversed() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = tocInitialScrollIndex(chapters, currentChapterId),
    )
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        items(items = newestFirst, key = { item -> item.chapterId }) { item ->
            TocRow(
                item = item,
                isCurrent = item.chapterId == currentChapterId,
                onClick = { onSelectChapter(item.chapterId) },
            )
        }
    }
}

@Composable
private fun TocRow(item: ReaderTocItem, isCurrent: Boolean, onClick: () -> Unit) {
    val currentLabel = stringResource(R.string.reader_toc_current)
    val readLabel = stringResource(R.string.reader_toc_read)
    val textColor = when {
        isCurrent -> ManhwareadPalette.DarkPrimary
        item.isRead -> ManhwareadPalette.DarkOnSurfaceVariant.copy(alpha = READ_CHAPTER_ALPHA)
        else -> ManhwareadPalette.DarkOnSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TocItemMinHeight)
            .clickable(onClick = onClick)
            .semantics {
                selected = isCurrent
                when {
                    isCurrent -> stateDescription = currentLabel
                    item.isRead -> stateDescription = readLabel
                }
            }
            .padding(horizontal = TocHorizontalPadding),
    ) {
        Box(
            modifier = Modifier
                .width(TocAccentWidth)
                .height(TocAccentHeight)
                .background(
                    color = if (isCurrent) ManhwareadPalette.DarkPrimary else Color.Transparent,
                    shape = RoundedCornerShape(TocAccentWidth),
                ),
        )
        Text(
            text = item.title,
            style = ManhwareadTypography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = TocAccentSpacing),
        )
        if (item.isRead && !isCurrent) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = ManhwareadPalette.DarkOnSurfaceVariant.copy(alpha = READ_CHAPTER_ALPHA),
                modifier = Modifier.size(TocCheckSize),
            )
        }
    }
}
