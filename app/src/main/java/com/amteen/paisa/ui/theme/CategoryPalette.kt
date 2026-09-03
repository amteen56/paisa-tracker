package com.amteen.paisa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colours a category may be given.
 *
 * A fixed palette rather than a free colour wheel, for two reasons: every swatch is
 * checked to stay legible as a filled circle against both the light and the dark
 * surface, and a chart of twenty user-picked colours only reads as data if the
 * colours are distinguishable from each other.
 *
 * These are stored as ARGB ints in the user's JSON, so **never reorder or repurpose
 * an entry** — a stored `0xFFEF6C00` must keep meaning the same orange forever. Add
 * to the end instead.
 */
object CategoryPalette {

    val colors: List<Int> = listOf(
        0xFFEF6C00.toInt(), // orange
        0xFFD84315.toInt(), // deep orange
        0xFFC62828.toInt(), // red
        0xFFAD1457.toInt(), // pink
        0xFFD81B60.toInt(), // rose
        0xFF7B1FA2.toInt(), // purple
        0xFF4527A0.toInt(), // deep purple
        0xFF283593.toInt(), // indigo
        0xFF1565C0.toInt(), // blue
        0xFF0277BD.toInt(), // light blue
        0xFF00838F.toInt(), // cyan
        0xFF00695C.toInt(), // teal
        0xFF2E7D32.toInt(), // green
        0xFF1B5E20.toInt(), // dark green
        0xFF558B2F.toInt(), // light green
        0xFF33691E.toInt(), // olive
        0xFF9E7D0A.toInt(), // amber
        0xFF6D4C41.toInt(), // brown
        0xFF455A64.toInt(), // blue grey
        0xFF546E7A.toInt(), // slate
    )

    val default: Int = colors.first()

    /** Falls back to [default] so a colour from a newer build still renders. */
    fun colorFor(argb: Int): Color =
        Color(if (colors.contains(argb)) argb else argb.takeIf { it != 0 } ?: default)

    /** Human-readable names, used for the picker's content descriptions. */
    val names: List<String> = listOf(
        "Orange", "Deep orange", "Red", "Pink", "Rose",
        "Purple", "Deep purple", "Indigo", "Blue", "Light blue",
        "Cyan", "Teal", "Green", "Dark green", "Light green",
        "Olive", "Amber", "Brown", "Blue grey", "Slate",
    )

    fun nameFor(argb: Int): String {
        val index = colors.indexOf(argb)
        return if (index >= 0) names[index] else "Custom"
    }
}
