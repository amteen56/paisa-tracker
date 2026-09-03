package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.CurrencyTable
import com.amteen.paisa.domain.model.TransactionDetails
import com.amteen.paisa.domain.repository.CategoryRepository
import com.amteen.paisa.domain.repository.CurrencyRepository
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository

/**
 * Loads one transaction with its references resolved, for the detail and edit
 * screens.
 *
 * Returns [AppError.NotFound] rather than null so a stale deep link or a back-stack
 * entry pointing at a since-deleted record produces a real message instead of a
 * blank screen.
 */
class GetTransactionDetailsUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(id: String): AppResult<TransactionDetails> {
        val record = transactions.getById(id)
            ?: return AppResult.Err(AppError.NotFound("That transaction"))

        categories.load()
        paymentMethods.load()
        currencies.load()
        settings.load()

        val table = CurrencyTable(currencies.currencies.value, settings.settings.value.baseCurrencyCode)
        val category = categories.categories.value.firstOrNull { it.id == record.categoryId }

        return AppResult.Ok(
            TransactionDetails(
                transaction = record,
                category = category,
                subcategory = category?.subcategory(record.subcategoryId),
                paymentMethod = paymentMethods.paymentMethods.value
                    .firstOrNull { it.id == record.paymentMethodId },
                currency = table.currency(record.currencyCode),
            ),
        )
    }
}

class DeleteTransactionUseCase(
    private val transactions: TransactionRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = try {
        transactions.delete(id)
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(
            AppError.Storage("Could not delete the transaction. Nothing was changed.", e),
        )
    }
}
