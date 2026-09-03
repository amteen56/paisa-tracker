package com.amteen.paisa.ui.screen.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.amteen.paisa.R
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.usecase.ReferenceCount
import com.amteen.paisa.ui.components.ConfirmDialog
import com.amteen.paisa.ui.components.EmptyState
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.dragHandle
import com.amteen.paisa.ui.components.onColorFor
import com.amteen.paisa.ui.components.rememberDragDropState
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.PaisaTheme

private const val ACTIVE_KEY_PREFIX = "cat-"
private const val ARCHIVED_KEY_PREFIX = "archived-"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    state: CategoryListUiState,
    onEvent: (CategoryListEvent) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(CategoryListEvent.MessageShown)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(CategoryListEvent.DismissError)
        }
    }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        listState = listState,
        isDraggable = { key -> key is String && key.startsWith(ACTIVE_KEY_PREFIX) },
        onMove = { from, to ->
            onEvent(
                CategoryListEvent.Moved(
                    fromId = (from as String).removePrefix(ACTIVE_KEY_PREFIX),
                    toId = (to as String).removePrefix(ACTIVE_KEY_PREFIX),
                ),
            )
        },
        onSettle = { onEvent(CategoryListEvent.OrderCommitted) },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_categories)) },
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
                onClick = onAddCategory,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.category_add)) },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            ScopeTabs(
                scope = state.scope,
                onSelect = { onEvent(CategoryListEvent.ScopeSelected(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.isLoading -> LoadingState(Modifier.fillMaxSize())

                state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Category,
                    title = stringResource(R.string.category_empty_title),
                    message = stringResource(R.string.category_empty_message),
                    actionLabel = stringResource(R.string.category_add),
                    onAction = onAddCategory,
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                ) {
                    items(
                        items = state.active,
                        key = { "$ACTIVE_KEY_PREFIX${it.id}" },
                    ) { row ->
                        val key = "$ACTIVE_KEY_PREFIX${row.id}"
                        val dragging = dragDropState.draggingItemKey == key
                        val index = state.active.indexOfFirst { it.id == row.id }

                        CategoryRow(
                            row = row,
                            onClick = { onEditCategory(row.id) },
                            onEdit = { onEditCategory(row.id) },
                            onArchive = {
                                onEvent(CategoryListEvent.ArchiveToggled(row.id, true))
                            },
                            onRestore = null,
                            onDelete = { onEvent(CategoryListEvent.RemoveRequested(row.id)) },
                            onMoveUp = if (index > 0) {
                                { onEvent(CategoryListEvent.MoveStep(row.id, up = true)) }
                            } else {
                                null
                            },
                            onMoveDown = if (index < state.active.lastIndex) {
                                { onEvent(CategoryListEvent.MoveStep(row.id, up = false)) }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    if (dragging) {
                                        translationY = dragDropState.draggingItemOffset
                                        shadowElevation = 8.dp.toPx()
                                    }
                                }
                                .dragHandle(dragDropState, key),
                            elevated = dragging,
                        )
                    }

                    item(key = "reorder-hint") {
                        Text(
                            text = stringResource(R.string.category_reorder_hint),
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
                                onToggle = { onEvent(CategoryListEvent.ToggleArchivedVisible) },
                            )
                        }

                        if (state.showArchived) {
                            items(
                                items = state.archived,
                                key = { "$ARCHIVED_KEY_PREFIX${it.id}" },
                            ) { row ->
                                CategoryRow(
                                    row = row,
                                    onClick = { onEditCategory(row.id) },
                                    onEdit = { onEditCategory(row.id) },
                                    onArchive = null,
                                    onRestore = {
                                        onEvent(CategoryListEvent.ArchiveToggled(row.id, false))
                                    },
                                    onDelete = {
                                        onEvent(CategoryListEvent.RemoveRequested(row.id))
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
    }

    state.pendingRemoval?.let { pending ->
        RemovalDialog(
            pending = pending,
            onConfirm = { onEvent(CategoryListEvent.RemoveConfirmed) },
            onDismiss = { onEvent(CategoryListEvent.RemoveDismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeTabs(
    scope: CategoryScope,
    onSelect: (CategoryScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(CategoryScope.EXPENSE, CategoryScope.INCOME)
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = scope == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
            ) {
                Text(
                    when (tab) {
                        CategoryScope.EXPENSE -> stringResource(R.string.scope_expense)
                        else -> stringResource(R.string.scope_income)
                    },
                )
            }
        }
    }
}

@Composable
private fun ArchivedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.category_archived_count, count),
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

/**
 * One category.
 *
 * The overflow menu carries Move up / Move down as well as the drag gesture: a
 * long-press drag cannot be performed with TalkBack running, so it can never be the
 * only route to reordering. The same two actions are also published as semantics
 * custom actions, which is how a screen reader surfaces them.
 */
@Composable
private fun CategoryRow(
    row: CategoryRowUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
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

    val swatch = Color(row.colorArgb)

    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (row.subcategoryCount == 0) {
                        stringResource(R.string.category_no_subcategories)
                    } else {
                        pluralStringResource(
                            R.plurals.category_subcategory_count,
                            row.subcategoryCount,
                            row.subcategoryCount,
                        )
                    },
                )
                if (row.sharedAcrossTypes) {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(stringResource(R.string.scope_both)) },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (row.archived) swatch.copy(alpha = 0.35f) else swatch),
            ) {
                Icon(
                    imageVector = CategoryIcons[row.iconKey],
                    contentDescription = null,
                    tint = onColorFor(swatch),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(
                            R.string.category_actions_for,
                            row.name,
                        ),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
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
                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
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

@Composable
private fun RemovalDialog(
    pending: PendingRemoval,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (pending.canDelete) {
        ConfirmDialog(
            title = stringResource(R.string.category_delete_title, pending.name),
            message = stringResource(R.string.category_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    } else {
        ConfirmDialog(
            title = stringResource(R.string.category_archive_title, pending.name),
            message = stringResource(
                R.string.category_archive_message,
                pending.references.describe(),
            ),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = false,
        )
    }
}

// -- Previews ---------------------------------------------------------------

private val previewState = CategoryListUiState(
    isLoading = false,
    scope = CategoryScope.EXPENSE,
    active = listOf(
        CategoryRowUi("1", "Food & Drink", "restaurant", 0xFFEF6C00.toInt(), 5, false, false),
        CategoryRowUi("2", "Transport", "car", 0xFF1565C0.toInt(), 5, false, false),
        CategoryRowUi("3", "Gifts", "gift", 0xFF2E7D32.toInt(), 0, false, true),
    ),
    archived = listOf(
        CategoryRowUi("4", "Old Rent", "home", 0xFF6D4C41.toInt(), 0, true, false),
    ),
)

@Preview(name = "Categories — light", showBackground = true)
@Composable
private fun CategoryListPreviewLight() {
    PaisaTheme {
        CategoryListScreen(previewState, {}, {}, {}, {})
    }
}

@Preview(name = "Categories — dark", showBackground = true, uiMode = 32)
@Composable
private fun CategoryListPreviewDark() {
    PaisaTheme {
        CategoryListScreen(previewState, {}, {}, {}, {})
    }
}

@Preview(name = "Categories — empty", showBackground = true)
@Composable
private fun CategoryListPreviewEmpty() {
    PaisaTheme {
        CategoryListScreen(CategoryListUiState(isLoading = false), {}, {}, {}, {})
    }
}

@Preview(name = "Categories — delete blocked by references", showBackground = true)
@Composable
private fun CategoryListPreviewBlocked() {
    PaisaTheme {
        CategoryListScreen(
            previewState.copy(
                pendingRemoval = PendingRemoval("1", "Food & Drink", ReferenceCount(12, 1)),
            ),
            {}, {}, {}, {},
        )
    }
}
