package com.amteen.paisa.ui.screen.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.repository.BackupRepository
import com.amteen.paisa.domain.usecase.CommitImportUseCase
import com.amteen.paisa.domain.usecase.ExportBackupUseCase
import com.amteen.paisa.domain.usecase.ExportCsvUseCase
import com.amteen.paisa.domain.usecase.ImportSource
import com.amteen.paisa.domain.usecase.PrepareImportUseCase
import com.amteen.paisa.domain.usecase.WriteLocalBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Import and export.
 *
 * The screen owns no logic beyond the SAF round trip: the ViewModel builds documents,
 * validates incoming ones and commits a validated preview. Every step goes through a
 * use case so the interesting half — validation — is testable off-device.
 */
class BackupViewModel(
    private val exportBackup: ExportBackupUseCase,
    private val exportCsv: ExportCsvUseCase,
    private val prepareImport: PrepareImportUseCase,
    private val commitImport: CommitImportUseCase,
    private val writeLocalBackup: WriteLocalBackupUseCase,
    private val backups: BackupRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        refreshLocalBackups()
    }

    fun onEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.ExportJsonRequested -> buildExport(
                mimeType = MIME_JSON,
                suggestedName = "paisa-backup-${today()}.json",
            ) { exportBackup() }

            BackupEvent.ExportCsvRequested -> buildExport(
                mimeType = MIME_CSV,
                suggestedName = "paisa-transactions-${today()}.csv",
            ) { exportCsv() }

            is BackupEvent.ExportDestinationChosen -> {
                // The screen has already written the bytes through the resolver; all
                // that is left is to drop the held document and say what happened.
                _uiState.update {
                    it.copy(
                        pendingExport = null,
                        busy = BackupBusy.NONE,
                        message = event.text,
                    )
                }
            }

            BackupEvent.ExportDismissed -> _uiState.update {
                it.copy(pendingExport = null, busy = BackupBusy.NONE)
            }

            is BackupEvent.FileRead -> validate(event)

            BackupEvent.PreviewDismissed -> _uiState.update { it.copy(preview = null) }

            BackupEvent.PreviewConfirmed -> commit()

            BackupEvent.SnapshotRequested -> viewModelScope.launch {
                _uiState.update { it.copy(busy = BackupBusy.SNAPSHOTTING) }
                when (val result = writeLocalBackup("manual")) {
                    is AppResult.Ok -> {
                        _uiState.update {
                            it.copy(busy = BackupBusy.NONE, message = "Saved ${result.value}.")
                        }
                        refreshLocalBackups()
                    }
                    is AppResult.Err -> _uiState.update {
                        it.copy(busy = BackupBusy.NONE, error = result.error.displayMessage)
                    }
                }
            }

            BackupEvent.MessageShown -> _uiState.update { it.copy(message = null) }
            BackupEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun buildExport(
        mimeType: String,
        suggestedName: String,
        build: suspend () -> AppResult<String>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = BackupBusy.EXPORTING) }
            when (val result = build()) {
                is AppResult.Ok -> _uiState.update {
                    it.copy(
                        // Held rather than rebuilt after the picker returns: building
                        // twice would be wasted work and could stamp two different
                        // export times onto one file.
                        pendingExport = PendingExport(result.value, suggestedName, mimeType),
                        busy = BackupBusy.NONE,
                    )
                }
                is AppResult.Err -> _uiState.update {
                    it.copy(busy = BackupBusy.NONE, error = result.error.displayMessage)
                }
            }
        }
    }

    private fun validate(event: BackupEvent.FileRead) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = BackupBusy.READING) }
            val result = when (event.source) {
                ImportSource.JSON -> prepareImport.fromJson(event.text, event.mode)
                ImportSource.CSV -> prepareImport.fromCsv(event.text, event.mode)
            }
            when (result) {
                is AppResult.Ok -> _uiState.update {
                    it.copy(busy = BackupBusy.NONE, preview = result.value)
                }
                is AppResult.Err -> _uiState.update {
                    it.copy(busy = BackupBusy.NONE, error = result.error.displayMessage)
                }
            }
        }
    }

    private fun commit() {
        val preview = _uiState.value.preview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = BackupBusy.IMPORTING, preview = null) }
            when (val result = commitImport(preview)) {
                is AppResult.Ok -> {
                    _uiState.update {
                        it.copy(
                            busy = BackupBusy.NONE,
                            message = "Imported ${preview.incomingTransactions} transaction(s).",
                        )
                    }
                    refreshLocalBackups()
                }
                is AppResult.Err -> _uiState.update {
                    it.copy(busy = BackupBusy.NONE, error = result.error.displayMessage)
                }
            }
        }
    }

    private fun refreshLocalBackups() {
        viewModelScope.launch {
            val names = runCatching { backups.listLocalBackups() }.getOrDefault(emptyList())
            _uiState.update { it.copy(localBackups = names) }
        }
    }

    private companion object {
        const val MIME_JSON = "application/json"
        const val MIME_CSV = "text/csv"
    }
}
