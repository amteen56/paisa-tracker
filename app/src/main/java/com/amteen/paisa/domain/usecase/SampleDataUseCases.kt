package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.data.seed.SampleData
import com.amteen.paisa.domain.repository.BudgetRepository
import com.amteen.paisa.domain.repository.TransactionRepository

/**
 * Fills the app with a plausible few months of history.
 *
 * Merges rather than replaces: someone trying the app out may already have entered
 * something real, and wiping it to make room for fake data would be indefensible.
 * Sample ids are prefixed, which is what makes [ClearSampleDataUseCase] able to take
 * exactly its own records back out again.
 */
class SeedSampleDataUseCase(
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
) {
    suspend operator fun invoke(months: Int = DEFAULT_MONTHS): AppResult<Int> = try {
        val generated = SampleData.transactions(months = months)
        val existing = transactions.getAll().map { it.id }.toSet()
        val fresh = generated.filterNot { it.id in existing }

        // One grouped write per affected shard rather than one per record.
        transactions.saveAll(fresh)
        SampleData.budgets().forEach { budget ->
            if (budgets.getById(budget.id) == null) budgets.upsert(budget)
        }

        AppResult.Ok(fresh.size)
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not add the sample data.", e))
    }

    companion object {
        const val DEFAULT_MONTHS = 6
    }
}

/**
 * Removes only what [SeedSampleDataUseCase] added.
 *
 * Matched on the `sample-` id prefix, so a user who tried the sample data and then
 * recorded real expenses keeps theirs. Anything else would make "add sample data" a
 * one-way door.
 */
class ClearSampleDataUseCase(
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
) {
    suspend operator fun invoke(): AppResult<Int> = try {
        val all = transactions.getAll()
        val mine = all.filter { it.id.startsWith(SampleData.ID_PREFIX) }

        // One whole-store write rather than a delete per record: deleting several
        // hundred one at a time would rewrite the same shards over and over.
        transactions.replaceAll(all - mine.toSet())
        budgets.budgets.value
            .filter { it.id.startsWith(SampleData.ID_PREFIX) }
            .forEach { budgets.hardDelete(it.id) }

        AppResult.Ok(mine.size)
    } catch (e: Exception) {
        AppResult.Err(AppError.Storage("Could not remove the sample data.", e))
    }
}
