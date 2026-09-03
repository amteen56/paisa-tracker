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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.amteen.paisa.ui.icons.CategoryIcons
import com.amteen.paisa.ui.icons.IconChoice

/**
 * Choose the icon a category or payment method is drawn with.
 *
 * Selection is carried by a filled swatch *and* a heavier border, never by tint
 * alone — the unselected icons are already tinted, so a change of shade on its own
 * would not read as "chosen".
 *
 * Each option is a 48dp `selectable` with `Role.RadioButton`, so TalkBack announces
 * it as one of a set and the touch target clears the accessibility minimum.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selectedKey: String,
    accentColor: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    choices: List<IconChoice> = CategoryIcons.choices,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { choice ->
            val selected = choice.key == selectedKey
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) accentColor else Color.Transparent)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice.key) },
                    ),
            ) {
                Icon(
                    imageVector = choice.image,
                    contentDescription = choice.label,
                    tint = if (selected) {
                        onColorFor(accentColor)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Black or white, whichever keeps a glyph readable on [background]. */
internal fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.45f) Color.Black else Color.White
