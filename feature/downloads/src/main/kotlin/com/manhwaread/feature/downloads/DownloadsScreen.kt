package com.manhwaread.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.StageStatus

private val ContentPadding = 12.dp
private val ItemSpacing = 8.dp
private val SelfCheckIndicatorSize = 18.dp

// Точка входа раздела «Загрузки» (подключается в NavHost приложения).
@Composable
fun DownloadsRoute(viewModel: DownloadsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(
        state = state,
        actions = DownloadsActions(
            onCancelTask = viewModel::onCancelTask,
            onCancelJob = viewModel::onCancelJob,
            onRunSelfCheck = viewModel::onRunSelfCheck,
            onSelfCheckShown = viewModel::onSelfCheckShown,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val selfCheck = state.selfCheck
    val successText = stringResource(R.string.downloads_selfcheck_success, selfCheck?.segments ?: 0, selfCheck?.overlays ?: 0)
    val failedText = stringResource(R.string.downloads_selfcheck_failed, selfCheck?.status?.name.orEmpty())
    LaunchedEffect(state.selfCheck) {
        val result = state.selfCheck ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(if (result.success) successText else failedText)
        actions.onSelfCheckShown()
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                actions = {
                    SelfCheckAction(
                        isRunning = state.isSelfCheckRunning,
                        onRun = actions.onRunSelfCheck,
                    )
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.tasks.isEmpty() && state.jobs.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(ContentPadding),
                verticalArrangement = Arrangement.spacedBy(ItemSpacing),
            ) {
                if (state.tasks.isNotEmpty()) {
                    item {
                        SectionHeader(textRes = R.string.downloads_tasks_header)
                    }
                    state.tasks.forEach { task ->
                        item(key = "task-${task.taskId}") {
                            TaskCard(task = task, onCancel = { actions.onCancelTask(task.taskId) })
                        }
                    }
                }
                if (state.jobs.isNotEmpty()) {
                    item {
                        SectionHeader(textRes = R.string.downloads_jobs_header)
                    }
                    state.jobs.forEach { job ->
                        item(key = "job-${job.jobId}") {
                            JobCard(job = job, onCancel = { actions.onCancelJob(job.jobId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = ItemSpacing),
    )
}

// Кнопка офлайн-самопроверки конвейера: во время прогона — индикатор вместо иконки.
@Composable
private fun SelfCheckAction(isRunning: Boolean, onRun: () -> Unit) {
    if (isRunning) {
        Box(
            modifier = Modifier.padding(horizontal = ContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(SelfCheckIndicatorSize),
                strokeWidth = 2.dp,
            )
        }
    } else {
        IconButton(onClick = onRun) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = stringResource(R.string.downloads_selfcheck),
            )
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTaskRow, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.mangaTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.chapterName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = downloadStatusText(task.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task.status == DownloadStatus.RUNNING) {
                    LinearProgressIndicator(
                        progress = { task.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (task.status == DownloadStatus.PENDING || task.status == DownloadStatus.RUNNING) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.downloads_cancel),
                    )
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: TranslationJobRow, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.mangaTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = job.chapterName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stageStatusText(job.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (job.attempts > 0) {
                    Text(
                        text = stringResource(R.string.downloads_attempts, job.attempts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                job.lastError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isJobCancellable(job.status)) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.downloads_cancel),
                    )
                }
            }
        }
    }
}

private fun isJobCancellable(status: StageStatus): Boolean =
    status != StageStatus.DONE && status != StageStatus.FAILED && status != StageStatus.CANCELLED

@Composable
private fun downloadStatusText(status: DownloadStatus): String = stringResource(
    when (status) {
        DownloadStatus.PENDING -> R.string.downloads_status_pending
        DownloadStatus.RUNNING -> R.string.downloads_status_running
        DownloadStatus.COMPLETED -> R.string.downloads_status_completed
        DownloadStatus.FAILED -> R.string.downloads_status_failed
        DownloadStatus.CANCELLED -> R.string.downloads_status_cancelled
    },
)

@Composable
private fun stageStatusText(status: StageStatus): String = stringResource(
    when (status) {
        StageStatus.QUEUED -> R.string.downloads_stage_queued
        StageStatus.DOWNLOADING -> R.string.downloads_stage_downloading
        StageStatus.ANALYZING -> R.string.downloads_stage_analyzing
        StageStatus.TRANSLATING -> R.string.downloads_stage_translating
        StageStatus.COMPOSITING -> R.string.downloads_stage_compositing
        StageStatus.DONE -> R.string.downloads_stage_done
        StageStatus.FAILED -> R.string.downloads_stage_failed
        StageStatus.CANCELLED -> R.string.downloads_stage_cancelled
    },
)
