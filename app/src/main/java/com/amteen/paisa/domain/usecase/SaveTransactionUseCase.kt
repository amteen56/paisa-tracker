package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * What the add/edit form produces. [id] null means insert.
 *
 * Kept separate from [Transaction] because a form in progress is not a valid
 * transaction — it has no id, no timestamps, and its amount may not yet parse.
 */
data class TransactionInput(
    val id: String? = null,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: String,
    val subcategoryId: String? = null,
    val description: String = "",
    val date: LocalDate,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val paymentMethodId: String? = null,
    val notes: String? = null,
)

/**
 * Validates and persists a transaction.
 *
 * Validation returns [AppError.Validation] with the offending field so the form can
 * highlight the right input, rather than throwing or showing a generic failure.
 *
 * The repository handles the shard bookkeeping, including moving a record between
 * months when the date changes — see [TransactionRepository.save].
 */
class SaveTransactionUseCase(
    private val transactions: TransactionRepository,
    private val now: () -> Instant = { Instant.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend operator fun invoke(input: TransactionInput): AppResult<Transaction> {
        validate(input)?.let { return AppResult.Err(it) }

        val timestamp = now()
        val existing = input.id?.let { transactions.getById(it) }

        val transaction = Transaction(
            id = existing?.id ?: input.id ?: newId(),
            type = input.type,
            amountMinor = input.amountMinor,
            currencyCode = input.currencyCode,
            categoryId = input.categoryId,
            subcategoryId = input.subcategoryId,
            description = input.description.trim(),
            date = input.date,
            time = input.time,
            paymentMethodId = input.paymentMethodId,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            // Preserve the original creation instant across an edit; only
            // updatedAt moves, so "added on" stays truthful.
            createdAt = existing?.createdAt ?: timestamp,
            updatedAt = timestamp,
        )

        return try {
            transactions.save(transaction)
            AppResult.Ok(transaction)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage(
                    message = "Could not save the transaction. Your existing data is unchanged.",
                    cause = e,
                ),
            )
        }
    }

    private fun validate(input: TransactionInput): AppError.Validation? {
        if (input.amountMinor <= 0L) {
            return AppError.Validation(FIELD_AMOUNT, "Enter an amount greater than zero.")
        }
        if (input.currencyCode.isBlank()) {
            return AppError.Validation(FIELD_CURRENCY, "Choose a currency.")
        }
        if (input.categoryId.isBlank()) {
            return AppError.Validation(FIELD_CATEGORY, "Choose a category.")
        }
        if (input.description.length > MAX_DESCRIPTION) {
            return AppError.Validation(
                FIELD_DESCRIPTION,
                "Keep the description under $MAX_DESCRIPTION characters.",
            )
        }
        if ((input.notes?.length ?: 0) > MAX_NOTES) {
            return AppError.Validation(FIELD_NOTES, "Keep notes under $MAX_NOTES characters.")
        }

        // A date decades out is almost always a typo or a bad import, and it would
        // create a shard that every "all time" report then has to span.
        val currentYear = today().year
        if (input.date.year < MIN_YEAR || input.date.year > currentYear + MAX_YEARS_AHEAD) {
            return AppError.Validation(
                FIELD_DATE,
                "Pick a date between $MIN_YEAR and ${currentYear + MAX_YEARS_AHEAD}.",
            )
        }

        return null
    }

    companion object {
        const val FIELD_AMOUNT = "amount"
        const val FIELD_CURRENCY = "currency"
        const val FIELD_CATEGORY = "category"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_NOTES = "notes"
        const val FIELD_DATE = "date"

        const val MAX_DESCRIPTION = 120
        const val MAX_NOTES = 1000
        const val MIN_YEAR = 1970
        const val MAX_YEARS_AHEAD = 10
    }
}
