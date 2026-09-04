package com.amteen.paisa.data.repository

import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.seed.DefaultData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The app has exactly one currency, and that has to hold for installs that predate
 * the decision as well as fresh ones.
 *
 * This is the regression guard for a real upgrade hazard: builds before multi-currency
 * was cut seeded **eight** currencies, and the seed only runs when the file is
 * missing. An install from one of those builds still has all eight on disk, and
 * without normalising on read it would light the currency pickers straight back up.
 */
class FileCurrencyRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File

    private fun repository(): FileCurrencyRepositoryImpl {
        root = temp.newFolder()
        return FileCurrencyRepositoryImpl(JsonFileStore(root))
    }

    private fun writeCurrencyFile(json: String) {
        val file = File(root, FilePaths.CURRENCIES)
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    @Test
    fun `a fresh install seeds exactly one currency`() = runTest {
        val repository = repository()
        repository.load()

        val currencies = repository.currencies.value
        assertEquals(1, currencies.size)
        assertEquals("PKR", currencies.single().code)
        assertEquals("Rs.", currencies.single().symbol)
        assertEquals(2, currencies.single().decimalDigits)
    }

    @Test
    fun `a legacy multi-currency file collapses to PKR alone`() = runTest {
        val repository = repository()
        // Exactly what a pre-cut build wrote: PKR plus seven others with live rates.
        writeCurrencyFile(
            """
            {
              "schemaVersion": 1,
              "currencies": [
                {"code":"PKR","name":"Pakistani Rupee","symbol":"Rs.","decimalDigits":2,"rateToBase":1.0},
                {"code":"USD","name":"US Dollar","symbol":"$","decimalDigits":2,"rateToBase":280.0},
                {"code":"EUR","name":"Euro","symbol":"€","decimalDigits":2,"rateToBase":305.0},
                {"code":"GBP","name":"Pound Sterling","symbol":"£","decimalDigits":2,"rateToBase":355.0},
                {"code":"SAR","name":"Saudi Riyal","symbol":"SR","decimalDigits":2,"rateToBase":74.5},
                {"code":"AED","name":"UAE Dirham","symbol":"AED","decimalDigits":2,"rateToBase":76.0},
                {"code":"INR","name":"Indian Rupee","symbol":"₹","decimalDigits":2,"rateToBase":3.35},
                {"code":"JPY","name":"Japanese Yen","symbol":"¥","decimalDigits":0,"rateToBase":1.85}
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        // If this ever returns more than one, every currency picker in the app comes
        // back to life on upgraded installs.
        assertEquals(listOf("PKR"), repository.currencies.value.map { it.code })
    }

    @Test
    fun `a legacy file with PKR archived or off-rate is repaired`() = runTest {
        val repository = repository()
        // A pre-cut install could have archived PKR or rebased away from it, and
        // either would leave the one remaining currency unusable.
        writeCurrencyFile(
            """
            {
              "schemaVersion": 1,
              "currencies": [
                {"code":"USD","name":"US Dollar","symbol":"$","decimalDigits":2,"rateToBase":1.0},
                {"code":"PKR","name":"Pakistani Rupee","symbol":"Rs.","decimalDigits":2,"rateToBase":0.00357,"archived":true}
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        val pkr = repository.currencies.value.single()
        assertEquals("PKR", pkr.code)
        assertFalse(pkr.archived)
        // Rate 1.0, or every stored amount would be read through a stale rate.
        assertEquals(1.0, pkr.rateToBase, 0.0)
    }

    @Test
    fun `a file with no PKR at all falls back to the seed`() = runTest {
        val repository = repository()
        writeCurrencyFile(
            """
            {
              "schemaVersion": 1,
              "currencies": [
                {"code":"USD","name":"US Dollar","symbol":"$","decimalDigits":2,"rateToBase":1.0}
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        // Never an empty list: MoneyFormatter needs a symbol and decimalDigits, and
        // a screen with no currency at all cannot render an amount.
        assertEquals(DefaultData.currency, repository.currencies.value.single())
    }

    @Test
    fun `a corrupt file still yields a usable PKR`() = runTest {
        val repository = repository()
        writeCurrencyFile("{ this is not json")

        repository.load()

        // Reads recover rather than crash — CLAUDE.md rule 2.
        assertEquals("PKR", repository.currencies.value.single().code)
    }
}
