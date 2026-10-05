package com.manhwaread.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
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
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.appErrorText
import com.manhwaread.core.designsystem.component.AgeRatingBadge
import com.manhwaread.core.designsystem.component.MangaCover
import com.manhwaread.core.designsystem.component.MangaTypeBadge
import com.manhwaread.core.designsystem.component.ManhwareadPrimaryButton
import com.manhwaread.core.designsystem.component.ManhwareadSecondaryButton
import com.manhwaread.core.designsystem.component.RatingIndicator
import com.manhwaread.core.designsystem.component.ReadingStatusChip
import com.manhwaread.core.model.NextChapterTarget
import com.manhwaread.core.model.ReadingStatus
import com.manhwaread.source.api.MangaStatus
import java.text.DateFormat
import java.util.Date

private val CoverWidth = 112.dp
private val ContentPadding = 16.dp
private val ItemSpacing = 8.dp
private val RowPadding = 12.dp
private val BlurRadius = 24.dp
private const val SHORT_DESCRIPTION_THRESHOLD = 160

// Точка входа карточки тайтла: из каталога (sourceId+mangaUrl) или из
// библиотеки/истории (mangaId). Подключается в NavHost приложения.
@Composable
fun DetailsRoute(
    mangaId: Long?,
    sourceId: Long?,
    mangaUrl: String?,
    onBack: () -> Unit,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
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
            onReadingStatusChange = viewModel::onReadingStatusChange,
            onTabSelected = viewModel::onTabSelected,
            onChapterQueryChange = viewModel::onChapterQueryChange,
            onToggleSortOrder = viewModel::onToggleSortOrder,
            onToggleOnlyUnread = viewModel::onToggleOnlyUnread,
            onChapterClick = viewModel::onChapterClick,
            onChapterDownload = viewModel::onChapterDownloadClick,
            onOpenReader = onOpenReader,
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
    // Переход в читалку — навигационное сообщение: обрабатывается отдельно, без snackbar.
    val openReader = message as? DetailsMessage.OpenReader
    LaunchedEffect(openReader) {
        if (openReader != null) {
            actions.onOpenReader(openReader.mangaId, openReader.chapterId)
            actions.onMessageShown()
        }
    }
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
                        // Переведённый тайтл приоритетнее исходного (titleRu живёт в БД).
                        text = state.manga?.let { manga -> manga.titleRu ?: manga.title }
                            ?: stringResource(R.string.details_title),
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
private fun detailsMessageText(message: DetailsMessage): String? = when (message) {
    is DetailsMessage.AddedToDownloads ->
        stringResource(R.string.details_added_to_downloads, message.chapterName)
    DetailsMessage.AddedToLibrary -> stringResource(R.string.details_added_to_library)
    DetailsMessage.RemovedFromLibrary -> stringResource(R.string.details_removed_from_library)
    is DetailsMessage.OpenReader -> null
}

@Composable
private fun DetailsContent(
    state: DetailsUiState,
    manga: MangaEntity,
    actions: DetailsActions,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        contentPadding = PaddingValues(bottom = ContentPadding),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        if (state.isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            DetailsHeader(manga = manga)
        }
        item {
            DetailsActionButtons(state = state, manga = manga, actions = actions)
        }
        state.error?.let { error ->
            item {
                DetailsErrorRow(error = error, onRetry = actions.onRetry)
            }
        }
        item {
            DetailsTabsRow(
                selectedTab = state.selectedTab,
                chaptersCount = state.chapters.size,
                onTabSelected = actions.onTabSelected,
            )
        }

        when (state.selectedTab) {
            DetailsTab.INFO -> detailsInfoItems(manga = manga, onToggleLibrary = actions.onToggleLibrary)
            DetailsTab.CHAPTERS -> detailsChaptersItems(state = state, actions = actions)
        }
    }
}

@Composable
private fun DetailsErrorRow(error: AppError, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = appErrorText(error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.details_retry))
        }
    }
}

@Composable
private fun DetailsTabsRow(
    selectedTab: DetailsTab,
    chaptersCount: Int,
    onTabSelected: (DetailsTab) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Tab(
            selected = selectedTab == DetailsTab.INFO,
            onClick = { onTabSelected(DetailsTab.INFO) },
            text = { Text(stringResource(R.string.details_tab_info)) },
        )
        Tab(
            selected = selectedTab == DetailsTab.CHAPTERS,
            onClick = { onTabSelected(DetailsTab.CHAPTERS) },
            text = {
                Text(stringResource(R.string.details_tab_chapters, chaptersCount))
            },
        )
    }
}

