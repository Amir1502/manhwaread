package com.manhwaread.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.manhwaread.core.database.MangaEntity

private val CardImageHeight = 160.dp
private val CardMinWidth = 110.dp
private val ContentPadding = 12.dp
private val CardSpacing = 8.dp
private val BadgePadding = 4.dp

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
                    Text(
                        text = stringResource(
                            if (state.allCount == 0) R.string.library_empty else R.string.library_search_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> LibraryGrid(state = state, actions = actions, modifier = Modifier.weight(1f))
            }
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
    Card(onClick = { actions.onOpenManga(manga) }) {
        Column {
            Box {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(CardImageHeight),
                )
                IconButton(
                    onClick = { actions.onRemove(manga) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(BadgePadding),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.library_remove),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                // Русский перевод тайтла приоритетен; откат — название из источника.
                text = manga.titleRu ?: manga.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(ContentPadding / 2),
            )
        }
    }
}
