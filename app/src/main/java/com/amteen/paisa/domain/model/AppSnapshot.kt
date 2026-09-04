package com.amteen.paisa.domain.model

import java.time.Instant

/**
 * Everything the user owns, as domain objects.
 *
 * The unit of a backup and of a restore. Deliberately free of serialization types:
 * the wire format is a DTO in `data/dto/`, and the mapping between the two is what
 * lets the JSON schema change without touching import logic. See CLAUDE.md.
 *
 * [settings] is nullable because a CSV carries none, and a Replace driven by a CSV
 * must not therefore reset the user's preferences.
 */
data class AppSnapshot(
    val schemaVersion: Int,
    val exportedAt: Instant?,
    val settings: AppSettings?,
    val categories: List<Category>,
    val paymentMethods: List<PaymentMethod>,
    val budgets: List<Budget>,
    val transactions: List<Transaction>,
    /**
     * Records the file contained but this build could not parse.
     *
     * Reported to the user rather than swallowed: "imported 40 of 43" is useful,
     * "imported 40" when the file held 43 is a silent loss.
     */
    val unreadableTransactions: Int = 0,
    val unreadableCategories: Int = 0,
)

/**
 * One CSV line, as strings, keyed by column name.
 *
 * Strings because a CSV is whatever the spreadsheet wrote — parsing and validating it
 * is the import use case's job, and doing it here would put the interesting logic
 * behind a file boundary where it is harder to test.
 */
data class CsvRow(val cells: Map<String, String>) {
    operator fun get(column: String): String = cells[column].orEmpty().trim()
}
