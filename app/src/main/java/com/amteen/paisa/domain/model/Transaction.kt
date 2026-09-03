package com.amteen.paisa.domain.model

import com.amteen.paisa.core.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class TransactionType {
    EXPENSE,
    INCOME;

    val isExpense: Boolean get() = this == EXPENSE
    val isIncome: Boolean get() = this == INCOME
}

/**
 * A single recorded expense or income.
 *
 * [amountMinor] is always **positive**; direction comes from [type]. Storing a
 * signed amount would mean every aggregate has to agree on the sign convention,
 * and one disagreement silently inverts a total.
 *
 * [currencyCode] is captured at entry and never rewritten — if the user later
 * changes their base currency or edits a rate, historical transactions keep the
 * amount that was actually spent. See CLAUDE.md rule 5.
 */
data class Transaction(
    val id: String,
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
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val money: Money get() = Money(amountMinor, currencyCode)

    /** The storage shard this transaction belongs to. */
    val month: YearMonth get() = YearMonth.from(date)

    /** Signed value for balance math: income adds, expense subtracts. */
    val signedMinor: Long
        get() = if (type.isIncome) amountMinor else -amountMinor
}
