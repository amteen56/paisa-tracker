package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.BudgetsFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.StateFlow

class FileBudgetRepositoryImpl(store: JsonFileStore) : BudgetRepository {

    private val backing = FileBackedCollection(
        store = store,
        path = FilePaths.BUDGETS,
        serializer = BudgetsFile.serializer(),
        extract = { file -> file.budgets.mapNotNull { it.toDomain() } },
        wrap = { list -> BudgetsFile(budgets = list.map { it.toDto() }) },
        // No default budgets: a limit the user did not choose is worse than none,
        // because it fires alerts about a number that means nothing to them.
        seed = { emptyList() },
        // The user's own order, lowest first. Ties fall back to the id so the list
        // is stable rather than reshuffling between reads — every budget written
        // before this field existed parses with sortOrder 0.
        sort = { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) },
    )

    override val budgets: StateFlow<List<Budget>> = backing.items

    override suspend fun load() = backing.load()

    override suspend fun getById(id: String): Budget? {
        backing.ensureLoaded()
        return backing.current().firstOrNull { it.id == id }
    }

    override suspend fun upsert(budget: Budget) = backing.mutate { current ->
        if (current.any { it.id == budget.id }) {
            current.map { if (it.id == budget.id) budget else it }
        } else {
            // A new budget goes to the end rather than jumping to the top of an
            // order the user arranged deliberately.
            current + budget.copy(
                sortOrder = if (budget.sortOrder == 0) {
                    (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
                } else {
                    budget.sortOrder
                },
            )
        }
    }

    override suspend fun archive(id: String, archived: Boolean) = backing.mutate { current ->
        current.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    /**
     * Budgets carry no history of their own — usage is always derived from
     * transactions — so deleting one loses nothing and needs no reference check.
     */
    override suspend fun hardDelete(id: String) = backing.mutate { current ->
        current.filterNot { it.id == id }
    }

    override suspend fun reorder(orderedIds: List<String>) = backing.mutate { current ->
        val position = orderedIds.withIndex().associate { (index, id) -> id to index }
        // A budget missing from the list keeps the order it had, so reordering the
        // live section cannot disturb the archived one.
        current.map { it.copy(sortOrder = position[it.id] ?: it.sortOrder) }
    }

    override suspend fun replaceAll(budgets: List<Budget>) = backing.replaceAll(budgets)
}
