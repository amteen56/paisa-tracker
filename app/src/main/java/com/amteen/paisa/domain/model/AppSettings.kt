package com.amteen.paisa.domain.model

import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Default sort order for the transaction history list. */
enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
}

/**
 * Everything in `settings.json`.
 *
 * Held as one immutable object so a settings change is a single atomic file write
 * rather than a scatter of independent keys that can end up half-applied.
 */
data class AppSettings(
    val baseCurrencyCode: String = DEFAULT_BASE_CURRENCY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val defaultSortOrder: SortOrder = SortOrder.DATE_DESC,
    val defaultPaymentMethodId: String? = null,
    /** Fire local notifications at the 75/90/100% budget thresholds. */
    val budgetAlertsEnabled: Boolean = true,
    /** Snapshot to `backup/` automatically; rolling, [backupsToKeep] retained. */
    val autoBackupEnabled: Boolean = true,
    val backupsToKeep: Int = 5,
    /** False until the seed data has been written, so first run happens once. */
    val initialized: Boolean = false,
) {
    companion object {
        const val DEFAULT_BASE_CURRENCY = "PKR"
    }
}
