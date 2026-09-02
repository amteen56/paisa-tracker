package com.amteen.paisa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Material 3 type scale, tightened slightly for a data-dense finance UI.
 */
val PaisaTypography = Typography().let { d ->
    d.copy(
        displaySmall = d.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = d.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = d.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = d.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = d.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * Styles for rendering money.
 *
 * Amounts use tabular figures so digits occupy a constant width — without this,
 * a column of amounts in a list visibly jitters as values change, and decimal
 * points fail to line up.
 */
object AmountTextStyles {
    private val tabular = TextStyle(
        fontFamily = FontFamily.Default,
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.End,
    )

    /** The single large figure on the dashboard balance card. */
    val Hero = tabular.copy(
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )

    /** Section totals: income/expense pair, budget limits, report headers. */
    val Large = tabular.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Amount on a transaction row. */
    val Row = tabular.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Supporting figures: chart labels, calendar day totals, captions. */
    val Small = tabular.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )
}
