package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.AppSnapshot
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CsvRow
import com.amteen.paisa.domain.model.ImportPlaceholders
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.repository.BackupRepository
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** How an import should treat the data already on the device. */
enum class ImportMode {
    /**
     * Keep what is there and add what is new. A record whose id already exists is
     * skipped, so importing the same file twice is a no-op rather than a doubling.
     */
    MERGE,

    /** Wipe and replace. Snapshots to `backup/` first — CLAUDE.md rule 8. */
    REPLACE,
}

/**
 * What an import *would* do, worked out before anything is written.
 *
 * This is the preview step: the complete candidate state is built and validated in
 * memory, and the user sees these counts before confirming. Committing then writes
 * exactly this candidate — it cannot discover a problem halfway through and leave the
 * app holding a mixture of two datasets. See CLAUDE.md rule 8.
 */
data class ImportPreview(
    val mode: ImportMode,
    val source: ImportSource,
    val exportedAt: Instant?,

    val incomingTransactions: Int,
    val duplicateTransactions: Int,
    val incomingCategories: Int,
    val incomingPaymentMethods: Int,
    val incomingBudgets: Int,

    /** Records the file held but this build could not read. Reported, then dropped. */
    val unreadable: List<String>,

    /** Categories invented so no transaction is left pointing at nothing. */
    val repairedReferences: Int,

    /** What a Replace would delete. Zero for a merge. */
    val replacedTransactions: Int,

    /**
     * The validated candidate state, ready to commit without re-parsing.
     *
     * The UI has no business reading this — it renders the counts above and passes
     * the whole preview back to [CommitImportUseCase] unchanged. It is public only
     * because the preview itself is.
     */
    val candidate: Candidate,
) {
    val hasAnythingToDo: Boolean
        get() = incomingTransactions > 0 || incomingCategories > 0 ||
            incomingPaymentMethods > 0 || incomingBudgets > 0

    /** Drives the confirmation wording: a Replace destroys, a merge does not. */
    val isDestructive: Boolean get() = mode == ImportMode.REPLACE

    data class Candidate(
        val settings: AppSettings?,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
        val budgets: List<Budget>,
        val transactions: List<Transaction>,
    )
}

enum class ImportSource { JSON, CSV }

/** Column names for the CSV export, and what the importer looks for. */
object CsvColumns {
    const val DATE = "date"
    const val TIME = "time"
    const val TYPE = "type"
    const val AMOUNT_MINOR = "amountminor"
    const val CURRENCY = "currency"
    const val CATEGORY = "category"
    const val SUBCATEGORY = "subcategory"
    const val PAYMENT_METHOD = "paymentmethod"
    const val DESCRIPTION = "description"
    const val NOTES = "notes"
    const val ID = "id"

    /** Written in this order; read by name, so a reordered file still imports. */
    val HEADER = listOf(
        DATE, TIME, TYPE, AMOUNT_MINOR, CURRENCY,
        CATEGORY, SUBCATEGORY, PAYMENT_METHOD, DESCRIPTION, NOTES, ID,
    )
}

/**
 * Everything the user owns, as one JSON document.
 *
 * Reads through the repositories rather than off the files, so a snapshot can never
 * catch a half-written shard.
 */
class ExportBackupUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
    private val now: () -> Instant = { Instant.now() },
) {
    suspend operator fun invoke(): AppResult<String> = try {
        AppResult.Ok(backups.encodeJson(snapshot()))
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not build the backup.", e))
    }

    /** Shared with the auto-snapshot path, so both write identical documents. */
    suspend fun snapshot(): AppSnapshot {
        settings.load()
        categories.load()
        paymentMethods.load()
        budgets.load()

        return AppSnapshot(
            schemaVersion = backups.schemaVersion,
            exportedAt = now(),
            settings = settings.settings.value,
            categories = categories.categories.value,
            paymentMethods = paymentMethods.paymentMethods.value,
            budgets = budgets.budgets.value,
            transactions = transactions.getAll(),
        )
    }
}

/**
 * Transactions as CSV, with references resolved to names.
 *
 * Names rather than ids, because a spreadsheet is for a person to read. That makes
 * CSV the lossy format — the JSON backup is the one that round-trips exactly — and
 * the importer matches those names back case-insensitively, creating what it cannot
 * find rather than dropping the row.
 *
 * Amounts are written as **minor units**, as an integer. A spreadsheet that helpfully
 * reformats `350.50` is exactly the drift the `Long` model exists to prevent.
 */
class ExportCsvUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val backups: BackupRepository,
) {
    suspend operator fun invoke(): AppResult<String> = try {
        categories.load()
        paymentMethods.load()

        val categoryById = categories.categories.value.associateBy { it.id }
        val methodById = paymentMethods.paymentMethods.value.associateBy { it.id }

        val rows = transactions.getAll()
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.time }.thenBy { it.id })
            .map { record ->
                val category = categoryById[record.categoryId]
                listOf(
                    record.date.toString(),
                    record.time.toString(),
                    record.type.name,
                    record.amountMinor.toString(),
                    record.currencyCode,
                    category?.name.orEmpty(),
                    category?.subcategory(record.subcategoryId)?.name.orEmpty(),
                    methodById[record.paymentMethodId]?.name.orEmpty(),
                    record.description,
                    record.notes.orEmpty(),
                    record.id,
                )
            }

        AppResult.Ok(backups.encodeCsv(CsvColumns.HEADER, rows))
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not build the CSV.", e))
    }
}

/**
 * Validates an incoming document and reports what it would do.
 *
 * **Nothing is written here.** This parses, assembles the complete candidate state,
 * repairs any reference that would not resolve, and hands back an [ImportPreview] the
 * caller shows and then commits unchanged.
 */
class PrepareImportUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
) {

    suspend fun fromJson(text: String, mode: ImportMode): AppResult<ImportPreview> {
        val snapshot = try {
            backups.decodeJson(text)
        } catch (e: Exception) {
            return AppResult.Err(
                AppError.Storage(
                    "This does not look like a Paisa backup. Check the file and try again.",
                    e,
                ),
            )
        }

        if (snapshot.schemaVersion > backups.schemaVersion) {
            // Refuse rather than guess. A newer file may carry fields this build
            // would silently drop, and dropping data during a Replace cannot be undone.
            // There is a purpose-built error for exactly this, and its message
            // already explains both versions.
            return AppResult.Err(
                AppError.SchemaTooNew(
                    fileVersion = snapshot.schemaVersion,
                    supportedVersion = backups.schemaVersion,
                ),
            )
        }

        return try {
            AppResult.Ok(buildFromSnapshot(snapshot, mode))
        } catch (e: Exception) {
            AppResult.Err(AppError.Storage("Could not read this backup.", e))
        }
    }

    suspend fun fromCsv(text: String, mode: ImportMode): AppResult<ImportPreview> = try {
        val rows = backups.decodeCsv(text)
        if (rows.isEmpty()) {
            AppResult.Err(AppError.Storage("That file has no rows in it."))
        } else if (rows.none { it[CsvColumns.DATE].isNotBlank() }) {
            AppResult.Err(
                AppError.Storage(
                    "That CSV has no readable 'date' column. Export one from Paisa to " +
                        "see the expected format.",
                ),
            )
        } else {
            AppResult.Ok(buildFromCsv(rows, mode))
        }
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not read that CSV.", e))
    }

    // -- JSON ---------------------------------------------------------------

    private suspend fun buildFromSnapshot(
        snapshot: AppSnapshot,
        mode: ImportMode,
    ): ImportPreview {
        loadAll()

        // The app is PKR-only. A backup from a multi-currency build carries other
        // codes, and keeping them would render amounts against a currency that no
        // longer exists — see CLAUDE.md, Single currency.
        val incoming = snapshot.transactions.map {
            it.copy(currencyCode = AppSettings.DEFAULT_BASE_CURRENCY)
        }

        val existingTransactions = transactions.getAll()
        val existingCategories = categories.categories.value
        val existingMethods = paymentMethods.paymentMethods.value
        val existingBudgets = budgets.budgets.value

        val knownTransactionIds = existingTransactions.map { it.id }.toSet()
        val newTransactions = incoming.filterNot { it.id in knownTransactionIds }
        val newCategories = snapshot.categories.filterNot { candidate ->
            existingCategories.any { it.id == candidate.id }
        }
        val newMethods = snapshot.paymentMethods.filterNot { candidate ->
            existingMethods.any { it.id == candidate.id }
        }
        val newBudgets = snapshot.budgets.filterNot { candidate ->
            existingBudgets.any { it.id == candidate.id }
        }

        val replace = mode == ImportMode.REPLACE
        val candidateCategories = if (replace) {
            snapshot.categories
        } else {
            // Incoming loses to what is already here: an id collision on a merge
            // means the user already has that record, and overwriting it would make
            // "add what is new" quietly destructive.
            existingCategories + newCategories
        }
        val candidateTransactions = if (replace) incoming else existingTransactions + newTransactions

        val repairs = missingCategories(candidateTransactions, candidateCategories)

        val unreadable = buildList {
            if (snapshot.unreadableTransactions > 0) {
                add("${snapshot.unreadableTransactions} transaction(s) could not be read.")
            }
            if (snapshot.unreadableCategories > 0) {
                add("${snapshot.unreadableCategories} category/categories could not be read.")
            }
        }

        return ImportPreview(
            mode = mode,
            source = ImportSource.JSON,
            exportedAt = snapshot.exportedAt,
            incomingTransactions = if (replace) incoming.size else newTransactions.size,
            duplicateTransactions = if (replace) 0 else incoming.size - newTransactions.size,
            incomingCategories = if (replace) snapshot.categories.size else newCategories.size,
            incomingPaymentMethods = if (replace) snapshot.paymentMethods.size else newMethods.size,
            incomingBudgets = if (replace) snapshot.budgets.size else newBudgets.size,
            unreadable = unreadable,
            repairedReferences = repairs.size,
            replacedTransactions = if (replace) existingTransactions.size else 0,
            candidate = ImportPreview.Candidate(
                settings = snapshot.settings?.copy(
                    // Never adopt another install's base currency.
                    baseCurrencyCode = AppSettings.DEFAULT_BASE_CURRENCY,
                    initialized = true,
                ),
                categories = candidateCategories + repairs,
                paymentMethods = if (replace) snapshot.paymentMethods else existingMethods + newMethods,
                budgets = if (replace) snapshot.budgets else existingBudgets + newBudgets,
                transactions = candidateTransactions,
            ),
        )
    }

    // -- CSV ----------------------------------------------------------------

    private suspend fun buildFromCsv(
        rows: List<CsvRow>,
        mode: ImportMode,
    ): ImportPreview {
        loadAll()

        val existingTransactions = transactions.getAll()
        val candidateCategories = categories.categories.value.toMutableList()
        val candidateMethods = paymentMethods.paymentMethods.value.toMutableList()

        // Matched on name, case-insensitively: a CSV carries names, not ids.
        val categoryByName = candidateCategories.associateByTo(HashMap()) { it.name.lowercase() }
        val methodByName = candidateMethods.associateByTo(HashMap()) { it.name.lowercase() }

        val seenIds = existingTransactions.map { it.id }.toHashSet()
        val incoming = ArrayList<Transaction>()
        val unreadable = ArrayList<String>()
        var duplicates = 0
        var createdCategories = 0
        var createdMethods = 0

        rows.forEachIndexed { offset, row ->
            // +2: one for the header, one because humans count from one.
            val line = offset + 2

            val date = runCatching { LocalDate.parse(row[CsvColumns.DATE]) }.getOrNull()
            if (date == null) {
                unreadable += "Line $line: unreadable date."
                return@forEachIndexed
            }

            val amount = row[CsvColumns.AMOUNT_MINOR].toLongOrNull()
            if (amount == null || amount <= 0L) {
                unreadable += "Line $line: amount must be a whole number above zero."
                return@forEachIndexed
            }

            // A file exported by Paisa carries ids, so re-importing it is a no-op.
            // A hand-made file has none, and gets a stable id derived from its
            // content so importing it twice still does not duplicate.
            val id = row[CsvColumns.ID].ifBlank {
                "csv-${date}-$amount-${row[CsvColumns.DESCRIPTION].lowercase().hashCode()}"
            }
            if (id in seenIds) {
                duplicates++
                return@forEachIndexed
            }

            val categoryName = row[CsvColumns.CATEGORY].ifBlank { "Imported" }
            val category = categoryByName.getOrPut(categoryName.lowercase()) {
                createdCategories++
                ImportPlaceholders.category(categoryName).also { candidateCategories += it }
            }

            val methodName = row[CsvColumns.PAYMENT_METHOD]
            val method = if (methodName.isBlank()) {
                null
            } else {
                methodByName.getOrPut(methodName.lowercase()) {
                    createdMethods++
                    ImportPlaceholders.paymentMethod(methodName).also { candidateMethods += it }
                }
            }

            val subcategoryName = row[CsvColumns.SUBCATEGORY]
            incoming += Transaction(
                id = id,
                type = if (row[CsvColumns.TYPE].equals("INCOME", ignoreCase = true)) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                },
                amountMinor = amount,
                // Whatever the file says, amounts are rupees here.
                currencyCode = AppSettings.DEFAULT_BASE_CURRENCY,
                categoryId = category.id,
                subcategoryId = if (subcategoryName.isBlank()) {
                    null
                } else {
                    category.subcategories
                        .firstOrNull { it.name.equals(subcategoryName, ignoreCase = true) }?.id
                },
                description = row[CsvColumns.DESCRIPTION],
                date = date,
                // A missing time is midday rather than midnight, so a date-only row
                // does not sort ahead of everything genuinely recorded that morning.
                time = runCatching { LocalTime.parse(row[CsvColumns.TIME]) }.getOrNull()
                    ?: LocalTime.NOON,
                paymentMethodId = method?.id,
                notes = row[CsvColumns.NOTES].ifBlank { null },
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            seenIds += id
        }

        val replace = mode == ImportMode.REPLACE

        return ImportPreview(
            mode = mode,
            source = ImportSource.CSV,
            exportedAt = null,
            incomingTransactions = incoming.size,
            duplicateTransactions = duplicates,
            incomingCategories = createdCategories,
            incomingPaymentMethods = createdMethods,
            incomingBudgets = 0,
            unreadable = unreadable,
            repairedReferences = 0,
            replacedTransactions = if (replace) existingTransactions.size else 0,
            candidate = ImportPreview.Candidate(
                // A CSV carries no settings and no budgets, so a Replace driven by
                // one must not wipe either.
                settings = null,
                categories = candidateCategories,
                paymentMethods = candidateMethods,
                budgets = budgets.budgets.value,
                transactions = if (replace) incoming else existingTransactions + incoming,
            ),
        )
    }

    /**
     * Categories a transaction names but the candidate state does not contain.
     *
     * A transaction must never end up pointing at a `categoryId` that no longer
     * resolves — CLAUDE.md rule 4 — so a backup missing its own categories is
     * repaired with placeholders rather than having the money thrown away.
     */
    private fun missingCategories(
        candidateTransactions: List<Transaction>,
        candidateCategories: List<Category>,
    ): List<Category> {
        val known = candidateCategories.map { it.id }.toSet()
        return candidateTransactions.map { it.categoryId }
            .filterNot { it in known }
            .distinct()
            .map { ImportPlaceholders.recoveredCategory(it) }
    }

    private suspend fun loadAll() {
        settings.load()
        categories.load()
        paymentMethods.load()
        budgets.load()
    }
}

