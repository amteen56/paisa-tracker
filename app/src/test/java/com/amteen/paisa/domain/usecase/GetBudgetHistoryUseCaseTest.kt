package com.amteen.paisa.domain.usecase

import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeBudgetRepository
import com.amteen.paisa.testing.FakeCurrencyRepository
import com.amteen.paisa.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * A budget's recent months.
 *
 * Reaches across several shards, which is why the real file-backed repository is
 * used here — a fake serving one list would hide a range that never spans them.
 */
class GetBudgetHistoryUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val today = LocalDate.of(2026, 9, 12)
    private val month = YearMonth.of(2026, 9)

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)

    private lateinit var transactions: FileTransactionRepositoryImpl
    private lateinit var budgets: FakeBudgetRepository

    @Before
    fun setUp() {
        transactions = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        budgets = FakeBudgetRepository()
    }

    private fun useCase() = GetBudgetHistoryUseCase(
        budgets = budgets,
        transactions = transactions,
        currencies = FakeCurrencyRepository(listOf(pkr)),
        settings = FakeSettingsRepository(AppSettings(baseCurrencyCode = "PKR")),
        today = { today },
    )

    private suspend fun spend(id: String, amountMinor: Long, date: LocalDate) =
        transactions.save(
            Transaction(
                id = id,
                type = TransactionType.EXPENSE,
                amountMinor = amountMinor,
                currencyCode = "PKR",
                categoryId = "cat-food",
                date = date,
                time = LocalTime.NOON,
            ),
        )

    @Test
    fun `a recurring budget reports the requested number of months, newest first`() = runTest {
        budgets.upsert(
            Budget("b1", "cat-food", limitMinor = 100_000, currencyCode = "PKR"),
        )
        spend("sep", 50_000, LocalDate.of(2026, 9, 3))
        spend("aug", 80_000, LocalDate.of(2026, 8, 3))
        spend("jul", 20_000, LocalDate.of(2026, 7, 3))

        val history = useCase()("b1", months = 3)

        assertEquals(
            listOf(month, month.minusMonths(1), month.minusMonths(2)),
            history.map { it.month },
        )
        assertEquals(listOf(50_000L, 80_000L, 20_000L), history.map { it.spentMinor })
    }

    @Test
    fun `a month with no spending is reported as zero rather than skipped`() = runTest {
        budgets.upsert(Budget("b1", "cat-food", limitMinor = 100_000, currencyCode = "PKR"))
        spend("sep", 50_000, LocalDate.of(2026, 9, 3))

        val history = useCase()("b1", months = 3)

        assertEquals(3, history.size)
        assertEquals(0L, history[1].spentMinor)
        assertEquals(0L, history[2].spentMinor)
    }

    @Test
    fun `a budget pinned to one month has exactly that month of history`() = runTest {
        budgets.upsert(
            Budget(
                id = "b1",
                categoryId = "cat-food",
                limitMinor = 100_000,
                currencyCode = "PKR",
                period = YearMonth.of(2026, 8),
            ),
        )
        spend("aug", 80_000, LocalDate.of(2026, 8, 3))
        spend("sep", 50_000, LocalDate.of(2026, 9, 3))

        val history = useCase()("b1")

        assertEquals(listOf(YearMonth.of(2026, 8)), history.map { it.month })
        assertEquals(80_000L, history.single().spentMinor)
    }

    @Test
    fun `an archived budget still reports its history`() = runTest {
        budgets.upsert(
            Budget(
                id = "b1",
                categoryId = "cat-food",
                limitMinor = 100_000,
                currencyCode = "PKR",
                archived = true,
            ),
        )
        spend("sep", 50_000, LocalDate.of(2026, 9, 3))

        // The dashboard hides archived budgets; looking one up deliberately must
        // still produce real figures, or the history screen is blank for exactly the
        // budgets a user goes back to review.
        assertEquals(50_000L, useCase()("b1").first().spentMinor)
    }

    @Test
    fun `a budget that no longer exists reports nothing rather than failing`() = runTest {
        assertTrue(useCase()("gone").isEmpty())
    }
}
