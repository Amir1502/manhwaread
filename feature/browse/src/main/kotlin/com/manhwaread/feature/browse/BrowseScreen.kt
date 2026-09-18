package com.manhwaread.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.manhwaread.core.designsystem.appErrorText
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source

private val CardImageHeight = 160.dp
private val CardMinWidth = 110.dp
private val ContentPadding = 12.dp
private val CardSpacing = 8.dp
private val BadgePadding = 6.dp
private val BadgeVerticalPadding = 2.dp

// Точка входа раздела «Каталог» (подключается в NavHost приложения).
@Composable
fun BrowseRoute(
    onOpenManga: (sourceId: Long, mangaUrl: String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BrowseScreen(
        state = state,
        actions = BrowseActions(
            onSourceSelected = viewModel::onSourceSelected,
            onBackToSources = viewModel::onBackToSources,
            onModeSelected = viewModel::onModeSelected,
            onQueryChange = viewModel::onQueryChange,
            onSearchSubmit = viewModel::onSearchSubmit,
            onLoadMore = viewModel::onLoadMore,
            onRetry = viewModel::onRetry,
            onMangaClick = { manga -> onOpenManga(manga.sourceId, manga.url) },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    actions: BrowseActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(state.selectedSource?.name ?: stringResource(R.string.browse_title))
                },
                navigationIcon = {
                    if (state.isSourceSelected) {
                        IconButton(onClick = actions.onBackToSources) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.browse_back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isSourceSelected) {
            SourceCatalog(state = state, actions = actions, innerPadding = innerPadding)
        } else {
            SourcesList(
                sources = state.sources,
                onSourceSelected = actions.onSourceSelected,
                innerPadding = innerPadding,
            )
        }
    }
}

// Выбор источника: карточки подключённых источников из реестра.
@Composable
private fun SourcesList(
    sources: List<Source>,
    onSourceSelected: (Source) -> Unit,
    innerPadding: PaddingValues,
) {
    if (sources.isEmpty()) {
        Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.browse_sources_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        contentPadding = PaddingValues(ContentPadding),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        items(sources, key = { source -> source.id }) { source ->
            Card(onClick = { onSourceSelected(source) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ContentPadding)) {
                    Text(text = source.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (source.isNsfw) {
                            "${source.lang} • ${stringResource(R.string.browse_nsfw_badge)}"
                        } else {
                            source.lang
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// Каталог выбранного источника: поиск, режимы, сетка тайтлов.
@Composable
private fun SourceCatalog(
    state: BrowseUiState,
    actions: BrowseActions,
    innerPadding: PaddingValues,
) {
    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.browse_search_hint)) },
            )
            IconButton(onClick = actions.onSearchSubmit) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.browse_search_action),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            FilterChip(
                selected = state.mode == BrowseListingMode.POPULAR,
                onClick = { actions.onModeSelected(BrowseListingMode.POPULAR) },
                label = { Text(stringResource(R.string.browse_mode_popular)) },
            )
            FilterChip(
                selected = state.mode == BrowseListingMode.LATEST,
                onClick = { actions.onModeSelected(BrowseListingMode.LATEST) },
                label = { Text(stringResource(R.string.browse_mode_latest)) },
            )
        }
        state.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(ContentPadding),
            ) {
                Column(modifier = Modifier.padding(ContentPadding)) {
                    Text(text = appErrorText(error), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = actions.onRetry) {
                        Text(stringResource(R.string.browse_retry))
                    }
                }
            }
        }
        MangaGrid(
            listing = state.listing,
            hasError = state.error != null,
            onMangaClick = actions.onMangaClick,
            onLoadMore = actions.onLoadMore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MangaGrid(
    listing: BrowseListing,
    hasError: Boolean,
    onMangaClick: (SManga) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CardMinWidth),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        items(listing.items, key = { manga -> "${manga.sourceId}:${manga.url}" }) { manga ->
            MangaGridCard(manga = manga, onClick = { onMangaClick(manga) })
        }
        if (listing.items.isEmpty() && !listing.isLoading && !hasError) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(ContentPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.browse_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (listing.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(ContentPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (listing.hasNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(ContentPadding), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.browse_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaGridCard(manga: SManga, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column {
            Box {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(CardImageHeight),
                )
                if (manga.nsfw) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(BadgePadding),
                    ) {
                        Text(
                            text = stringResource(R.string.browse_nsfw_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(
                                horizontal = BadgePadding,
                                vertical = BadgeVerticalPadding,
                            ),
                        )
                    }
                }
            }
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(BadgePadding),
            )
        }
    }
}
