package com.amteen.paisa.ui.screen.paymentmethod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.amteen.paisa.R
import com.amteen.paisa.domain.usecase.ReferenceCount
import com.amteen.paisa.ui.components.ConfirmDialog
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.IconPicker
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.dragHandle
import com.amteen.paisa.ui.components.rememberDragDropState
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.PaisaTheme

private const val ACTIVE_KEY_PREFIX = "pm-active-"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodScreen(
    state: PaymentMethodUiState,
    onEvent: (PaymentMethodEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(PaymentMethodEvent.MessageShown)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(PaymentMethodEvent.DismissError)
        }
    }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        listState = listState,
        isDraggable = { key -> key is String && key.startsWith(ACTIVE_KEY_PREFIX) },
        onMove = { from, to ->
            onEvent(
                PaymentMethodEvent.Moved(
                    fromId = (from as String).removePrefix(ACTIVE_KEY_PREFIX),
                    toId = (to as String).removePrefix(ACTIVE_KEY_PREFIX),
                ),
            )
        },
        onSettle = { onEvent(PaymentMethodEvent.OrderCommitted) },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_payment_methods)) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(PaymentMethodEvent.AddRequested) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.payment_method_add)) },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding).fillMaxSize())

            state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                icon = Icons.Filled.Payments,
                title = stringResource(R.string.payment_method_empty_title),
                message = stringResource(R.string.payment_method_empty_message),
                actionLabel = stringResource(R.string.payment_method_add),
                onAction = { onEvent(PaymentMethodEvent.AddRequested) },
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 96.dp,
                ),
            ) {
                items(state.active, key = { "$ACTIVE_KEY_PREFIX${it.id}" }) { row ->
                    val key = "$ACTIVE_KEY_PREFIX${row.id}"
                    val dragging = dragDropState.draggingItemKey == key
                    val index = state.active.indexOfFirst { it.id == row.id }

                    PaymentMethodRow(
                        row = row,
                        onClick = { onEvent(PaymentMethodEvent.EditRequested(row.id)) },
                        onToggleDefault = { onEvent(PaymentMethodEvent.DefaultToggled(row.id)) },
                        onArchive = { onEvent(PaymentMethodEvent.ArchiveToggled(row.id, true)) },
                        onRestore = null,
                        onDelete = { onEvent(PaymentMethodEvent.RemoveRequested(row.id)) },
                        onMoveUp = if (index > 0) {
                            { onEvent(PaymentMethodEvent.MoveStep(row.id, up = true)) }
                        } else {
                            null
                        },
                        onMoveDown = if (index < state.active.lastIndex) {
                            { onEvent(PaymentMethodEvent.MoveStep(row.id, up = false)) }
                        } else {
                            null
                        },
                        elevated = dragging,
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                if (dragging) {
                                    translationY = dragDropState.draggingItemOffset
                                    shadowElevation = 8.dp.toPx()
                                }
                            }
                            .dragHandle(dragDropState, key),
                    )
                }

                item(key = "hint") {
                    Text(
                        text = stringResource(R.string.payment_method_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        ArchivedHeader(
                            count = state.archived.size,
                            expanded = state.showArchived,
                            onToggle = { onEvent(PaymentMethodEvent.ToggleArchivedVisible) },
                        )
                    }
                    if (state.showArchived) {
                        items(state.archived, key = { "pm-archived-${it.id}" }) { row ->
                            PaymentMethodRow(
                                row = row,
                                onClick = { onEvent(PaymentMethodEvent.EditRequested(row.id)) },
                                onToggleDefault = null,
                                onArchive = null,
                                onRestore = {
                                    onEvent(PaymentMethodEvent.ArchiveToggled(row.id, false))
                                },
                                onDelete = {
                                    onEvent(PaymentMethodEvent.RemoveRequested(row.id))
                                },
                                onMoveUp = null,
                                onMoveDown = null,
                            )
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        EditorSheet(
            editor = editor,
            onEvent = onEvent,
        )
    }

    state.pendingRemoval?.let { pending ->
        if (pending.canDelete) {
            ConfirmDialog(
                title = stringResource(R.string.payment_method_delete_title, pending.name),
                message = stringResource(R.string.payment_method_delete_message),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = { onEvent(PaymentMethodEvent.RemoveConfirmed) },
                onDismiss = { onEvent(PaymentMethodEvent.RemoveDismissed) },
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.payment_method_archive_title, pending.name),
                message = stringResource(
                    R.string.payment_method_archive_message,
                    pending.references.describe(),
                ),
                confirmLabel = stringResource(R.string.action_archive),
                onConfirm = { onEvent(PaymentMethodEvent.RemoveConfirmed) },
                onDismiss = { onEvent(PaymentMethodEvent.RemoveDismissed) },
                destructive = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    editor: PaymentMethodEditorUi,
    onEvent: (PaymentMethodEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onEvent(PaymentMethodEvent.EditorDismissed) },
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                text = stringResource(
                    if (editor.isEditing) {
                        R.string.payment_method_edit_title
                    } else {
                        R.string.payment_method_new_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = editor.name,
                onValueChange = { onEvent(PaymentMethodEvent.EditorNameChanged(it)) },
                label = { Text(stringResource(R.string.payment_method_name_label)) },
                singleLine = true,
                isError = editor.nameError != null,
                supportingText = editor.nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.category_icon),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            IconPicker(
                selectedKey = editor.iconKey,
                accentColor = MaterialTheme.colorScheme.primary,
                onSelect = { onEvent(PaymentMethodEvent.EditorIconSelected(it)) },
                choices = CategoryIcons.paymentMethodChoices,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onEvent(PaymentMethodEvent.EditorSaved) },
                enabled = editor.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.payment_method_archived_count, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.action_collapse else R.string.action_expand,
                ),
            )
        }
    }
}

@Composable
private fun PaymentMethodRow(
    row: PaymentMethodRowUi,
    onClick: () -> Unit,
    onToggleDefault: (() -> Unit)?,
    onArchive: (() -> Unit)?,
    onRestore: (() -> Unit)?,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val moveActions = remember(onMoveUp, onMoveDown) {
        buildList {
            onMoveUp?.let { add(CustomAccessibilityAction("Move up") { it(); true }) }
            onMoveDown?.let { add(CustomAccessibilityAction("Move down") { it(); true }) }
        }
    }

    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = if (row.isDefault) {
            { Text(stringResource(R.string.payment_method_is_default)) }
        } else {
            null
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Icon(
                    imageVector = CategoryIcons[row.iconKey],
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onToggleDefault?.let { toggle ->
                    IconButton(onClick = toggle) {
                        Icon(
                            imageVector = if (row.isDefault) {
                                Icons.Filled.Star
                            } else {
                                Icons.Filled.StarBorder
                            },
                            contentDescription = stringResource(
                                if (row.isDefault) {
                                    R.string.payment_method_clear_default
                                } else {
                                    R.string.payment_method_set_default
                                },
                                row.name,
                            ),
                            tint = if (row.isDefault) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(
                                R.string.payment_method_actions_for,
                                row.name,
                            ),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onClick() },
                        )
                        onMoveUp?.let { move ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_up)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                                },
                                onClick = { menuOpen = false; move() },
                            )
                        }
                        onMoveDown?.let { move ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_down)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                                },
                                onClick = { menuOpen = false; move() },
                            )
                        }
                        onArchive?.let { archive ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_archive)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Archive, contentDescription = null)
                                },
                                onClick = { menuOpen = false; archive() },
                            )
                        }
                        onRestore?.let { restore ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_restore)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Unarchive, contentDescription = null)
                                },
                                onClick = { menuOpen = false; restore() },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        },
        colors = if (elevated) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            ListItemDefaults.colors()
        },
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { customActions = moveActions },
    )
}

