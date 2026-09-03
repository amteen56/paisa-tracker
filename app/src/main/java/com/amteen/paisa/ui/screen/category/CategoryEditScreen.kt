package com.amteen.paisa.ui.screen.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amteen.paisa.R
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.ui.components.ColorPicker
import com.amteen.paisa.ui.components.IconPicker
import com.amteen.paisa.ui.components.LoadingState
import com.amteen.paisa.ui.components.onColorFor
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.theme.PaisaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    state: CategoryEditUiState,
    onEvent: (CategoryEditEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(CategoryEditEvent.DismissError)
        }
    }
    LaunchedEffect(state.isLoading, state.isEditing) {
        // Only on Add: opening the keyboard on an edit hides the thing being edited.
        if (!state.isLoading && !state.isEditing) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) {
                                R.string.category_edit_title
                            } else {
                                R.string.category_new_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { onEvent(CategoryEditEvent.Save) },
                            enabled = state.canSave,
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(innerPadding).fillMaxSize())
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            CategoryPreviewHeader(
                name = state.name,
                iconKey = state.iconKey,
                colorArgb = state.colorArgb,
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(CategoryEditEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.category_name_label)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus),
            )

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.category_applies_to))
            ScopeSelector(
                scope = state.applicableTo,
                onSelect = { onEvent(CategoryEditEvent.ScopeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.category_colour))
            ColorPicker(
                selectedArgb = state.colorArgb,
                onSelect = { onEvent(CategoryEditEvent.ColorSelected(it)) },
            )

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.category_icon))
            IconPicker(
                selectedKey = state.iconKey,
                accentColor = Color(state.colorArgb),
                onSelect = { onEvent(CategoryEditEvent.IconSelected(it)) },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.category_subcategories))
            Text(
                text = stringResource(R.string.category_subcategories_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            state.subcategories.forEachIndexed { index, row ->
                SubcategoryField(
                    row = row,
                    position = index + 1,
                    onChange = { onEvent(CategoryEditEvent.SubcategoryChanged(index, it)) },
                    onRemove = { onEvent(CategoryEditEvent.SubcategoryRemoved(index)) },
                )
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = { onEvent(CategoryEditEvent.SubcategoryAdded) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.category_add_subcategory))
            }

            if (state.archivedSubcategories.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.category_archived_subcategories))
                Text(
                    text = stringResource(R.string.category_archived_subcategories_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                state.archivedSubcategories.forEach { archived ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = archived.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onEvent(CategoryEditEvent.SubcategoryRestored(archived.id))
                            },
                        ) {
                            Icon(
                                Icons.Filled.Unarchive,
                                contentDescription = stringResource(
                                    R.string.category_restore_subcategory,
                                    archived.name,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CategoryPreviewHeader(name: String, iconKey: String, colorArgb: Int) {
    val swatch = Color(colorArgb)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(swatch),
        ) {
            Icon(
                imageVector = CategoryIcons[iconKey],
                contentDescription = null,
                tint = onColorFor(swatch),
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = name.ifBlank { stringResource(R.string.category_name_placeholder) },
            style = MaterialTheme.typography.titleLarge,
            color = if (name.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSelector(
    scope: CategoryScope,
    onSelect: (CategoryScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(CategoryScope.EXPENSE, CategoryScope.INCOME, CategoryScope.BOTH)
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = scope == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    when (option) {
                        CategoryScope.EXPENSE -> stringResource(R.string.scope_expense)
                        CategoryScope.INCOME -> stringResource(R.string.scope_income)
                        CategoryScope.BOTH -> stringResource(R.string.scope_both)
                    },
                )
            }
        }
    }
}

@Composable
private fun SubcategoryField(
    row: SubcategoryRowUi,
    position: Int,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = row.name,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.category_subcategory_n, position)) },
            singleLine = true,
            isError = row.error != null,
            supportingText = row.error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(
                    R.string.category_remove_subcategory,
                    row.name.ifBlank { position.toString() },
                ),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

// -- Previews ---------------------------------------------------------------

private fun previewEditState() = CategoryEditUiState(
    isLoading = false,
    isEditing = true,
    name = "Food & Drink",
    applicableTo = CategoryScope.EXPENSE,
    iconKey = "restaurant",
    colorArgb = 0xFFEF6C00.toInt(),
    subcategories = listOf(
        SubcategoryRowUi("a", "Groceries"),
        SubcategoryRowUi("b", "Restaurants"),
        SubcategoryRowUi("c", "Fast Food"),
    ),
    archivedSubcategories = listOf(ArchivedSubcategoryUi("d", "Office Canteen")),
)

@Preview(name = "Edit category — light", showBackground = true)
@Composable
private fun CategoryEditPreviewLight() {
    PaisaTheme {
        CategoryEditScreen(previewEditState(), {}, {})
    }
}

@Preview(name = "Edit category — dark", showBackground = true, uiMode = 32)
@Composable
private fun CategoryEditPreviewDark() {
    PaisaTheme {
        CategoryEditScreen(previewEditState(), {}, {})
    }
}

@Preview(name = "New category", showBackground = true)
@Composable
private fun CategoryEditPreviewNew() {
    PaisaTheme {
        CategoryEditScreen(CategoryEditUiState(isLoading = false), {}, {})
    }
}
