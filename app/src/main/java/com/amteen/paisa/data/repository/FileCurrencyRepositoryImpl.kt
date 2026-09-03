package com.amteen.paisa.data.repository

import com.amteen.paisa.core.money.CurrencyConverter
import com.amteen.paisa.data.dto.CurrenciesFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.StateFlow

class FileCurrencyRepositoryImpl(store: JsonFileStore) : CurrencyRepository {

    private val backing = FileBackedCollection(
        store = store,
        path = FilePaths.CURRENCIES,
        serializer = CurrenciesFile.serializer(),
        extract = { file -> file.currencies.mapNotNull { it.toDomain() } },
        wrap = { list -> CurrenciesFile(currencies = list.map { it.toDto() }) },
        seed = { DefaultData.currencies },
        // Base currency first, then alphabetically — the base is the one the user
        // reaches for most and the anchor every other rate is expressed against.
        sort = { list -> list.sortedWith(compareByDescending<Currency> { it.isBase }.thenBy { it.code }) },
    )

    override val currencies: StateFlow<List<Currency>> = backing.items

    override suspend fun load() = backing.load()

    override suspend fun getByCode(code: String): Currency? {
        backing.ensureLoaded()
        return backing.current().firstOrNull { it.code == code }
    }

    override suspend fun upsert(currency: Currency) = backing.mutate { current ->
        if (current.any { it.code == currency.code }) {
            current.map { if (it.code == currency.code) currency else it }
        } else {
            current + currency
        }
    }

    /**
     * Rebases the whole table so [code] becomes `1.0`, preserving every cross-rate.
     *
     * Stored transaction amounts are **not** touched — a purchase of $10 stays $10
     * forever. See CLAUDE.md rule 5.
     */
    override suspend fun setBaseCurrency(code: String) = backing.mutate { current ->
        CurrencyConverter.rebase(current, code)
    }

    override suspend fun archive(code: String, archived: Boolean) = backing.mutate { current ->
        current.map { if (it.code == code) it.copy(archived = archived) else it }
    }

    override suspend fun hardDelete(code: String) = backing.mutate { current ->
        current.filterNot { it.code == code }
    }

    override suspend fun replaceAll(currencies: List<Currency>) = backing.replaceAll(currencies)
}