private fun LazyListScope.detailsInfoItems(
    manga: MangaEntity,
    onToggleLibrary: () -> Unit,
) {
    manga.description?.let { description ->
        item {
            ExpandableDescription(description = description)
        }
    }
    if (manga.genres.isNotEmpty()) {
        item {
            DetailsGenresRow(genres = manga.genres)
        }
    }
    item {
        DetailsMetaSection(manga = manga)
    }
    item {
        Button(
            onClick = onToggleLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ContentPadding, vertical = ItemSpacing),
        ) {
            Text(
                stringResource(
                    if (manga.inLibrary) {
                        R.string.details_remove_from_library
                    } else {
                        R.string.details_add_to_library
                    },
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsGenresRow(genres: List<String>) {
    FlowRow(
        modifier = Modifier.padding(horizontal = ContentPadding),
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        genres.forEach { genre ->
            AssistChip(onClick = {}, label = { Text(genre) })
        }
    }
}

private fun LazyListScope.detailsChaptersItems(
    state: DetailsUiState,
    actions: DetailsActions,
) {
    item {
        OutlinedTextField(
            value = state.chapterQuery,
            onValueChange = actions.onChapterQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ContentPadding),
            singleLine = true,
            label = { Text(stringResource(R.string.details_search_chapters_hint)) },
        )
    }
    item {
        ChaptersControlBar(
            state = state,
            actions = actions,
            modifier = Modifier.padding(horizontal = ContentPadding),
        )
    }
    if (state.filteredChapters.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.details_no_chapters),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(ContentPadding),
            )
        }
    } else {
        items(state.filteredChapters, key = { chapter -> chapter.id }) { chapter ->
            ChapterRow(
                chapter = chapter,
                onClick = { actions.onChapterClick(chapter) },
                onDownload = { actions.onChapterDownload(chapter) },
                modifier = Modifier.padding(horizontal = ContentPadding),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DetailsHeader(manga: MangaEntity) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ItemSpacing),
    ) {
        if (manga.thumbnailUrl != null) {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(BlurRadius),
            )
        }
        // Скрим затемнения и градиента (гарантирует читаемость на всех версиях Android, включая API < 31).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(ContentPadding),
        ) {
            MangaCover(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                modifier = Modifier.width(CoverWidth),
                shape = ManhwareadShapes.Card,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ItemSpacing / 2),
            ) {
                Text(
                    text = manga.titleRu ?: manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (manga.titleRu != null && manga.titleRu != manga.title) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ItemSpacing / 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    manga.type?.let { type ->
                        MangaTypeBadge(type = type)
                    }
                    val ageRating = manga.ageRating ?: if (manga.nsfw) "18+" else null
                    ageRating?.let { age ->
                        AgeRatingBadge(ageRating = age)
                    }
                    val rating = manga.rating
                    if (rating != null && rating > 0f) {
                        RatingIndicator(rating = rating)
                    }
                }
                Text(
                    text = stringResource(R.string.details_status, statusText(manga.status)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                manga.author?.let { author ->
                    Text(
                        text = stringResource(R.string.details_author, author),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsActionButtons(
    state: DetailsUiState,
    manga: MangaEntity,
    actions: DetailsActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPadding),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        when (val target = state.nextChapterTarget) {
            is NextChapterTarget.Start -> {
                ManhwareadPrimaryButton(
                    onClick = { actions.onChapterClick(target.chapter.toEntity()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.details_action_start_reading))
                }
            }
            is NextChapterTarget.Resume -> {
                ManhwareadPrimaryButton(
                    onClick = { actions.onChapterClick(target.chapter.toEntity()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.details_action_resume_reading, target.chapter.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is NextChapterTarget.ReRead -> {
                ManhwareadSecondaryButton(
                    onClick = { actions.onChapterClick(target.chapter.toEntity()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.details_action_reread))
                }
            }
            NextChapterTarget.None -> Unit
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadingStatus.entries.forEach { status ->
                val isSelected = manga.inLibrary && manga.readingStatus == status
                ReadingStatusChip(
                    status = status,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) {
                            actions.onReadingStatusChange(null)
                        } else {
                            actions.onReadingStatusChange(status)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpandableDescription(description: String) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPadding),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (description.length > SHORT_DESCRIPTION_THRESHOLD) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(
                        if (isExpanded) R.string.details_read_less else R.string.details_read_more,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DetailsMetaSection(manga: MangaEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        manga.altTitle?.let { alt ->
            Text(
                text = stringResource(R.string.details_alt_title, alt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        manga.year?.let { year ->
            Text(
                text = stringResource(R.string.details_year, year),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        manga.artist?.let { artist ->
            Text(
                text = stringResource(R.string.details_artist, artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChaptersControlBar(
    state: DetailsUiState,
    actions: DetailsActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.sortOrder == ChapterSortOrder.OLDEST_FIRST,
                onClick = actions.onToggleSortOrder,
                label = {
                    Text(
                        stringResource(
                            if (state.sortOrder == ChapterSortOrder.NEWEST_FIRST) {
                                R.string.details_sort_newest
                            } else {
                                R.string.details_sort_oldest
                            },
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            FilterChip(
                selected = state.onlyUnread,
                onClick = actions.onToggleOnlyUnread,
                label = { Text(stringResource(R.string.details_filter_unread)) },
            )
        }
        Text(
            text = "${state.filteredChapters.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Строка главы: клик открывает читалку (скачанную офлайн, нескачанную —
// стримингом), кнопка скачивания ставит главу в очередь загрузок.
@Composable
private fun ChapterRow(
    chapter: ChapterEntity,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
        IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(R.string.details_download_chapter),
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
