package com.amteen.paisa.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 tonal palette.
 *
 * Primary is a deep green (money/ledger), secondary a muted sage, tertiary a warm
 * clay used sparingly for accents. Error doubles as the "over budget" colour.
 *
 * Amount semantics live in [ExpenseColors] rather than the [androidx.compose.material3.ColorScheme],
 * because income/expense are domain concepts, not Material roles.
 */

// --- Light -----------------------------------------------------------------
val PrimaryLight = Color(0xFF1F6E4E)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFA8F2C8)
val OnPrimaryContainerLight = Color(0xFF002114)

val SecondaryLight = Color(0xFF4C6358)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCEE9DA)
val OnSecondaryContainerLight = Color(0xFF092017)

val TertiaryLight = Color(0xFF7C5635)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDCC2)
val OnTertiaryContainerLight = Color(0xFF2E1500)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFCF8F8)
val OnBackgroundLight = Color(0xFF1B1C1B)
val SurfaceLight = Color(0xFFFCF8F8)
val OnSurfaceLight = Color(0xFF1B1C1B)
val SurfaceVariantLight = Color(0xFFDCE5DD)
val OnSurfaceVariantLight = Color(0xFF404943)
val SurfaceContainerLight = Color(0xFFF0EDEC)
val SurfaceContainerHighLight = Color(0xFFEAE7E7)
val OutlineLight = Color(0xFF707973)
val OutlineVariantLight = Color(0xFFC0C9C2)

// --- Dark ------------------------------------------------------------------
val PrimaryDark = Color(0xFF8CD5AD)
val OnPrimaryDark = Color(0xFF003825)
val PrimaryContainerDark = Color(0xFF005237)
val OnPrimaryContainerDark = Color(0xFFA8F2C8)

val SecondaryDark = Color(0xFFB2CCBF)
val OnSecondaryDark = Color(0xFF1E352B)
val SecondaryContainerDark = Color(0xFF344C41)
val OnSecondaryContainerDark = Color(0xFFCEE9DA)

val TertiaryDark = Color(0xFFEEBD93)
val OnTertiaryDark = Color(0xFF48290C)
val TertiaryContainerDark = Color(0xFF623F20)
val OnTertiaryContainerDark = Color(0xFFFFDCC2)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF14120F)
val OnBackgroundDark = Color(0xFFE3E3E0)
val SurfaceDark = Color(0xFF14120F)
val OnSurfaceDark = Color(0xFFE3E3E0)
val SurfaceVariantDark = Color(0xFF404943)
val OnSurfaceVariantDark = Color(0xFFC0C9C2)
val SurfaceContainerDark = Color(0xFF201F1D)
val SurfaceContainerHighDark = Color(0xFF2B2A27)
val OutlineDark = Color(0xFF8A938C)
val OutlineVariantDark = Color(0xFF404943)

/**
 * Domain colours that Material's roles do not cover.
 *
 * Income and expense must stay distinguishable for colour-blind users, so the UI
 * pairs these with a `+` / `-` sign and an icon — colour is never the only signal.
 */
data class ExpenseColors(
    val income: Color,
    val onIncomeContainer: Color,
    val incomeContainer: Color,
    val expense: Color,
    val onExpenseContainer: Color,
    val expenseContainer: Color,
    val budgetNormal: Color,
    val budgetWarning: Color,
    val budgetCritical: Color,
    val budgetExceeded: Color,
    /** Categorical series colours for charts, in draw order. */
    val chartSeries: List<Color>,
)

val LightExpenseColors = ExpenseColors(
    income = Color(0xFF1F6E4E),
    incomeContainer = Color(0xFFD3F3E2),
    onIncomeContainer = Color(0xFF00291A),
    expense = Color(0xFFB3261E),
    expenseContainer = Color(0xFFFCE0DE),
    onExpenseContainer = Color(0xFF410E0B),
    budgetNormal = Color(0xFF2E7D5B),
    budgetWarning = Color(0xFFB8860B),
    budgetCritical = Color(0xFFD97706),
    budgetExceeded = Color(0xFFBA1A1A),
    chartSeries = listOf(
        Color(0xFF2E7D5B),
        Color(0xFF3F6BB5),
        Color(0xFFC2703B),
        Color(0xFF7A5EA8),
        Color(0xFFB5476B),
        Color(0xFF4E8F8B),
        Color(0xFF8A7A2E),
        Color(0xFF6B6F76),
    ),
)

val DarkExpenseColors = ExpenseColors(
    income = Color(0xFF7FD6AA),
    incomeContainer = Color(0xFF10412D),
    onIncomeContainer = Color(0xFFCDF3DF),
    expense = Color(0xFFFFB4AB),
    expenseContainer = Color(0xFF5C1B18),
    onExpenseContainer = Color(0xFFFFDAD6),
    budgetNormal = Color(0xFF7FD6AA),
    budgetWarning = Color(0xFFE4C05C),
    budgetCritical = Color(0xFFF0A860),
    budgetExceeded = Color(0xFFFFB4AB),
    chartSeries = listOf(
        Color(0xFF7FD6AA),
        Color(0xFF8FB3F0),
        Color(0xFFF0A878),
        Color(0xFFBCA6E8),
        Color(0xFFF08FAF),
        Color(0xFF7FCFCA),
        Color(0xFFD4C168),
        Color(0xFFB0B5BD),
    ),
)
