package com.amteen.paisa.docs

import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileBackupRepositoryImpl
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.data.seed.DefaultData
import com.amteen.paisa.domain.usecase.CommitImportUseCase
import com.amteen.paisa.domain.usecase.ExportBackupUseCase
import com.amteen.paisa.domain.usecase.ImportMode
import com.amteen.paisa.domain.usecase.ImportPreview
import com.amteen.paisa.domain.usecase.PrepareImportUseCase
import com.amteen.paisa.testing.FakeBudgetRepository
import com.amteen.paisa.testing.FakeCategoryRepository
import com.amteen.paisa.testing.FakePaymentMethodRepository
import com.amteen.paisa.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The sample CSV shipped in `docs/sample-data/` must actually import.
 *
 * Documentation drifts silently. This is the one test whose job is to fail when the
 * documented format and the real importer stop agreeing — far more likely than either
 * changing on its own.
 *
 * Unit tests run with `app/` as the working directory, hence the `../`.
 */
class SampleCsvTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val csvFile = File("../docs/sample-data/transactions.csv")

    /** A whole import pipeline over a fresh temp store. */
    private class Harness(root: File) {
        private val store = JsonFileStore(root)
        val transactions = FileTransactionRepositoryImpl(store)
        private val backups = FileBackupRepositoryImpl(store)
        private val categories = FakeCategoryRepository(DefaultData.categories)
        private val methods = FakePaymentMethodRepository(DefaultData.paymentMethods)
        private val budgets = FakeBudgetRepository()
        private val settings = FakeSettingsRepository()

        val prepare = PrepareImportUseCase(
            transactions, categories, methods, budgets, settings, backups,
        )
        val commit = CommitImportUseCase(
            transactions, categories, methods, budgets, settings, backups,
            ExportBackupUseCase(transactions, categories, methods, budgets, settings, backups),
        )
    }

    private fun harness() = Harness(temp.newFolder())

    private suspend fun Harness.preview(text: String): ImportPreview {
        val result = prepare.fromCsv(text, ImportMode.MERGE)
        assertTrue("preview failed: $result", result is AppResult.Ok)
        return (result as AppResult.Ok).value
    }

    @Test
    fun `the shipped sample CSV exists where the docs say it does`() {
        assertTrue("missing: ${csvFile.absolutePath}", csvFile.isFile)
    }

    @Test
    fun `the shipped sample CSV imports with no unreadable rows`() = runTest {
        val harness = harness()
        val preview = harness.preview(csvFile.readText())

        // A line number showing up here means the docs and the parser disagree.
        assertEquals(preview.unreadable.joinToString(), 0, preview.unreadable.size)
        assertEquals(15, preview.incomingTransactions)

        // "Gardening" is deliberately not a default category, so the file exercises
        // the create-rather-than-drop rule the docs promise.
        assertEquals(1, preview.incomingCategories)

        harness.commit(preview)
        val saved = harness.transactions.getAll()
        assertEquals(15, saved.size)

        // The quoted comma and the embedded quotes survived the round trip.
        val burger = saved.single { it.description == "Burger" }
        assertEquals("with cheese, no onions", burger.notes)
        assertTrue(saved.any { it.description.contains('"') })

        // Whole paisa, both directions present, and everything in rupees.
        assertEquals(28_000L, burger.amountMinor)
        assertTrue(saved.any { it.type.isIncome })
        assertTrue(saved.any { it.type.isExpense })
        assertTrue(saved.all { it.currencyCode == "PKR" })
    }

    @Test
    fun `re-importing the shipped CSV adds nothing the second time`() = runTest {
        val harness = harness()
        val text = csvFile.readText()

        harness.commit(harness.preview(text))
        val afterFirst = harness.transactions.getAll().size

        val second = harness.preview(text)

        // The rows carry no ids, so this is the content-derived id doing its job —
        // exactly what the docs claim.
        assertEquals(0, second.incomingTransactions)
        assertEquals(afterFirst, second.duplicateTransactions)
    }
}
