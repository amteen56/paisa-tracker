package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.LoansFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.repository.LoanRepository
import kotlinx.coroutines.flow.StateFlow

class FileLoanRepositoryImpl(store: JsonFileStore) : LoanRepository {

    private val backing = FileBackedCollection(
        store = store,
        path = FilePaths.LOANS,
        serializer = LoansFile.serializer(),
        extract = { file -> file.loans.mapNotNull { it.toDomain() } },
        wrap = { list -> LoansFile(loans = list.map { it.toDto() }) },
        // Nothing to seed: a made-up debt is not a helpful starting point.
        seed = { emptyList() },
        // Newest first, which is what someone opening the screen to check "did I lend
        // Ali money last week?" is looking for. Ties fall back to the id so the list
        // is stable between reads rather than reshuffling.
        sort = { list ->
            list.sortedWith(compareByDescending<Loan> { it.date }.thenBy { it.id })
        },
    )

    override val loans: StateFlow<List<Loan>> = backing.items

    override suspend fun load() = backing.load()

    override suspend fun getById(id: String): Loan? {
        backing.ensureLoaded()
        return backing.current().firstOrNull { it.id == id }
    }

    override suspend fun upsert(loan: Loan) = backing.mutate { current ->
        if (current.any { it.id == loan.id }) {
            current.map { if (it.id == loan.id) loan else it }
        } else {
            current + loan
        }
    }

    /**
     * Deletes a loan outright.
     *
     * Nothing references a loan — it is a standalone ledger, and no transaction, budget
     * or report points at one — so there is nothing to orphan and no reference count to
     * check. See CLAUDE.md rule 4, which lists the types that *are* reference-counted.
     */
    override suspend fun hardDelete(id: String) = backing.mutate { current ->
        current.filterNot { it.id == id }
    }

    override suspend fun replaceAll(loans: List<Loan>) = backing.replaceAll(loans)
}
