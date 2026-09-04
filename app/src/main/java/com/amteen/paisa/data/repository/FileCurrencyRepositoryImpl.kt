package com.amteen.paisa.data.repository

import com.amteen.paisa.data.dto.CurrenciesFile
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.mapper.toDomain
import com.amteen.paisa.data.mapper.toDto
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * `currencies.json`, read-only, and always exactly PKR.
 *
 * **The normalisation in [extract] is not belt-and-braces — it is load-bearing.**
 * Builds before multi-currency was cut seeded eight currencies, and the seed only
 * runs when the file is *missing*, so every install created by one of those builds
 * still has all eight on disk. Without collapsing the list on read, those installs
 * would light up the currency pickers again the moment they upgraded, and the app
 * would not actually be PKR-only for the people already using it.
 *
 * A legacy entry's `rateToBase` is discarded along with it. Under one currency there
 * is nothing to convert, and keeping a rate table alive would be keeping the cut
 * feature alive. Amounts stored against a legacy code are read through
 * `CurrencyTable`'s fallback — see the note on the class.
 */
class FileCurrencyRepositoryImpl(store: JsonFileStore) : CurrencyRepository {

    private val backing = FileBackedCollection(
        store = store,
        path = FilePaths.CURRENCIES,
        serializer = CurrenciesFile.serializer(),
        // Whatever the file holds, the app has one currency. A file written by an
        // older build is read, ignored, and left alone rather than rewritten: it is
        // harmless, and rewriting user data on read is not something this app does.
        extract = { file ->
            file.currencies.mapNotNull { it.toDomain() }
                .firstOrNull { it.code == DefaultData.BASE_CURRENCY_CODE }
                ?.let { listOf(it.copy(archived = false, rateToBase = 1.0)) }
                ?: listOf(DefaultData.currency)
        },
        wrap = { list -> CurrenciesFile(currencies = list.map { it.toDto() }) },
        seed = { DefaultData.currencies },
    )

    override val currencies: StateFlow<List<Currency>> = backing.items

    override suspend fun load() = backing.load()
}
