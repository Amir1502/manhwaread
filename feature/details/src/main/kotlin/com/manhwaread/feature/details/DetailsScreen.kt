package com.manhwaread.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.designsystem.appErrorText
import com.manhwaread.source.api.MangaStatus
import java.text.DateFormat
import java.util.Date

private val CoverWidth = 110.dp
private val CoverHeight = 160.dp
private val ContentPadding = 16.dp
private val ItemSpacing = 8.dp
private val RowPadding = 12.dp

// Точка входа карточки тайтла: из каталога (sourceId+mangaUrl) или из
// библиотеки/истории (mangaId). Подключается в NavHost приложения.
@Composable
fun DetailsRoute(
    mangaId: Long?,
    sourceId: Long?,
    mangaUrl: String?,
    onBack: () -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    LaunchedEffect(mangaId, sourceId, mangaUrl) {
        when {
            mangaId != null && mangaId > 0L -> viewModel.openById(mangaId)
            sourceId != null && mangaUrl != null -> viewModel.openBySourceUrl(sourceId, mangaUrl)
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailsScreen(
        state = state,
        actions = DetailsActions(
            onBack = onBack,
            onToggleLibrary = viewModel::onToggleLibrary,
            onChapterClick = viewModel::onChapterClick,
            onRetry = viewModel::onRetry,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    actions: DetailsActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message
    // Текст сообщения вычисляется в композиции: stringResource недоступен в suspend-лямбде.
    val messageText = message?.let { detailsMessage -> detailsMessageText(detailsMessage) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.manga?.title ?: stringResource(R.string.details_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.details_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val manga = state.manga
        when {
            manga == null && state.error != null -> ErrorPane(
                error = requireNotNull(state.error),
                onRetry = actions.onRetry,
                innerPadding = innerPadding,
            )
            manga == null -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            else -> DetailsContent(state = state, manga = manga, actions = actions, innerPadding = innerPadding)
        }
    }
}

@Composable
private fun detailsMessageText(message: DetailsMessage): String = when (message) {
    is DetailsMessage.AddedToDownloads ->
        stringResource(R.string.details_added_to_downloads, message.chapterName)
    DetailsMessage.AddedToLibrary -> stringResource(R.string.details_added_to_library)
    DetailsMessage.RemovedFromLibrary -> stringResource(R.string.details_removed_from_library)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(
    state: DetailsUiState,
    manga: MangaEntity,
    actions: DetailsActions,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        contentPadding = PaddingValues(ContentPadding),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        if (state.isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(ContentPadding)) {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(CoverWidth).height(CoverHeight),
                )
                Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                    manga.author?.let { author ->
                        Text(
                            text = stringResource(R.string.details_author, author),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    manga.artist?.let { artist ->
                        Text(
                            text = stringResource(R.string.details_artist, artist),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = stringResource(R.string.details_status, statusText(manga.status)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (manga.genres.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                            manga.genres.forEach { genre ->
                                AssistChip(onClick = {}, label = { Text(genre) })
                            }
                        }
                    }
                }
            }
        }
        manga.description?.let { description ->
            item {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Button(onClick = actions.onToggleLibrary, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (manga.inLibrary) R.string.details_remove_from_library else R.string.details_add_to_library,
                    ),
                )
            }
        }
        state.error?.let { error ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appErrorText(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onRetry) {
                        Text(stringResource(R.string.details_retry))
                    }
                }
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            Text(
                text = stringResource(R.string.details_chapters),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.chapters.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.details_no_chapters),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.chapters, key = { chapter -> chapter.id }) { chapter ->
            ChapterRow(chapter = chapter, onClick = { actions.onChapterClick(chapter) })
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (chapter.read) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            val subtitle = chapterSubtitle(chapter)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (chapter.read) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun chapterSubtitle(chapter: ChapterEntity): String {
    val date = if (chapter.dateUploadMs > 0L) {
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(chapter.dateUploadMs))
    } else {
        ""
    }
    val scanlator = chapter.scanlator.orEmpty()
    return listOf(date, scanlator).filter { part -> part.isNotBlank() }.joinToString(" • ")
}

@Composable
private fun statusText(status: MangaStatus): String = stringResource(
    when (status) {
        MangaStatus.UNKNOWN -> R.string.details_status_unknown
        MangaStatus.ONGOING -> R.string.details_status_ongoing
        MangaStatus.COMPLETED -> R.string.details_status_completed
        MangaStatus.HIATUS -> R.string.details_status_hiatus
        MangaStatus.CANCELLED -> R.string.details_status_cancelled
    },
)

@Composable
private fun ErrorPane(error: AppError, onRetry: () -> Unit, innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text(text = appErrorText(error), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.details_retry))
            }
        }
    }
}