// -- Previews ---------------------------------------------------------------

private val previewState = PaymentMethodUiState(
    isLoading = false,
    active = listOf(
        PaymentMethodRowUi("1", "Cash", "cash", false, isDefault = true),
        PaymentMethodRowUi("2", "Debit Card", "card", false, isDefault = false),
        PaymentMethodRowUi("3", "Mobile Wallet", "wallet", false, isDefault = false),
    ),
    archived = listOf(PaymentMethodRowUi("4", "Old Cheque Book", "receipt", true, false)),
)

@Preview(name = "Payment methods — light", showBackground = true)
@Composable
private fun PaymentMethodPreviewLight() {
    PaisaTheme { PaymentMethodScreen(previewState, {}, {}) }
}

@Preview(name = "Payment methods — dark", showBackground = true, uiMode = 32)
@Composable
private fun PaymentMethodPreviewDark() {
    PaisaTheme { PaymentMethodScreen(previewState, {}, {}) }
}

@Preview(name = "Payment methods — archive offered", showBackground = true)
@Composable
private fun PaymentMethodPreviewBlocked() {
    PaisaTheme {
        PaymentMethodScreen(
            previewState.copy(
                pendingRemoval = PendingPaymentMethodRemoval("1", "Cash", ReferenceCount(48)),
            ),
            {}, {},
        )
    }
}
