package com.amteen.paisa.ui.screen.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.core.time.DateFormatters
import com.amteen.paisa.domain.usecase.ImportMode
import com.amteen.paisa.domain.usecase.ImportPreview
import com.amteen.paisa.domain.usecase.ImportSource
import com.amteen.paisa.ui.theme.PaisaTheme
import java.time.ZoneId

/**
 * Import and export.
 *
 * Everything goes through the Storage Access Framework, so the app needs **no storage
 * permission at all** — the user picks the file, and the system hands back a URI
 * scoped to that one document. See CLAUDE.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onEvent: (BackupEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Which flavour of import the user started, so the picker result knows how to
    // parse what comes back.
    // rememberSaveable, not remember: the SAF picker is another process, and losing
    // this across a process death would silently discard the file the user chose.
    var pendingImport by rememberSaveable {
        mutableStateOf<Pair<ImportSource, ImportMode>?>(null)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(BackupEvent.MessageShown)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(BackupEvent.DismissError)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_ANY),
    ) { uri ->
        val export = state.pendingExport
        when {
            // Cancelling is not a failure, so it says nothing.
            uri == null -> onEvent(BackupEvent.ExportDismissed)

            // The held document is gone — realistically only after process death
            // mid-picker. Say so rather than looking like a successful export.
            export == null -> onEvent(
                BackupEvent.ExportFailed("The export was lost before it could be saved. Try again."),
            )

            else -> when (val outcome = context.writeText(uri, export.content)) {
                is IoOutcome.Written -> onEvent(BackupEvent.ExportSucceeded(export.suggestedName))
                is IoOutcome.Failed -> onEvent(BackupEvent.ExportFailed(outcome.reason))
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val request = pendingImport
        pendingImport = null
        when {
            uri == null -> Unit // Cancelled.

            request == null -> onEvent(
                BackupEvent.FileReadFailed("Lost track of that import. Pick the file again."),
            )

            else -> when (val read = context.readText(uri)) {
                is IoRead.Text -> onEvent(BackupEvent.FileRead(read.value, request.first, request.second))
                is IoRead.Failed -> onEvent(BackupEvent.FileReadFailed(read.reason))
            }
        }
    }

    // The document is built before the picker opens, so launching is a side effect of
    // it becoming available rather than something the button does directly.
    LaunchedEffect(state.pendingExport) {
        state.pendingExport?.let { saveLauncher.launch(it.suggestedName) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_backup)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isBusy) {
                item(key = "busy") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = state.busy.label(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            item(key = "export") {
                SectionCard(
                    title = stringResource(R.string.backup_export_title),
                    body = stringResource(R.string.backup_export_body),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEvent(BackupEvent.ExportJsonRequested) },
                            enabled = !state.isBusy,
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.backup_export_json))
                        }
                        OutlinedButton(
                            onClick = { onEvent(BackupEvent.ExportCsvRequested) },
                            enabled = !state.isBusy,
                        ) {
                            Text(stringResource(R.string.backup_export_csv))
                        }
                    }
                }
            }

            item(key = "import") {
                SectionCard(
                    title = stringResource(R.string.backup_import_title),
                    body = stringResource(R.string.backup_import_body),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    pendingImport = ImportSource.JSON to ImportMode.MERGE
                                    openLauncher.launch(JSON_TYPES)
                                },
                                enabled = !state.isBusy,
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.backup_import_merge))
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingImport = ImportSource.CSV to ImportMode.MERGE
                                    openLauncher.launch(CSV_TYPES)
                                },
                                enabled = !state.isBusy,
                            ) {
                                Text(stringResource(R.string.backup_import_csv))
                            }
                        }
                        TextButton(
                            onClick = {
                                pendingImport = ImportSource.JSON to ImportMode.REPLACE
                                openLauncher.launch(JSON_TYPES)
                            },
                            enabled = !state.isBusy,
                        ) {
                            Text(stringResource(R.string.backup_import_replace))
                        }
                        Text(
                            text = stringResource(R.string.backup_replace_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "local") {
                SectionCard(
                    title = stringResource(R.string.backup_local_title),
                    body = stringResource(R.string.backup_local_body),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEvent(BackupEvent.SnapshotRequested) },
                            enabled = !state.isBusy,
                        ) {
                            Text(stringResource(R.string.backup_snapshot_now))
                        }

                        if (state.localBackups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.backup_local_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.localBackups.forEach { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "privacy") {
                Text(
                    text = stringResource(R.string.backup_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.preview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = { onEvent(BackupEvent.PreviewConfirmed) },
            onDismiss = { onEvent(BackupEvent.PreviewDismissed) },
        )
    }
}

/**
 * The confirm step of validate → preview → confirm → commit.
 *
 * Says what will happen in counts before anything is written, and for a Replace says
 * what will be **lost** — the number that actually matters and the one a user cannot
 * recover if it surprises them.
 */
