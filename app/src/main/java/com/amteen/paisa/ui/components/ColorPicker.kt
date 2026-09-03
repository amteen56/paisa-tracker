package com.amteen.paisa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.amteen.paisa.ui.theme.CategoryPalette

/**
 * Choose a category's colour from the fixed palette.
 *
 * The selected swatch carries a check mark as well as a ring, so the choice is
 * still visible to someone who cannot tell two swatches apart — which is exactly
 * the population a colour picker is most likely to fail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CategoryPalette.colors.forEach { argb ->
            val selected = argb == selectedArgb
            val color = Color(argb)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(argb) },
                    )
                    // On the target rather than the check mark: an *unselected*
                    // swatch has no check to label, and it still needs a name.
                    .semantics { contentDescription = CategoryPalette.nameFor(argb) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = onColorFor(color),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
