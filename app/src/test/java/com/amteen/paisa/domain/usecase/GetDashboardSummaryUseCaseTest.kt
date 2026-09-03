package com.amteen.paisa.domain.usecase

import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeBudgetRepository
import com.amteen.paisa.testing.FakeCategoryRepository
import com.amteen.paisa.testing.FakeCurrencyRepository
import com.amteen.paisa.testing.FakePaymentMethodRepository
import com.amteen.paisa.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * The dashboard figures.
 *
 * Every number on the home screen comes from here, so these are the tests that stop
 * the first screen the user sees from lying to them. As elsewhere, the transaction
 * repository is the real file-backed one: the two-shard load is part of what is
 * being tested, and a fake would quietly serve everything from one list.
 */
class GetDashboardSummaryUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A Saturday, deliberately mid-month so "so far" is a partial month. */
    private val today = LocalDate.of(2026, 9, 12)

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    private val usd = Currency("USD", "US Dollar", "$", 2, 280.0)

    private val food = Category(
        id = "cat-food",
        name = "Food",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0x11223344,
        subcategories = listOf(Subcategory("sub-fastfood", "Fast Food")),
    )
    private val transport = Category(
        id = "cat-transport",
        name = "Transport",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "car",
        colorArgb = 0x55667788,
    )
    private val salary = Category(
        id = "cat-salary",
        name = "Salary",
        applicableTo = CategoryScope.INCOME,
        iconKey = "wallet",
        colorArgb = 0,
    )

    private lateinit var repository: FileTransactionRepositoryImpl
    private lateinit var budgets: FakeBudgetRepository

    @Before
    fun setUp() {
        repository = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        budgets = FakeBudgetRepository()
    }

    private fun useCase(
        baseCurrency: String = "PKR",
        on: LocalDate = today,
    ) = GetDashboardSummaryUseCase(
        transactions = repository,
        categories = FakeCategoryRepository(listOf(food, transport, salary)),
        paymentMethods = FakePaymentMethodRepository(
            listOf(PaymentMethod("pm-cash", "Cash", "cash")),
        ),
        currencies = FakeCurrencyRepository(listOf(pkr, usd)),
        settings = FakeSettingsRepository(AppSettings(baseCurrencyCode = baseCurrency)),
        budgets = budgets,
        today = { on },
    )

    private suspend fun summary(baseCurrency: String = "PKR", on: LocalDate = today) =
        useCase(baseCurrency, on)().first()

    private fun transaction(
        id: String,
        amountMinor: Long,
        date: LocalDate = today,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String = "cat-food",
        subcategoryId: String? = null,
        currencyCode: String = "PKR",
        hour: Int = 12,
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        date = date,
        time = LocalTime.of(hour, 0),
    )

    // -- Totals -------------------------------------------------------------

    @Test
    fun `an empty ledger reports zeroes rather than failing`() = runTest {
        val result = summary()

        assertFalse(result.hasAnyTransactions)
        assertEquals(0L, result.totals.income.amountMinor)
        assertEquals(0L, result.totals.expense.amountMinor)
        assertEquals(0L, result.dailyAverageMinor)
        assertEquals(7, result.dailySpend.size)
        assertTrue(result.topCategories.isEmpty())
    }

    @Test
    fun `income and expense are totalled separately for the current month`() = runTest {
        repository.save(transaction("a", 80_000))
        repository.save(
            transaction("b", 500_000, type = TransactionType.INCOME, categoryId = "cat-salary"),
        )

        val result = summary()

        assertEquals(500_000L, result.totals.income.amountMinor)
        assertEquals(80_000L, result.totals.expense.amountMinor)
        assertEquals(420_000L, result.totals.net.amountMinor)
    }

    @Test
    fun `last month's transactions stay out of this month's totals`() = runTest {
        repository.save(transaction("a", 80_000))
        repository.save(transaction("old", 999_000, date = LocalDate.of(2026, 8, 12)))

        val result = summary()

        assertEquals(80_000L, result.totals.expense.amountMinor)
    }

    @Test
    fun `today's spend counts only today, and only expenses`() = runTest {
        repository.save(transaction("today-1", 30_000))
        repository.save(transaction("today-2", 20_000))
        repository.save(transaction("yesterday", 90_000, date = today.minusDays(1)))
        repository.save(
            transaction(
                "income-today", 400_000,
                type = TransactionType.INCOME, categoryId = "cat-salary",
            ),
        )

        assertEquals(50_000L, summary().todaySpentMinor)
    }

    // -- Currency -----------------------------------------------------------

    @Test
    fun `a foreign expense is converted into the base currency before summing`() = runTest {
        repository.save(transaction("pkr", 100_000))
        // $10 at 280 = Rs. 2,800 = 280,000 minor units.
        repository.save(transaction("usd", 1_000, currencyCode = "USD"))

        val result = summary()

        assertEquals(380_000L, result.totals.expense.amountMinor)
        assertTrue(result.mixedCurrency)
    }

    @Test
    fun `a single-currency month is not labelled as converted`() = runTest {
        repository.save(transaction("a", 100_000))

        assertFalse(summary().mixedCurrency)
    }

    // -- Daily average ------------------------------------------------------

    @Test
    fun `the daily average divides by the days elapsed, not the days in the month`() = runTest {
        // Rs. 1,200 spent by the 12th averages Rs. 100 a day, not Rs. 40.
        repository.save(transaction("a", 120_000))

        val result = summary()

        assertEquals(12, result.daysElapsed)
        assertEquals(10_000L, result.dailyAverageMinor)
    }

    @Test
    fun `the daily average rounds half up and never touches a double`() = runTest {
        // 100 minor units over 12 days is 8.33..., which must round to 8.
        repository.save(transaction("a", 100))

        assertEquals(8L, summary().dailyAverageMinor)
    }

    // -- Month-on-month comparison ------------------------------------------

    @Test
    fun `the comparison uses the same stretch of last month, not the whole of it`() = runTest {
        repository.save(transaction("this", 100_000))
        // Before the 12th: counts. After it: does not, or a part-month would always
        // look like a fall.
        repository.save(transaction("prev-early", 60_000, date = LocalDate.of(2026, 8, 5)))
        repository.save(transaction("prev-late", 500_000, date = LocalDate.of(2026, 8, 25)))

        val result = summary()

        assertEquals(60_000L, result.previousMonthToDateExpenseMinor)
        // 100,000 against 60,000 is up two thirds.
        assertEquals(66.67, result.expenseChangePercent!!, 0.01)
    }

    @Test
    fun `a first month with no history to compare reports no change rather than an infinity`() =
        runTest {
            repository.save(transaction("this", 100_000))

            assertNull(summary().expenseChangePercent)
        }

    // -- Seven-day window ---------------------------------------------------

    @Test
    fun `the window ends today, spans seven days, and excludes the eighth`() = runTest {
        repository.save(transaction("in", 50_000, date = today.minusDays(6)))
        repository.save(transaction("out", 90_000, date = today.minusDays(7)))

        val window = summary().dailySpend

        assertEquals(7, window.size)
        assertEquals(today.minusDays(6), window.first().date)
        assertEquals(today, window.last().date)
        assertEquals(50_000L, window.first().amountMinor)
        assertTrue(window.none { it.amountMinor == 90_000L })
    }

    @Test
    fun `the window reaches back into last month during the first week`() = runTest {
        // On the 3rd, four of the seven days are in August — which is exactly why
        // the dashboard loads the previous shard as well as the current one.
        val third = LocalDate.of(2026, 9, 3)
        repository.save(transaction("august", 70_000, date = LocalDate.of(2026, 8, 30)))
        repository.save(transaction("september", 20_000, date = third))

        val result = summary(on = third)
        val window = result.dailySpend

        assertEquals(LocalDate.of(2026, 8, 28), window.first().date)
        assertEquals(70_000L, window.single { it.date == LocalDate.of(2026, 8, 30) }.amountMinor)
        assertEquals(20_000L, window.last().amountMinor)
        // August's spending belongs in the chart but not in September's total.
        assertEquals(20_000L, result.totals.expense.amountMinor)
    }

    @Test
    fun `the window is zero filled so a day with no spending is still a day`() = runTest {
        repository.save(transaction("a", 50_000))

        val window = summary().dailySpend

        assertEquals(7, window.size)
        assertEquals(6, window.count { it.amountMinor == 0L })
        assertEquals(50_000L, window.last().amountMinor)
    }

    @Test
    fun `income never appears in the seven day spending window`() = runTest {
        repository.save(
            transaction("in", 900_000, type = TransactionType.INCOME, categoryId = "cat-salary"),
        )

        assertTrue(summary().dailySpend.all { it.amountMinor == 0L })
    }

    // -- Top categories -----------------------------------------------------

    @Test
    fun `top categories are ordered by spend and carry their share of the month`() = runTest {
        repository.save(transaction("f1", 60_000, categoryId = "cat-food"))
        repository.save(transaction("f2", 20_000, categoryId = "cat-food"))
        repository.save(transaction("t1", 20_000, categoryId = "cat-transport"))

        val top = summary().topCategories

        assertEquals(listOf("Food", "Transport"), top.map { it.name })
        assertEquals(80_000L, top[0].amountMinor)
        assertEquals(0.8f, top[0].share, 0.0001f)
        assertEquals(0.2f, top[1].share, 0.0001f)
    }

    @Test
    fun `a category that no longer resolves still appears rather than vanishing`() = runTest {
        repository.save(transaction("ghost", 40_000, categoryId = "cat-deleted"))

        val top = summary().topCategories.single()

        assertEquals("Uncategorised", top.name)
        assertEquals(40_000L, top.amountMinor)
    }

    @Test
    fun `income is not counted as spending in the category breakdown`() = runTest {
        repository.save(
            transaction("in", 900_000, type = TransactionType.INCOME, categoryId = "cat-salary"),
        )

        assertTrue(summary().topCategories.isEmpty())
    }

    // -- Recent -------------------------------------------------------------

    @Test
    fun `recent transactions are newest first and resolve their references`() = runTest {
        repository.save(transaction("older", 10_000, date = today.minusDays(3)))
        repository.save(transaction("newest", 20_000, subcategoryId = "sub-fastfood", hour = 18))
        repository.save(transaction("middle", 30_000, hour = 9))

        val recent = summary().recent

        assertEquals(listOf("newest", "middle", "older"), recent.map { it.id })
        assertEquals("Food", recent.first().category?.name)
        assertEquals("Fast Food", recent.first().subcategory?.name)
    }

    @Test
    fun `the recent list is capped`() = runTest {
        repeat(9) { index -> repository.save(transaction("t$index", 1_000, hour = index + 1)) }

        assertEquals(DashboardSummary.RECENT_COUNT, summary().recent.size)
    }

    // -- Budgets ------------------------------------------------------------

    @Test
    fun `a budget's usage is derived from this month's matching expenses`() = runTest {
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-food", limitMinor = 200_000, currencyCode = "PKR"),
        )
        repository.save(transaction("a", 50_000, categoryId = "cat-food"))
        repository.save(transaction("b", 30_000, categoryId = "cat-transport"))
        repository.save(transaction("old", 90_000, date = LocalDate.of(2026, 8, 3)))

        val progress = summary().budgets.single().progress

        assertEquals(50_000L, progress.spentMinor)
        assertEquals(150_000L, progress.remainingMinor)
        assertEquals(25.0, progress.percent, 0.001)
    }

    @Test
    fun `budget usage is measured in the budget's own currency, not the base`() = runTest {
        // A $100 limit with PKR as base. A Rs. 2,800 expense is exactly $10 of it —
        // comparing the raw numbers would report 2,800 against 100 and scream.
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-food", limitMinor = 10_000, currencyCode = "USD"),
        )
        repository.save(transaction("a", 280_000, currencyCode = "PKR"))

        val budget = summary().budgets.single()

        assertEquals("USD", budget.progress.spent.currencyCode)
        assertEquals(1_000L, budget.progress.spentMinor)
        assertEquals(10.0, budget.progress.percent, 0.001)
        assertTrue(budget.progress.mixedCurrency)
        // The symbol has to come from the real currency, or the limit renders as
        // "USD 100.00" instead of "$100.00".
        assertEquals("$", budget.currency.symbol)
    }

    @Test
    fun `a subcategory budget counts only that subcategory`() = runTest {
        budgets.upsert(
            Budget(
                id = "b1",
                categoryId = "cat-food",
                subcategoryId = "sub-fastfood",
                limitMinor = 100_000,
                currencyCode = "PKR",
            ),
        )
        repository.save(transaction("fast", 20_000, subcategoryId = "sub-fastfood"))
        repository.save(transaction("other", 70_000, subcategoryId = null))

        assertEquals(20_000L, summary().budgets.single().progress.spentMinor)
    }

    @Test
    fun `a category budget counts its subcategories too`() = runTest {
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-food", limitMinor = 100_000, currencyCode = "PKR"),
        )
        repository.save(transaction("fast", 20_000, subcategoryId = "sub-fastfood"))
        repository.save(transaction("other", 70_000, subcategoryId = null))

        assertEquals(90_000L, summary().budgets.single().progress.spentMinor)
    }

    @Test
    fun `budgets closest to their limit come first`() = runTest {
        budgets.upsert(
            Budget(id = "safe", categoryId = "cat-food", limitMinor = 1_000_000, currencyCode = "PKR"),
        )
        budgets.upsert(
            Budget(
                id = "tight",
                categoryId = "cat-transport",
                limitMinor = 30_000,
                currencyCode = "PKR",
            ),
        )
        repository.save(transaction("f", 50_000, categoryId = "cat-food"))
        repository.save(transaction("t", 29_000, categoryId = "cat-transport"))

        assertEquals(listOf("tight", "safe"), summary().budgets.map { it.id })
    }

    @Test
    fun `an archived budget is left out`() = runTest {
        budgets.upsert(
            Budget(
                id = "b1",
                categoryId = "cat-food",
                limitMinor = 100_000,
                currencyCode = "PKR",
                archived = true,
            ),
        )
        repository.save(transaction("a", 50_000))

        assertTrue(summary().budgets.isEmpty())
    }

    @Test
    fun `a budget pinned to another month does not apply to this one`() = runTest {
        budgets.upsert(
            Budget(
                id = "b1",
                categoryId = "cat-food",
                limitMinor = 100_000,
                currencyCode = "PKR",
                period = YearMonth.of(2026, 8),
            ),
        )
        repository.save(transaction("a", 50_000))

        assertTrue(summary().budgets.isEmpty())
    }

    @Test
    fun `income never counts against a budget`() = runTest {
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-food", limitMinor = 100_000, currencyCode = "PKR"),
        )
        repository.save(transaction("in", 90_000, type = TransactionType.INCOME))

        assertEquals(0L, summary().budgets.single().progress.spentMinor)
    }
}
