package com.amteen.paisa.ui.screen.paymentmethod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import com.amteen.paisa.domain.usecase.ArchivePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.DeletePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.PaymentMethodInput
import com.amteen.paisa.domain.usecase.ReferenceCount
import com.amteen.paisa.domain.usecase.RemovalOutcome
import com.amteen.paisa.domain.usecase.ReorderPaymentMethodsUseCase
import com.amteen.paisa.domain.usecase.SavePaymentMethodUseCase
import com.amteen.paisa.domain.usecase.SetDefaultPaymentMethodUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Payment method management.
 *
 * Same shape as the category list — derived from the repository, with the drag
 * order held locally until the finger lifts — plus the default marker, which lives
 * in settings rather than on the payment method itself. Keeping it in settings
 * means "which one is default" is a single value that cannot go inconsistent by
 * having two rows both claim it.
 */
class PaymentMethodViewModel(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val savePaymentMethod: SavePaymentMethodUseCase,
    private val deletePaymentMethod: DeletePaymentMethodUseCase,
    private val archivePaymentMethod: ArchivePaymentMethodUseCase,
    private val reorderPaymentMethods: ReorderPaymentMethodsUseCase,
    private val setDefaultPaymentMethod: SetDefaultPaymentMethodUseCase,
) : ViewModel() {

    private data class Chrome(
        val isLoading: Boolean = true,
        val showArchived: Boolean = false,
        val editor: PaymentMethodEditorUi? = null,
        val pendingRemoval: PendingPaymentMethodRemoval? = null,
        val message: String? = null,
        val error: String? = null,
    )

    private val pendingOrder = MutableStateFlow<List<String>?>(null)
    private val chrome = MutableStateFlow(Chrome())

    val uiState: StateFlow<PaymentMethodUiState> = combine(
        paymentMethodRepository.paymentMethods,
        settingsRepository.settings,
        pendingOrder,
        chrome,
    ) { methods, settings, order, ui ->
        val defaultId = settings.defaultPaymentMethodId
        PaymentMethodUiState(
            isLoading = ui.isLoading,
            active = methods.filterNot { it.archived }
                .applyOrder(order)
                .map { it.toRow(defaultId) },
            archived = methods.filter { it.archived }.map { it.toRow(defaultId) },
            showArchived = ui.showArchived,
            editor = ui.editor,
            pendingRemoval = ui.pendingRemoval,
            message = ui.message,
            error = ui.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaymentMethodUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.load()
            paymentMethodRepository.load()
            chrome.update { it.copy(isLoading = false) }
        }
    }

    fun onEvent(event: PaymentMethodEvent) {
        when (event) {
            PaymentMethodEvent.AddRequested -> chrome.update {
                it.copy(editor = PaymentMethodEditorUi())
            }

            is PaymentMethodEvent.EditRequested -> openEditor(event.id)

            is PaymentMethodEvent.EditorNameChanged -> chrome.update { ui ->
                ui.copy(editor = ui.editor?.copy(name = event.text, nameError = null))
            }

            is PaymentMethodEvent.EditorIconSelected -> chrome.update { ui ->
                ui.copy(editor = ui.editor?.copy(iconKey = event.key))
            }

            PaymentMethodEvent.EditorDismissed -> chrome.update { it.copy(editor = null) }

            PaymentMethodEvent.EditorSaved -> saveEditor()

            is PaymentMethodEvent.DefaultToggled -> toggleDefault(event.id)

            is PaymentMethodEvent.RemoveRequested -> requestRemoval(event.id)

            PaymentMethodEvent.RemoveConfirmed -> confirmRemoval()

            PaymentMethodEvent.RemoveDismissed -> chrome.update { it.copy(pendingRemoval = null) }

            is PaymentMethodEvent.ArchiveToggled -> setArchived(event.id, event.archived)

            is PaymentMethodEvent.Moved -> move(event.fromId, event.toId)

            PaymentMethodEvent.OrderCommitted -> pendingOrder.value?.let { persist(it) }

            is PaymentMethodEvent.MoveStep -> moveStep(event.id, event.up)

            PaymentMethodEvent.ToggleArchivedVisible -> chrome.update {
                it.copy(showArchived = !it.showArchived)
            }

            PaymentMethodEvent.MessageShown -> chrome.update { it.copy(message = null) }

            PaymentMethodEvent.DismissError -> chrome.update { it.copy(error = null) }
        }
    }

    // -- Editor -------------------------------------------------------------

    private fun openEditor(id: String) {
        val row = uiState.value.let { it.active + it.archived }.firstOrNull { it.id == id } ?: return
        chrome.update {
            it.copy(editor = PaymentMethodEditorUi(id = row.id, name = row.name, iconKey = row.iconKey))
        }
    }

    private fun saveEditor() {
        val editor = chrome.value.editor ?: return
        if (editor.isSaving) return
        chrome.update { it.copy(editor = editor.copy(isSaving = true, nameError = null)) }

        viewModelScope.launch {
            val result = savePaymentMethod(
                PaymentMethodInput(id = editor.id, name = editor.name, iconKey = editor.iconKey),
            )
            when (result) {
                is AppResult.Ok -> chrome.update {
                    it.copy(editor = null, message = "Saved.")
                }
                is AppResult.Err -> {
                    val error = result.error
                    chrome.update { ui ->
                        val current = ui.editor?.copy(isSaving = false) ?: return@update ui
                        if (error is AppError.Validation &&
                            error.field == SavePaymentMethodUseCase.FIELD_NAME
                        ) {
                            ui.copy(editor = current.copy(nameError = error.message))
                        } else {
                            ui.copy(editor = current, error = error.displayMessage)
                        }
                    }
                }
            }
        }
    }

    // -- Default ------------------------------------------------------------

    private fun toggleDefault(id: String) {
        val currentDefault = settingsRepository.settings.value.defaultPaymentMethodId
        val next = if (currentDefault == id) null else id

        viewModelScope.launch {
            when (val result = setDefaultPaymentMethod(next)) {
                is AppResult.Err -> chrome.update { it.copy(error = result.error.displayMessage) }
                is AppResult.Ok -> chrome.update {
                    it.copy(
                        message = if (next == null) {
                            "No default payment method."
                        } else {
                            "Set as the default."
                        },
                    )
                }
            }
        }
    }

    // -- Removal ------------------------------------------------------------

    private fun requestRemoval(id: String) {
        val row = uiState.value.let { it.active + it.archived }.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val references = ReferenceCount(
                transactions = transactionRepository.countByPaymentMethod(id),
            )
            chrome.update {
                it.copy(
                    pendingRemoval = PendingPaymentMethodRemoval(id, row.name, references),
                )
            }
        }
    }

    private fun confirmRemoval() {
        val pending = chrome.value.pendingRemoval ?: return
        chrome.update { it.copy(pendingRemoval = null) }

        viewModelScope.launch {
            val result = if (pending.canDelete) {
                deletePaymentMethod(pending.id)
            } else {
                archivePaymentMethod(pending.id, archived = true)
            }

            when (result) {
                is AppResult.Err -> chrome.update { it.copy(error = result.error.displayMessage) }
                is AppResult.Ok -> chrome.update {
                    it.copy(message = describe(result.value, pending.name))
                }
            }
        }
    }

    private fun describe(outcome: RemovalOutcome, name: String): String = when (outcome) {
        RemovalOutcome.Deleted -> "“$name” deleted."
        RemovalOutcome.Archived -> "“$name” archived. It still appears in your history."
        is RemovalOutcome.Blocked ->
            "“$name” is used by ${outcome.references.describe()}, so it was archived instead."
    }

    private fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            when (val result = archivePaymentMethod(id, archived)) {
                is AppResult.Err -> chrome.update { it.copy(error = result.error.displayMessage) }
                is AppResult.Ok -> chrome.update {
                    it.copy(message = if (archived) "Archived." else "Restored.")
                }
            }
        }
    }

    // -- Reordering ---------------------------------------------------------

    private fun move(fromId: String, toId: String) {
        if (fromId == toId) return
        val current = uiState.value.active.map { it.id }
        val from = current.indexOf(fromId)
        val to = current.indexOf(toId)
        if (from < 0 || to < 0) return
        pendingOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun moveStep(id: String, up: Boolean) {
        val current = uiState.value.active.map { it.id }
        val index = current.indexOf(id)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in current.indices) return
        val reordered = current.toMutableList().apply { add(target, removeAt(index)) }
        pendingOrder.value = reordered
        persist(reordered)
    }

    private fun persist(visibleOrder: List<String>) {
        viewModelScope.launch {
            when (val result = reorderPaymentMethods(fullOrderWith(visibleOrder))) {
                is AppResult.Err -> {
                    pendingOrder.value = null
                    chrome.update { it.copy(error = result.error.displayMessage) }
                }
                is AppResult.Ok -> pendingOrder.value = null
            }
        }
    }

    /** Archived rows are hidden but still hold positions; leave theirs alone. */
    private fun fullOrderWith(visibleOrder: List<String>): List<String> {
        val all = paymentMethodRepository.paymentMethods.value
        val visible = visibleOrder.toSet()
        val queue = ArrayDeque(visibleOrder)
        return all.map { method ->
            if (method.id in visible && queue.isNotEmpty()) queue.removeFirst() else method.id
        }
    }

    private fun List<PaymentMethod>.applyOrder(order: List<String>?): List<PaymentMethod> {
        if (order == null) return this
        val position = order.withIndex().associate { (index, id) -> id to index }
        return sortedBy { position[it.id] ?: Int.MAX_VALUE }
    }

    private fun PaymentMethod.toRow(defaultId: String?) = PaymentMethodRowUi(
        id = id,
        name = name,
        iconKey = iconKey,
        archived = archived,
        isDefault = id == defaultId,
    )
}
