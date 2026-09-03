package com.amteen.paisa.ui.screen.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.usecase.DeleteTransactionUseCase
import com.amteen.paisa.domain.usecase.GetTransactionDetailsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val details: TransactionDetails? = null,
    val error: String? = null,
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false,
)

sealed interface TransactionDetailEvent {
    data object Reload : TransactionDetailEvent
    data object RequestDelete : TransactionDetailEvent
    data object ConfirmDelete : TransactionDetailEvent
    data object DismissDelete : TransactionDetailEvent
}

class TransactionDetailViewModel(
    private val transactionId: String,
    private val getTransactionDetails: GetTransactionDetailsUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: TransactionDetailEvent) {
        when (event) {
            TransactionDetailEvent.Reload -> load()
            TransactionDetailEvent.RequestDelete -> _uiState.update { it.copy(showDeleteConfirm = true) }
            TransactionDetailEvent.DismissDelete -> _uiState.update { it.copy(showDeleteConfirm = false) }
            TransactionDetailEvent.ConfirmDelete -> delete()
        }
    }

    /**
     * Reloaded rather than observed: returning from the edit screen must show the
     * new values, and a one-shot read on resume is simpler — and cheaper — than
     * holding a flow open for a single record.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getTransactionDetails(transactionId)) {
                is AppResult.Ok -> _uiState.update {
                    it.copy(isLoading = false, details = result.value)
                }
                is AppResult.Err -> _uiState.update {
                    it.copy(isLoading = false, error = result.error.displayMessage)
                }
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            when (val result = deleteTransaction(transactionId)) {
                is AppResult.Ok -> _uiState.update {
                    it.copy(showDeleteConfirm = false, deleted = true)
                }
                is AppResult.Err -> _uiState.update {
                    it.copy(showDeleteConfirm = false, error = result.error.displayMessage)
                }
            }
        }
    }
}
