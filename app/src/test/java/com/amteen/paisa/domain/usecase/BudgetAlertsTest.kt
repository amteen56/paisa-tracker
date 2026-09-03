package com.amteen.paisa.domain.usecase

import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetAlert
import com.amteen.paisa.domain.model.BudgetAlertThresholds
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeBudgetAlertStateRepository
import com.amteen.paisa.testing.FakeBudgetRepository
import com.amteen.paisa.testing.FakeCategoryRepository
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
 * When a budget alert fires, and — more importantly — when it does not.
 *
 * "Once per threshold per period" is the whole point of this use case, and it is the
 * kind of rule that is quietly wrong for weeks: an off-by-one in the comparison
 * means either silence at 100% or a notification on every app start.
 */
class BudgetAlertsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val today = LocalDate.of(2026, 9, 12)
    private val month = YearMonth.of(2026, 9)

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    private val usd = Currency("USD", "US Dollar", "$", 2, 280.0)

    private val food = Category(
        id = "cat-food",
        name = "Food",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0,
        subcategories = listOf(Subcategory("sub-fast", "Fast Food")),
    )

    private lateinit var transactions: FileTransactionRepositoryImpl
    private lateinit var budgets: FakeBudgetRepository
    private lateinit var alertState: FakeBudgetAlertStateRepository
    private lateinit var settings: FakeSettingsRepository

    @Before
    fun setUp() {
        transactions = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        budgets = FakeBudgetRepository()
        alertState = FakeBudgetAlertStateRepository()
        settings = FakeSettingsRepository(AppSettings(baseCurrencyCode = "PKR"))
    }

    private fun evaluate() = EvaluateBudgetAlertsUseCase(
        budgets = budgets,
        transactions = transactions,
        categories = FakeCategoryRepository(listOf(food)),
        currencies = FakeCurrencyRepository(listOf(pkr, usd)),
        settings = settings,
        alertState = alertState,
        today = { today },
    )

    private suspend fun spend(
        id: String,
        amountMinor: Long,
        subcategoryId: String? = null,
        currencyCode: String = "PKR",
        date: LocalDate = today,
    ) = transactions.save(
        Transaction(
            id = id,
            type = TransactionType.EXPENSE,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            categoryId = "cat-food",
            subcategoryId = subcategoryId,
            date = date,
            time = LocalTime.NOON,
        ),
    )

    /** Rs. 1,000 limit, so every 100 minor units is 0.1%. */
    private suspend fun foodBudget(limitMinor: Long = 100_000, currencyCode: String = "PKR") =
        budgets.upsert(
            Budget(
                id = "b-food",
                categoryId = "cat-food",
                limitMinor = limitMinor,
                currencyCode = currencyCode,
            ),
        )

    // -- Thresholds ---------------------------------------------------------

    @Test
    fun `nothing fires below the first threshold`() = runTest {
        foodBudget()
        spend("a", 74_000)

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `the warning fires exactly at seventy five percent`() = runTest {
        foodBudget()
        spend("a", 75_000)

        val event = evaluate()().single()

        assertEquals(BudgetAlertThresholds.WARNING, event.newlyCrossed)
    }

    @Test
    fun `crossing three thresholds at once announces only the highest`() = runTest {
        foodBudget()
        spend("a", 120_000)

        val event = evaluate()().single()

        assertEquals(BudgetAlertThresholds.EXCEEDED, event.newlyCrossed)
        // ...but all three are recorded, so none of them can fire later.
        assertEquals(BudgetAlertThresholds.all, event.toRecord.map { it.threshold })
    }

    @Test
    fun `crossing the next threshold later fires again`() = runTest {
        foodBudget()
        spend("a", 80_000)

        val use = evaluate()
        use.markShown(use())

        spend("b", 15_000)

        val event = use().single()
        assertEquals(BudgetAlertThresholds.CRITICAL, event.newlyCrossed)
    }

    // -- Once per threshold per period --------------------------------------

    @Test
    fun `the same threshold does not fire twice`() = runTest {
        foodBudget()
        spend("a", 80_000)

        val use = evaluate()
        assertEquals(1, use().size)
        use.markShown(use())

        assertTrue(use().isEmpty())
    }

    @Test
    fun `an already-announced threshold stays announced across a restart`() = runTest {
        foodBudget()
        spend("a", 80_000)
        alertState.record(listOf(BudgetAlert("b-food", month, BudgetAlertThresholds.WARNING)))

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `an alert recorded for another month does not suppress this one`() = runTest {
        foodBudget()
        spend("a", 80_000)
        alertState.record(
            listOf(BudgetAlert("b-food", month.minusMonths(1), BudgetAlertThresholds.WARNING)),
        )

        assertEquals(1, evaluate()().size)
    }

    @Test
    fun `spending falling back below a threshold does not re-arm it`() = runTest {
        foodBudget()
        spend("a", 80_000)
        val use = evaluate()
        use.markShown(use())

        // The user deletes the expense and adds it again. The crossing has already
        // been announced; re-announcing it is the noise the record exists to stop.
        transactions.delete("a")
        assertTrue(use().isEmpty())

        spend("b", 80_000)
        assertTrue(use().isEmpty())
    }

    @Test
    fun `marking shown prunes records older than the retention window`() = runTest {
        foodBudget()
        spend("a", 80_000)
        alertState.record(
            listOf(BudgetAlert("b-food", month.minusMonths(9), BudgetAlertThresholds.EXCEEDED)),
        )

        val use = evaluate()
        use.markShown(use())

        assertTrue(alertState.fired.value.none { it.period == month.minusMonths(9) })
        assertTrue(alertState.fired.value.any { it.period == month })
    }

    // -- What must never alert ----------------------------------------------

    @Test
    fun `nothing fires when alerts are switched off`() = runTest {
        settings.update { it.copy(budgetAlertsEnabled = false) }
        foodBudget()
        spend("a", 120_000)

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `an archived budget never alerts`() = runTest {
        foodBudget()
        budgets.archive("b-food")
        spend("a", 120_000)

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `a budget pinned to another month never alerts`() = runTest {
        budgets.upsert(
            Budget(
                id = "b-food",
                categoryId = "cat-food",
                limitMinor = 100_000,
                currencyCode = "PKR",
                period = month.minusMonths(1),
            ),
        )
        spend("a", 120_000)

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `income never pushes a budget over`() = runTest {
        foodBudget()
        transactions.save(
            Transaction(
                id = "in",
                type = TransactionType.INCOME,
                amountMinor = 900_000,
                currencyCode = "PKR",
                categoryId = "cat-food",
                date = today,
                time = LocalTime.NOON,
            ),
        )

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `last month's spending does not trip this month's budget`() = runTest {
        foodBudget()
        spend("old", 500_000, date = LocalDate.of(2026, 8, 20))

        assertTrue(evaluate()().isEmpty())
    }

    @Test
    fun `a limit of zero cannot cross anything`() = runTest {
        foodBudget(limitMinor = 0)
        spend("a", 120_000)

        assertTrue(evaluate()().isEmpty())
    }

    // -- Currency -----------------------------------------------------------

    @Test
    fun `a foreign expense is converted into the budget's currency before comparing`() = runTest {
        // A $100 limit. Rs. 28,000 is exactly $100 at 280, so this is 100% — not the
        // 28,000% a raw comparison would report.
        foodBudget(limitMinor = 10_000, currencyCode = "USD")
        spend("a", 2_800_000, currencyCode = "PKR")

        val event = evaluate()().single()

        assertEquals(BudgetAlertThresholds.EXCEEDED, event.newlyCrossed)
        assertEquals(100.0, event.summary.progress.percent, 0.001)
    }

    // -- Scope --------------------------------------------------------------

    @Test
    fun `a subcategory budget only sees its own subcategory`() = runTest {
        budgets.upsert(
            Budget(
                id = "b-fast",
                categoryId = "cat-food",
                subcategoryId = "sub-fast",
                limitMinor = 100_000,
                currencyCode = "PKR",
            ),
        )
        spend("other", 200_000, subcategoryId = null)

        assertTrue(evaluate()().isEmpty())
    }

    // -- Deleting a budget --------------------------------------------------

    @Test
    fun `deleting a budget forgets what it had already announced`() = runTest {
        foodBudget()
        spend("a", 80_000)
        val use = evaluate()
        use.markShown(use())
        assertTrue(alertState.fired.value.any { it.budgetId == "b-food" })

        DeleteBudgetUseCase(budgets, alertState)("b-food")

        // Otherwise a later budget that reused the id would inherit an "already
        // announced" record it never earned, and stay silent through its first
        // overspend.
        assertTrue(alertState.fired.value.none { it.budgetId == "b-food" })
    }

    @Test
    fun `two budgets over the limit each get their own event`() = runTest {
        foodBudget()
        budgets.upsert(
            Budget(
                id = "b-fast",
                categoryId = "cat-food",
                subcategoryId = "sub-fast",
                limitMinor = 10_000,
                currencyCode = "PKR",
            ),
        )
        spend("a", 120_000, subcategoryId = "sub-fast")

        assertEquals(setOf("b-food", "b-fast"), evaluate()().map { it.summary.id }.toSet())
    }
}