/**
 * Writes a validated [ImportPreview].
 *
 * A Replace snapshots to `backup/` first, and **aborts if the snapshot fails**:
 * replacing everything with no way back is worse than not importing at all.
 */
class CommitImportUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
    private val exportBackup: ExportBackupUseCase,
) {
    suspend operator fun invoke(preview: ImportPreview): AppResult<Unit> = try {
        if (preview.isDestructive) {
            val snapshot = exportBackup.snapshot()
            backups.writeLocalBackup(
                content = backups.encodeJson(snapshot),
                reason = "before-import",
                keep = settings.settings.value.backupsToKeep,
            )
        }

        val candidate = preview.candidate

        // Reference data first, so no transaction is ever briefly pointing at a
        // category that has not been written yet.
        categories.replaceAll(candidate.categories)
        paymentMethods.replaceAll(candidate.paymentMethods)
        budgets.replaceAll(candidate.budgets)
        transactions.replaceAll(candidate.transactions)
        candidate.settings?.let { imported -> settings.update { imported } }

        AppResult.Ok(Unit)
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("The import could not be completed.", e))
    }
}

/**
 * Takes a rolling local snapshot.
 *
 * Called on demand from the backup screen and before any Replace import. Not on a
 * timer: a snapshot is only worth taking when something is about to change, and a
 * scheduler would be a dependency and a wakeup budget spent to learn nothing.
 */
class WriteLocalBackupUseCase(
    private val exportBackup: ExportBackupUseCase,
    private val backups: BackupRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(reason: String = "manual"): AppResult<String> = try {
        val document = backups.encodeJson(exportBackup.snapshot())
        AppResult.Ok(
            backups.writeLocalBackup(
                content = document,
                reason = reason,
                keep = settings.settings.value.backupsToKeep,
            ),
        )
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not write the local backup.", e))
    }
}
