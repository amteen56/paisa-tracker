package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.BudgetAlertsFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.domain.model.BudgetAlert
import com.amteen.paisa.domain.repository.BudgetAlertStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

/**
 * The record of which budget alerts have been shown.
 *
 * Backed by its own small file rather than by [FileBackedCollection], because it is
 * a `Set` and its identity is the whole triple — there is no id to update in place
 * and no ordering to preserve.
 */
class FileBudgetAlertStateRepositoryImpl(
    private val store: JsonFileStore,
) : BudgetAlertStateRepository {

    private val _fired = MutableStateFlow<Set<BudgetAlert>>(emptySet())
    override val fired: StateFlow<Set<BudgetAlert>> = _fired.asStateFlow()

    /**
     * Guards the read-modify-write in [mutate]. `JsonFileStore` already serialises
     * writes per file, but that would not stop two concurrent alert checks from both
     * reading the same set and one overwriting the other's additions.
     */
    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    override suspend fun load() {
        if (loaded) return
        val read = store.readFile(FilePaths.BUDGET_ALERTS, BudgetAlertsFile.serializer()) {
            BudgetAlertsFile()
        }
        _fired.value = read.value.fired.mapNotNull { it.toDomain() }.toSet()
        loaded = true
        // Deliberately not written back on a missing file. An absent record means
        // "nothing has been announced yet", which is exactly the correct starting
        // state — there is nothing to materialise.
    }

    override suspend fun record(alerts: Collection<BudgetAlert>) {
        if (alerts.isEmpty()) return
        mutate { it + alerts }
    }

    override suspend fun forget(budgetId: String) {
        mutate { current -> current.filterNot { it.budgetId == budgetId }.toSet() }
    }

    override suspend fun pruneBefore(before: YearMonth) {
        mutate { current -> current.filterNot { it.period < before }.toSet() }
    }

    private suspend fun mutate(transform: (Set<BudgetAlert>) -> Set<BudgetAlert>) {
        load()
        mutex.withLock {
            val next = transform(_fired.value)
            if (next == _fired.value) return
            // Written before the in-memory state moves, as everywhere else: a failed
            // write must not leave the app believing it has announced something it
            // has not. See CLAUDE.md rule 1.
            store.writeFile(
                FilePaths.BUDGET_ALERTS,
                BudgetAlertsFile.serializer(),
                BudgetAlertsFile(fired = next.map { it.toDto() }),
            )
            _fired.value = next
        }
    }
}
