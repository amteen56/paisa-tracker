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
 * How [AppSettings.averageFilterCategoryIds] is read when working out the daily average.
 *
 * [EXCLUDE] is the default because the question people actually ask is "what do I spend
 * on a normal day?" — and the answer is spoiled by a handful of lumpy categories, not by
 * the dozens of ordinary ones.
 */
enum class AverageFilterMode { EXCLUDE, INCLUDE }

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
    /** How [averageFilterCategoryIds] is applied. Means nothing while that list is empty. */
    val averageFilterMode: AverageFilterMode = AverageFilterMode.EXCLUDE,
    /**
     * Main categories the dashboard's daily average is narrowed by. Empty — the default —
     * means no filter, so the average is every expense, exactly as it was before.
     *
     * Ids of categories that no longer exist are harmless: they simply match nothing. They
     * are deliberately not pruned, so archiving a category and bringing it back does not
     * silently lose the user's choice.
     */
    val averageFilterCategoryIds: List<String> = emptyList(),
    /** False until the seed data has been written, so first run happens once. */
    val initialized: Boolean = false,
) {
    /**
     * Whether the daily average is narrowed at all.
     *
     * An empty selection is "no filter" in *both* modes. An `INCLUDE` list the user has
     * emptied would otherwise report Rs. 0.00, which reads as a bug rather than as a
     * filter they need to add something to.
     */
    val averageFilterActive: Boolean get() = averageFilterCategoryIds.isNotEmpty()

    companion object {
        const val DEFAULT_BASE_CURRENCY = "PKR"
    }
}