@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        preview.isAlreadyUpToDate -> R.string.backup_preview_uptodate_title
                        preview.isDestructive -> R.string.backup_preview_replace_title
                        else -> R.string.backup_preview_merge_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.exportedAt?.let { instant ->
                    Text(
                        text = stringResource(
                            R.string.backup_preview_taken,
                            DateFormatters.fullDate(
                                instant.atZone(ZoneId.systemDefault()).toLocalDate(),
                            ),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (preview.isAlreadyUpToDate) {
                    // A row of zeros reads as a broken import. Naming what was
                    // recognised makes it obvious the file was read correctly and
                    // simply had nothing new in it.
                    Text(stringResource(R.string.backup_preview_uptodate_body))
                    Text(
                        text = stringResource(
                            R.string.backup_preview_uptodate_detail,
                            preview.duplicateTransactions,
                            preview.duplicateCategories,
                            preview.duplicatePaymentMethods,
                            preview.duplicateBudgets,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(
                            R.string.backup_preview_counts,
                            preview.incomingTransactions,
                            preview.incomingCategories,
                            preview.incomingPaymentMethods,
                            preview.incomingBudgets,
                        ),
                    )

                    // Only the non-zero ones, so an ordinary import stays terse.
                    if (preview.totalDuplicates > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.backup_preview_skipped,
                                preview.totalDuplicates,
                                preview.totalDuplicates,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (preview.repairedReferences > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.backup_preview_repaired,
                            preview.repairedReferences,
                            preview.repairedReferences,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (preview.unreadable.isNotEmpty()) {
                    HorizontalDivider()
                    // Capped: a badly formed file can produce hundreds of these, and
                    // a dialog the user cannot scroll past is worse than a summary.
                    preview.unreadable.take(MAX_PROBLEMS_SHOWN).forEach { problem ->
                        Text(
                            text = problem,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (preview.unreadable.size > MAX_PROBLEMS_SHOWN) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.backup_preview_more_problems,
                                preview.unreadable.size - MAX_PROBLEMS_SHOWN,
                                preview.unreadable.size - MAX_PROBLEMS_SHOWN,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (preview.isDestructive) {
                    HorizontalDivider()
                    Text(
                        text = pluralStringResource(
                            R.plurals.backup_preview_will_delete,
                            preview.replacedTransactions,
                            preview.replacedTransactions,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.backup_preview_snapshot_first),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (preview.hasAnythingToDo) {
                TextButton(onClick = onConfirm) {
                    Text(
                        stringResource(
                            if (preview.isDestructive) {
                                R.string.backup_preview_replace_confirm
                            } else {
                                R.string.backup_preview_merge_confirm
                            },
                        ),
                    )
                }
            } else {
                // Nothing to do, so there is nothing to confirm. A greyed-out "Add"
                // beside a row of counts just leaves the user wondering what is wrong.
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.calendar_day_close))
                }
            }
        },
        dismissButton = {
            if (preview.hasAnythingToDo) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun SectionCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun BackupBusy.label(): String = stringResource(
    when (this) {
        BackupBusy.EXPORTING -> R.string.backup_busy_exporting
        BackupBusy.READING -> R.string.backup_busy_reading
        BackupBusy.IMPORTING -> R.string.backup_busy_importing
        BackupBusy.SNAPSHOTTING -> R.string.backup_busy_snapshotting
        BackupBusy.NONE -> R.string.backup_busy_exporting
    },
)

/**
 * Writes through the content resolver, reporting *why* it failed.
 *
 * The previous version collapsed every failure into a boolean, and the caller then
 * turned `false` into a null message — which showed nothing at all. An export that
 * quietly does nothing is the worst outcome this screen has, because the user walks
 * away believing they have a backup.
 *
 * It was also wrong: `openOutputStream(...)?.use { … } != null` tests the value of
 * `use`, which is whatever the lambda returned — `flush()`, i.e. `Unit`. `Unit` is
 * never null, so the check only ever meant "the stream opened and nothing threw".
 */
private fun Context.writeText(uri: Uri, content: String): IoOutcome = try {
    val stream = contentResolver.openOutputStream(uri, "wt")
    if (stream == null) {
        IoOutcome.Failed("Could not open that location for writing.")
    } else {
        stream.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        IoOutcome.Written
    }
} catch (e: Exception) {
    IoOutcome.Failed(e.readableReason("Could not write to that location."))
}

private fun Context.readText(uri: Uri): IoRead = try {
    val stream = contentResolver.openInputStream(uri)
    if (stream == null) {
        IoRead.Failed("Could not open that file.")
    } else {
        IoRead.Text(stream.use { it.readBytes().toString(Charsets.UTF_8) })
    }
} catch (e: Exception) {
    IoRead.Failed(e.readableReason("Could not read that file."))
}

private sealed interface IoOutcome {
    data object Written : IoOutcome
    data class Failed(val reason: String) : IoOutcome
}

private sealed interface IoRead {
    data class Text(val value: String) : IoRead
    data class Failed(val reason: String) : IoRead
}

/**
 * A message worth showing.
 *
 * An exception's own text is often a bare path or a class name, so it is only used
 * when it reads like a sentence; otherwise the caller's plain-English fallback wins.
 */
private fun Exception.readableReason(fallback: String): String {
    val detail = message?.trim()
    return if (detail.isNullOrEmpty() || detail.length > 120) fallback else "$fallback ($detail)"
}

/**
 * `CreateDocument` needs a concrete type, but the suggested filename carries the real
 * extension and the user may well want to save a CSV as `text/plain` anyway.
 */
private const val MIME_ANY = "application/octet-stream"

/** Some file managers report a Paisa backup as plain text, so both are accepted. */
private val JSON_TYPES = arrayOf("application/json", "text/plain", "*/*")
private val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")

private const val MAX_PROBLEMS_SHOWN = 5

// -- Previews ---------------------------------------------------------------

@Preview(name = "Backup", showBackground = true, heightDp = 900)
@Composable
private fun BackupPreview() {
    PaisaTheme {
        BackupScreen(
            state = BackupUiState(
                localBackups = listOf(
                    "backup-2026-09-04T09-12-00Z-manual.json",
                    "backup-2026-09-01T20-04-11Z-before-import.json",
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Backup · dark", showBackground = true, heightDp = 900, uiMode = 32)
@Composable
private fun BackupDarkPreview() {
    PaisaTheme {
        BackupScreen(state = BackupUiState(), onEvent = {}, onBack = {})
    }
}
