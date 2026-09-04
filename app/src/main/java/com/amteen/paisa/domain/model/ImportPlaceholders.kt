package com.amteen.paisa.domain.model

/**
 * Records invented during an import so nothing the file references goes missing.
 *
 * Domain rather than seed data: this is the policy "never drop a record because its
 * category is absent — invent the category" (CLAUDE.md rule 4), and the import use
 * case must be able to apply it without reaching into `data/`.
 */
object ImportPlaceholders {

    /**
     * A category for a name a CSV mentions but the app does not have.
     *
     * Creating it beats dropping the row: dropping would move the money to
     * "Uncategorised" and quietly change every report the user looks at next.
     */
    fun category(name: String) = Category(
        id = "cat-import-${slug(name)}",
        name = name,
        // BOTH, because a CSV row's type is per-row and a category invented for an
        // income row must not be barred from expense rows in the same file.
        applicableTo = CategoryScope.BOTH,
        iconKey = "category",
        colorArgb = NEUTRAL_GREY,
    )

    fun paymentMethod(name: String) = PaymentMethod(
        id = "pm-import-${slug(name)}",
        name = name,
        iconKey = "wallet",
    )

    /**
     * A stand-in for a category a backup's own transactions reference but the backup
     * does not contain.
     *
     * Keeps the id, so the transactions still resolve and the user can rename the
     * category afterwards rather than re-filing every record.
     */
    fun recoveredCategory(id: String) = Category(
        id = id,
        name = "Recovered",
        applicableTo = CategoryScope.BOTH,
        iconKey = "category",
        colorArgb = NEUTRAL_GREY,
    )

    /**
     * Stable, readable, and collision-resistant.
     *
     * The hash rides along because two different names must never collapse to one
     * id — "Food & Drink" and "Food / Drink" both slug to `food-drink`, and merging
     * them would silently combine two categories the user kept apart.
     */
    private fun slug(name: String): String {
        val cleaned = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .take(24)
        val hash = name.hashCode().toUInt().toString(16)
        return if (cleaned.isEmpty()) hash else "$cleaned-$hash"
    }

    /** Legible on both themes; the user can recolour it afterwards. */
    private const val NEUTRAL_GREY: Int = 0xFF8E8E93.toInt()
}
