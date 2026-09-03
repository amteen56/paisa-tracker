package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.repository.PaymentMethodRepository
import com.amteen.paisa.domain.repository.SettingsRepository
import com.amteen.paisa.domain.repository.TransactionRepository
import java.util.UUID

/** What the payment-method editor produces. [id] null means insert. */
data class PaymentMethodInput(
    val id: String? = null,
    val name: String,
    val iconKey: String,
)

class SavePaymentMethodUseCase(
    private val paymentMethods: PaymentMethodRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend operator fun invoke(input: PaymentMethodInput): AppResult<PaymentMethod> {
        val name = input.name.trim()
        if (name.isEmpty()) {
            return AppResult.Err(AppError.Validation(FIELD_NAME, "Give the payment method a name."))
        }
        if (name.length > MAX_NAME) {
            return AppResult.Err(
                AppError.Validation(FIELD_NAME, "Keep the name under $MAX_NAME characters."),
            )
        }

        val clash = paymentMethods.paymentMethods.value
            .firstOrNull { it.id != input.id && it.name.equals(name, ignoreCase = true) }
        if (clash != null) {
            return AppResult.Err(
                AppError.Validation(
                    FIELD_NAME,
                    if (clash.archived) {
                        "“${clash.name}” already exists but is archived. " +
                            "Restore it instead of creating a duplicate."
                    } else {
                        "“${clash.name}” already exists."
                    },
                ),
            )
        }

        val existing = try {
            input.id?.let { paymentMethods.getById(it) }
        } catch (e: Exception) {
            return AppResult.Err(AppError.Storage("Could not read the payment method.", e))
        }

        val method = PaymentMethod(
            id = existing?.id ?: input.id ?: newId(),
            name = name,
            iconKey = input.iconKey.ifBlank { DEFAULT_ICON },
            sortOrder = existing?.sortOrder ?: 0,
            archived = existing?.archived ?: false,
        )

        return try {
            paymentMethods.upsert(method)
            AppResult.Ok(method)
        } catch (e: Exception) {
            AppResult.Err(
                AppError.Storage(
                    "Could not save the payment method. Your existing data is unchanged.",
                    e,
                ),
            )
        }
    }

    companion object {
        const val FIELD_NAME = "name"
        const val MAX_NAME = 40
        const val DEFAULT_ICON = "wallet"
    }
}

/**
 * Removes a payment method only at reference count zero.
 *
 * Budgets do not reference payment methods, so transactions are the only referrer —
 * but settings *does* name one as the default, and a default pointing at something
 * that no longer exists would silently pre-select nothing on the add screen. So the
 * default is cleared as part of the same operation rather than left dangling.
 */
class DeletePaymentMethodUseCase(
    private val paymentMethods: PaymentMethodRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(id: String): AppResult<RemovalOutcome> = try {
        val references = ReferenceCount(transactions = transactions.countByPaymentMethod(id))
        if (references.isReferenced) {
            AppResult.Ok(RemovalOutcome.Blocked(references))
        } else {
            paymentMethods.hardDelete(id)
            clearDefaultIfMatching(settings, id)
            AppResult.Ok(RemovalOutcome.Deleted)
        }
    } catch (e: Exception) {
        AppResult.Err(
            AppError.Storage("Could not delete the payment method. Nothing was changed.", e),
        )
    }
}

/**
 * Archives a payment method, clearing it as the default on the way out.
 *
 * An archived method is gone from the picker, so leaving it as the default would
 * pre-select an option the user cannot see or change from the add screen.
 */
class ArchivePaymentMethodUseCase(
    private val paymentMethods: PaymentMethodRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(id: String, archived: Boolean): AppResult<RemovalOutcome> = try {
        paymentMethods.archive(id, archived)
        if (archived) clearDefaultIfMatching(settings, id)
        AppResult.Ok(if (archived) RemovalOutcome.Archived else RemovalOutcome.Deleted)
    } catch (e: Exception) {
        AppResult.Err(
            AppError.Storage("Could not update the payment method. Nothing was changed.", e),
        )
    }
}

/**
 * Chooses which method the add screen pre-selects. Passing null clears it.
 *
 * This is the tap the 5-second entry target is buying: most people pay for most
 * things the same way, so the common case should need no interaction at all.
 */
class SetDefaultPaymentMethodUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(id: String?): AppResult<Unit> = try {
        settings.update { it.copy(defaultPaymentMethodId = id) }
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not save the default payment method.", e))
    }
}

class ReorderPaymentMethodsUseCase(private val paymentMethods: PaymentMethodRepository) {
    suspend operator fun invoke(orderedIds: List<String>): AppResult<Unit> = try {
        paymentMethods.reorder(orderedIds)
        AppResult.Success
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not save the new order.", e))
    }
}

private suspend fun clearDefaultIfMatching(settings: SettingsRepository, id: String) {
    if (settings.settings.value.defaultPaymentMethodId == id) {
        settings.update { it.copy(defaultPaymentMethodId = null) }
    }
}
