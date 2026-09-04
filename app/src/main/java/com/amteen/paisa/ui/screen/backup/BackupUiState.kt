package com.amteen.paisa.ui.screen.backup

import com.amteen.paisa.domain.usecase.ImportMode
import com.amteen.paisa.domain.usecase.ImportPreview
import com.amteen.paisa.domain.usecase.ImportSource

/** What the screen is waiting on, so exactly one spinner can be shown. */
enum class BackupBusy { NONE, EXPORTING, READING, IMPORTING, SNAPSHOTTING }

/**
 * What the import/export screen renders.
 *
 * [pendingExport] is the document text held between "the user asked to export" and
 * "the system told us where to put it" — the SAF picker is a round trip, and building
 * the document twice would be wasteful and could produce two different timestamps.
 */
data class BackupUiState(
    val busy: BackupBusy = BackupBusy.NONE,
    val localBackups: List<String> = emptyList(),

    val pendingExport: PendingExport? = null,
    val preview: ImportPreview? = null,

    val message: String? = null,
    val error: String? = null,
) {
    val isBusy: Boolean get() = busy != BackupBusy.NONE
}

/** A built document waiting for the user to choose a destination. */
data class PendingExport(
    val content: String,
    val suggestedName: String,
    val mimeType: String,
)

sealed interface BackupEvent {
    data object ExportJsonRequested : BackupEvent
    data object ExportCsvRequested : BackupEvent

    /** The SAF destination came back — or `null` if the user cancelled. */
    data class ExportDestinationChosen(val text: String?) : BackupEvent
    data object ExportDismissed : BackupEvent

    /** The user picked a file to import; [text] is its contents. */
    data class FileRead(val text: String, val source: ImportSource, val mode: ImportMode) :
        BackupEvent

    data object PreviewDismissed : BackupEvent
    data object PreviewConfirmed : BackupEvent

    data object SnapshotRequested : BackupEvent
    data object MessageShown : BackupEvent
    data object DismissError : BackupEvent
}
