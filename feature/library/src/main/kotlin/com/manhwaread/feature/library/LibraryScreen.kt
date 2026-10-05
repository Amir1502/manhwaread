package com.manhwaread.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.component.MangaBadge
import com.manhwaread.core.designsystem.component.MangaCover
import com.manhwaread.core.designsystem.component.MangaTypeBadge
import com.manhwaread.core.designsystem.component.RatingIndicator
import com.manhwaread.core.designsystem.component.statusColor
import com.manhwaread.core.designsystem.component.statusTextRes
import com.manhwaread.core.model.ReadingStatus

private val CardMinWidth = 108.dp
private val ContentPadding = 12.dp
private val CardSpacing = 8.dp
private val BadgePadding = 6.dp
private val CloseButtonSize = 28.dp
private val CloseIconSize = 16.dp

// Точка входа раздела «Библиотека» (подключается в NavHost приложения).
@Composable
fun LibraryRoute(
    onOpenManga: (mangaId: Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        actions = LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onStatusSelected = viewModel::onStatusSelected,
            onOpenManga = { manga -> onOpenManga(manga.id) },
            onRemove = viewModel::onRemove,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.library_title)) })
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding),
                singleLine = true,
                label = { Text(stringResource(R.string.library_search_hint)) },
            )
            LibraryStatusTabs(
                selectedStatus = state.selectedStatus,
                statusCounts = state.statusCounts,
                allCount = state.allCount,
                onStatusSelected = actions.onStatusSelected,
            )
            when {
                state.isLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val emptyMessage = when {
                        state.allCount == 0 -> stringResource(R.string.library_empty)
                        state.query.isNotBlank() -> stringResource(R.string.library_search_empty)
                        else -> stringResource(R.string.library_status_empty)
                    }
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> LibraryGrid(state = state, actions = actions, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryStatusTabs(
    selectedStatus: ReadingStatus?,
    statusCounts: Map<ReadingStatus, Int>,
    allCount: Int,
    onStatusSelected: (ReadingStatus?) -> Unit,
) {
    val statuses = remember { listOf(null) + ReadingStatus.entries }
    val selectedIndex = statuses.indexOf(selectedStatus).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = ContentPadding,
        modifier = Modifier.fillMaxWidth(),
    ) {
        statuses.forEach { status ->
            val isSelected = selectedStatus == status
            val title = if (status == null) {
                stringResource(R.string.library_tab_all)
            } else {
                stringResource(statusTextRes(status))
            }
            val count = if (status == null) {
                allCount
            } else {
                statusCounts[status] ?: 0
            }

            Tab(
                selected = isSelected,
                onClick = { onStatusSelected(status) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (count > 0) {
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryGrid(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CardMinWidth),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        items(state.items, key = { manga -> manga.id }) { manga ->
            LibraryCard(manga = manga, actions = actions)
        }
    }
}

@Composable
private fun LibraryCard(manga: MangaEntity, actions: LibraryActions) {
    Card(
        onClick = { actions.onOpenManga(manga) },
        shape = ManhwareadShapes.Card,
    ) {
        Column {
            MangaCover(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxWidth(),
                shape = ManhwareadShapes.Card,
                overlay = {
                    manga.type?.let { type ->
                        MangaTypeBadge(
                            type = type,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(BadgePadding),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(BadgePadding)
                            .size(CloseButtonSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .clickable { actions.onRemove(manga) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.library_remove),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(CloseIconSize),
                        )
                    }

                    manga.readingStatus?.let { status ->
                        MangaBadge(
                            text = stringResource(statusTextRes(status)),
                            containerColor = statusColor(status),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(BadgePadding),
                        )
                    }

                    val rating = manga.rating
                    if (rating != null && rating > 0f) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = ManhwareadShapes.Badge,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(BadgePadding),
                        ) {
                            RatingIndicator(
                                rating = rating,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
            )
            Text(
                // Русский перевод тайтла приоритетен; откат — название из источника.
                text = manga.titleRu ?: manga.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(ContentPadding / 2),
            )
        }
    }
}
