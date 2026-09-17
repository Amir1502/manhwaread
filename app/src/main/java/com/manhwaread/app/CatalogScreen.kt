package com.manhwaread.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.manhwaread.core.source.SManga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: CatalogState,
    onSearch: (String) -> Unit,
    onMore: () -> Unit,
    onOpen: (SManga) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val catalogPosition = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.selected?.title ?: stringResource(R.string.catalog_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (state.selected != null) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val selected = state.selected
        if (selected != null) {
            MangaDetails(state, selected, onRetry, Modifier.padding(padding))
        } else {
            LazyColumn(
                state = catalogPosition,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "search") { SearchBox(state.query, onSearch) }
                item(key = "notice") { FoundationNotice() }
                item(key = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(if (state.query.isBlank()) R.string.popular else R.string.search_results), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.catalog_subtitle), style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(state.items, key = { "manga:${it.sourceId}:${it.url}" }) { manga -> MangaRow(manga) { onOpen(manga) } }
                if (state.loading) item(key = "loading") { LoadingRow() }
                state.error?.let { error -> item(key = "error") { ErrorCard(error, onRetry) } }
                if (!state.loading && state.error == null && state.items.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.empty_catalog), modifier = Modifier.padding(vertical = 24.dp)) }
                }
                if (state.hasNextPage && state.error == null) {
                    item(key = "more") {
                        OutlinedButton(onClick = onMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(committedQuery: String, onSearch: (String) -> Unit) {
    var query by rememberSaveable(committedQuery) { mutableStateOf(committedQuery) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); onSearch(query) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = submit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.search)) }
    }
}

@Composable
private fun MangaRow(manga: SManga, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Cover(manga, Modifier.width(82.dp).height(116.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(manga.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                manga.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (manga.genres.isNotEmpty()) Text(manga.genres.take(4).joinToString(" · "), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Cover(manga: SManga, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        AsyncImage(
            model = manga.thumbnailUrl,
            contentDescription = stringResource(R.string.cover_description, manga.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MangaDetails(state: CatalogState, manga: SManga, onRetry: () -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable(manga.url) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(manga, Modifier.width(112.dp).height(164.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(manga.title, style = MaterialTheme.typography.titleLarge)
                    manga.author?.let { Text(stringResource(R.string.author, it), style = MaterialTheme.typography.bodySmall) }
                    manga.artist?.let { Text(stringResource(R.string.artist, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item(key = "description") {
            Column {
                Text(manga.description ?: stringResource(R.string.no_description), maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                if (!manga.description.isNullOrBlank()) TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.collapse_description else R.string.expand_description))
                }
            }
        }
        if (manga.genres.isNotEmpty()) item(key = "genres") { Text(manga.genres.joinToString(" · "), style = MaterialTheme.typography.labelLarge) }
        item(key = "notice") { FoundationNotice() }
        if (state.loading) item(key = "loading") { LoadingRow() }
        state.error?.let { error -> item(key = "error") { ErrorCard(error, onRetry) } }
        if (!state.loading && state.error == null) {
            item(key = "chapter-heading") { Text(stringResource(R.string.chapters_count, state.chapters.size), style = MaterialTheme.typography.titleLarge) }
            if (state.chapters.isEmpty()) item(key = "empty") { Text(stringResource(R.string.no_chapters)) }
            items(state.chapters, key = { "chapter:${it.url}" }) { chapter ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(chapter.name, style = MaterialTheme.typography.bodyLarge)
                    chapter.scanlator?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun FoundationNotice() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(stringResource(R.string.foundation_notice), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoadingRow() {
    val label = stringResource(R.string.loading)
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun ErrorCard(error: CatalogFailure, onRetry: () -> Unit) {
    val message = when (error) {
        CatalogFailure.NETWORK -> R.string.error_network
        CatalogFailure.RATE_LIMIT -> R.string.error_rate_limit
        CatalogFailure.FORMAT -> R.string.error_format
        CatalogFailure.UNKNOWN -> R.string.error_unknown
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(message))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
