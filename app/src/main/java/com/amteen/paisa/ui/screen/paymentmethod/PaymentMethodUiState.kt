package com.amteen.paisa.ui.screen.paymentmethod

import com.amteen.paisa.domain.usecase.ReferenceCount

data class PaymentMethodRowUi(
    val id: String,
    val name: String,
    val iconKey: String,
    val archived: Boolean,
    /** Pre-selected on the add screen; at most one row is ever true. */
    val isDefault: Boolean,
)

/**
 * The add/edit sheet. Null when closed.
 *
 * A payment method is a name and an icon, so it gets a bottom sheet rather than its
 * own route — a whole screen and a back-stack entry to type one word would be a
 * worse trade than the sheet.
 */
data class PaymentMethodEditorUi(
    val id: String? = null,
    val name: String = "",
    val iconKey: String = "wallet",
    val nameError: String? = null,
    val isSaving: Boolean = false,
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = !isSaving && name.isNotBlank()
}

data class PendingPaymentMethodRemoval(
    val id: String,
    val name: String,
    val references: ReferenceCount,
) {
    val canDelete: Boolean get() = !references.isReferenced
}

data class PaymentMethodUiState(
    val isLoading: Boolean = true,
    val active: List<PaymentMethodRowUi> = emptyList(),
    val archived: List<PaymentMethodRowUi> = emptyList(),
    val showArchived: Boolean = false,
    val editor: PaymentMethodEditorUi? = null,
    val pendingRemoval: PendingPaymentMethodRemoval? = null,
    val message: String? = null,
    val error: String? = null,
)

sealed interface PaymentMethodEvent {
    data object AddRequested : PaymentMethodEvent
    data class EditRequested(val id: String) : PaymentMethodEvent
    data class EditorNameChanged(val text: String) : PaymentMethodEvent
    data class EditorIconSelected(val key: String) : PaymentMethodEvent
    data object EditorDismissed : PaymentMethodEvent
    data object EditorSaved : PaymentMethodEvent

    /** Tapping the current default clears it rather than re-setting it. */
    data class DefaultToggled(val id: String) : PaymentMethodEvent

    data class RemoveRequested(val id: String) : PaymentMethodEvent
    data object RemoveConfirmed : PaymentMethodEvent
    data object RemoveDismissed : PaymentMethodEvent

    data class ArchiveToggled(val id: String, val archived: Boolean) : PaymentMethodEvent

    data class Moved(val fromId: String, val toId: String) : PaymentMethodEvent
    data object OrderCommitted : PaymentMethodEvent
    data class MoveStep(val id: String, val up: Boolean) : PaymentMethodEvent

    data object ToggleArchivedVisible : PaymentMethodEvent
    data object MessageShown : PaymentMethodEvent
    data object DismissError : PaymentMethodEvent
}
