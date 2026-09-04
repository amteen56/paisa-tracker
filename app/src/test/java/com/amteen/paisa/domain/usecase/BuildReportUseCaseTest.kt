package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.core.time.PeriodFilter
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeCategoryRepository
import com.amteen.paisa.testing.FakeCurrencyRepository
import com.amteen.paisa.testing.FakePaymentMethodRepository
import com.amteen.paisa.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Report figures, series and the bounded read.
 *
 * Runs against the real file-backed transaction repository, so the widened read that
 * feeds the trend and the comparison window is exercised rather than assumed.
 */
class BuildReportUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val today = LocalDate.of(2026, 9, 15)
    private val september = YearMonth.of(2026, 9)

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)

    private val food = Category(
        id = "cat-food",
        name = "Food & Drink",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0x11,
        subcategories = listOf(
            Subcategory("sub-fast", "Fast Food"),
            Subcategory("sub-groceries", "Groceries"),
        ),
    )
    private val transport = Category(
        id = "cat-transport",
        name = "Transport",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "car",
        colorArgb = 0x22,
    )
    private val salary = Category(
        id = "cat-salary",
        name = "Salary",
        applicableTo = CategoryScope.INCOME,
        iconKey = "wallet",
        colorArgb = 0x33,
    )
    private val cash = PaymentMethod("pm-cash", "Cash", "cash")

    private lateinit var repository: FileTransactionRepositoryImpl

    private fun useCase(): BuildReportUseCase {
        repository = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder()))
        return BuildReportUseCase(
            transactions = repository,
            categories = FakeCategoryRepository(listOf(food, transport, salary)),
            paymentMethods = FakePaymentMethodRepository(listOf(cash)),
            currencies = FakeCurrencyRepository(listOf(pkr)),
            settings = FakeSettingsRepository(AppSettings(baseCurrencyCode = "PKR")),
            today = { today },
        )
    }

    private fun transaction(
        id: String,
        amountMinor: Long,
        date: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String = "cat-food",
        subcategoryId: String? = null,
        description: String = "",
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = "PKR",
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        description = description,
        date = date,
        time = LocalTime.NOON,
        paymentMethodId = "pm-cash",
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private suspend fun report(
        period: PeriodFilter = PeriodFilter.ThisMonth,
        categoryId: String? = null,
        seed: suspend FileTransactionRepositoryImpl.() -> Unit = {},
    ): Report {
        val use = useCase()
        repository.seed()
        return use(flowOf(BuildReportUseCase.Request(period, categoryId))).first()
    }

    // -- Totals -------------------------------------------------------------

    @Test
    fun `totals cover the period and nothing else`() = runTest {
        val result = report {
            save(transaction("in", 200_000, LocalDate.of(2026, 9, 4)))
            save(transaction("also-in", 100_000, LocalDate.of(2026, 9, 20)))
            // Loaded by the widened read for the trend, but outside the period.
            save(transaction("out", 900_000, LocalDate.of(2026, 8, 10)))
        }

        assertEquals(300_000L, result.totals.expense.amountMinor)
        assertEquals(2, result.totals.count)
    }

    @Test
    fun `net is income minus expense`() = runTest {
        val result = report {
            save(transaction("spend", 200_000, LocalDate.of(2026, 9, 3)))
            save(
                transaction(
                    "earn", 1_000_000, LocalDate.of(2026, 9, 1),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        assertEquals(1_000_000L, result.totals.income.amountMinor)
        assertEquals(200_000L, result.totals.expense.amountMinor)
        assertEquals(800_000L, result.totals.net.amountMinor)
        // One currency: nothing is converted, so nothing is disclosed as converted.
        assertFalse(result.totals.mixedCurrency)
    }

    @Test
    fun `the daily average divides by the period, not by days that had spending`() = runTest {
        val result = report {
            // 900,000 over a 30-day month is 30,000 a day, even though only two days
            // had anything on them. Dividing by 2 would report a figure the user has
            // never averaged.
            save(transaction("a", 600_000, LocalDate.of(2026, 9, 4)))
            save(transaction("b", 300_000, LocalDate.of(2026, 9, 5)))
        }

        assertEquals(30, result.dayCount)
        assertEquals(30_000L, result.averageExpensePerDayMinor)
    }

    @Test
    fun `the average rounds half-up and never goes through a Double`() = runTest {
        val result = report(period = PeriodFilter.Custom(DateRange(today, today.plusDays(2)))) {
            // 100 over 3 days is 33.33; half-up on the minor units gives 33.
            save(transaction("a", 100, today))
        }
        assertEquals(3, result.dayCount)
        assertEquals(33L, result.averageExpensePerDayMinor)
    }

    // -- Category breakdown -------------------------------------------------

    @Test
    fun `categories are ranked by amount with shares of the period expense`() = runTest {
        val result = report {
            save(transaction("f1", 600_000, LocalDate.of(2026, 9, 2)))
            save(transaction("f2", 200_000, LocalDate.of(2026, 9, 3)))
            save(transaction("t1", 200_000, LocalDate.of(2026, 9, 4), categoryId = "cat-transport"))
        }

        val slices = result.categories
        assertEquals(listOf("cat-food", "cat-transport"), slices.map { it.categoryId })
        assertEquals(800_000L, slices[0].amountMinor)
        assertEquals(2, slices[0].count)
        assertEquals(0.8f, slices[0].share, 0.001f)
        assertEquals(0.2f, slices[1].share, 0.001f)
    }

    @Test
    fun `income never appears in the category breakdown`() = runTest {
        val result = report {
            save(transaction("spend", 200_000, LocalDate.of(2026, 9, 3)))
            save(
                transaction(
                    "earn", 1_000_000, LocalDate.of(2026, 9, 1),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        // The donut is a breakdown of spending. Folding a salary into it would make
        // every share meaningless.
        assertEquals(listOf("cat-food"), result.categories.map { it.categoryId })
    }

    @Test
    fun `a deleted category still gets a slice, a name and a colour`() = runTest {
        val result = report {
            save(transaction("orphan", 200_000, LocalDate.of(2026, 9, 3), categoryId = "cat-gone"))
        }

        val slice = result.categories.single()
        assertEquals("Uncategorised", slice.name)
        // Dropping it would silently remove money from the breakdown, and a zero
        // colour would draw an invisible slice.
        assertEquals(200_000L, slice.amountMinor)
        assertTrue(slice.colorArgb != 0)
    }

    @Test
    fun `the breakdown is capped so the ring stays readable`() = runTest {
        val use = useCase()
        repeat(12) { index ->
            repository.save(
                transaction(
                    "t$index", (12 - index) * 10_000L,
                    LocalDate.of(2026, 9, 2), categoryId = "cat-$index",
                ),
            )
        }
        val result = use(flowOf(BuildReportUseCase.Request(PeriodFilter.ThisMonth))).first()

        assertEquals(Report.TOP_CATEGORY_COUNT, result.categories.size)
        // The cap takes the biggest, not an arbitrary eight.
        assertEquals("cat-0", result.categories.first().categoryId)
    }

    // -- Drill-down ---------------------------------------------------------

    @Test
    fun `drilling into a category breaks it down by subcategory`() = runTest {
        val result = report(categoryId = "cat-food") {
            save(transaction("a", 600_000, LocalDate.of(2026, 9, 2), subcategoryId = "sub-fast"))
            save(transaction("b", 200_000, LocalDate.of(2026, 9, 3), subcategoryId = "sub-groceries"))
            // Another category's spending must not leak into the drill-down.
            save(transaction("c", 900_000, LocalDate.of(2026, 9, 4), categoryId = "cat-transport"))
        }

        val subs = result.subcategories
        assertEquals(listOf("Fast Food", "Groceries"), subs.map { it.name })
        assertEquals(600_000L, subs[0].amountMinor)
        // Shares are of the category, not of the whole period.
        assertEquals(0.75f, subs[0].share, 0.001f)
    }

    @Test
    fun `spending with no subcategory becomes its own named bucket`() = runTest {
        val result = report(categoryId = "cat-food") {
            save(transaction("tagged", 200_000, LocalDate.of(2026, 9, 2), subcategoryId = "sub-fast"))
            save(transaction("untagged", 300_000, LocalDate.of(2026, 9, 3)))
        }

        // Dropping the null bucket would lose Rs. 3,000 out of a breakdown that is
        // supposed to add up to the category total.
        val unspecified = result.subcategories.first { it.subcategoryId == null }
        assertEquals("Unspecified", unspecified.name)
        assertEquals(300_000L, unspecified.amountMinor)
        assertEquals(500_000L, result.subcategories.sumOf { it.amountMinor })
    }

    @Test
    fun `no drill-down means no subcategory figures`() = runTest {
        val result = report {
            save(transaction("a", 200_000, LocalDate.of(2026, 9, 2), subcategoryId = "sub-fast"))
        }
        assertNull(result.selectedCategoryId)
        assertEquals(emptyList<SubcategorySlice>(), result.subcategories)
    }

    // -- Series -------------------------------------------------------------

    @Test
    fun `the daily series is zero-filled across the whole period`() = runTest {
        val result = report {
            save(transaction("a", 200_000, LocalDate.of(2026, 9, 4)))
        }

        assertEquals(30, result.dailySeries.size)
        assertEquals(september.atDay(1), result.dailySeries.first().date)
        assertEquals(september.atEndOfMonth(), result.dailySeries.last().date)
        // A quiet day is drawn as a quiet day rather than skipped, or the bars would
        // silently compress and misrepresent the shape of the month.
        assertEquals(0L, result.dailySeries.first().expenseMinor)
        assertEquals(200_000L, result.dailySeries.first { it.date.dayOfMonth == 4 }.expenseMinor)
    }

    @Test
    fun `a period too long for bars produces no daily series`() = runTest {
        val result = report(period = PeriodFilter.ThisYear) {
            save(transaction("a", 200_000, LocalDate.of(2026, 9, 4)))
        }

        // 365 one-pixel bars is a picture of nothing; the screen shows the trend.
        assertTrue(result.dailySeries.isEmpty())
        assertTrue(result.monthlySeries.size >= 2)
    }

    @Test
    fun `a short period still gets a multi-month trend`() = runTest {
        val result = report {
            save(transaction("a", 200_000, LocalDate.of(2026, 9, 4)))
        }

        // A one-point line is a dot, which says nothing about direction.
        assertEquals(Report.TREND_MONTHS, result.monthlySeries.size)
        assertEquals(YearMonth.of(2026, 4), result.monthlySeries.first().month)
        assertEquals(september, result.monthlySeries.last().month)
    }

    @Test
    fun `the trend counts months outside the report period`() = runTest {
        val result = report {
            save(transaction("sep", 200_000, LocalDate.of(2026, 9, 4)))
            save(transaction("aug", 500_000, LocalDate.of(2026, 8, 10)))
            save(
                transaction(
                    "aug-in", 1_000_000, LocalDate.of(2026, 8, 1),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        val august = result.monthlySeries.first { it.month == YearMonth.of(2026, 8) }
        // The whole point of the trend is the months either side of the report.
        assertEquals(500_000L, august.expenseMinor)
        assertEquals(1_000_000L, august.incomeMinor)
        assertEquals(500_000L, august.netMinor)
        // ...but they stay out of the period's own totals.
        assertEquals(200_000L, result.totals.expense.amountMinor)
    }

    @Test
    fun `the busiest day is the biggest spender, ties going to the earlier date`() = runTest {
        val result = report {
            save(transaction("small", 100_000, LocalDate.of(2026, 9, 2)))
            save(transaction("equal-late", 400_000, LocalDate.of(2026, 9, 20)))
            save(transaction("equal-early", 400_000, LocalDate.of(2026, 9, 8)))
        }

        assertEquals(LocalDate.of(2026, 9, 8), result.busiestDay?.date)
        assertEquals(400_000L, result.busiestDay?.expenseMinor)
    }

    @Test
    fun `an income-only period has no busiest day`() = runTest {
        val result = report {
            save(
                transaction(
                    "earn", 1_000_000, LocalDate.of(2026, 9, 1),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        // "Busiest day: nothing spent" would be a lie.
        assertNull(result.busiestDay)
        assertTrue(result.hasAnyTransactions)
    }

    // -- Top expenses -------------------------------------------------------

    @Test
    fun `top expenses are the biggest, capped, and never include income`() = runTest {
        val result = report {
            repeat(14) { index ->
                save(transaction("e$index", (index + 1) * 10_000L, LocalDate.of(2026, 9, 2)))
            }
            save(
                transaction(
                    "huge-income", 9_000_000, LocalDate.of(2026, 9, 3),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        assertEquals(Report.TOP_EXPENSE_COUNT, result.topExpenses.size)
        assertEquals("e13", result.topExpenses.first().id)
        assertTrue(result.topExpenses.none { it.transaction.type.isIncome })
        // References resolved, so the row needs no lookups while scrolling.
        assertEquals("Food & Drink", result.topExpenses.first().category?.name)
    }

    // -- Comparison ---------------------------------------------------------

    @Test
    fun `a month is compared against the month before it`() = runTest {
        val result = report {
            save(transaction("sep", 300_000, LocalDate.of(2026, 9, 10)))
            save(transaction("aug", 200_000, LocalDate.of(2026, 8, 10)))
        }

        assertEquals(200_000L, result.previousExpenseMinor)
        // Up by half.
        assertEquals(50.0, result.expenseChangePercent!!, 0.001)
    }

    @Test
    fun `a period with no previous spending reports no percentage`() = runTest {
        val result = report {
            save(transaction("sep", 300_000, LocalDate.of(2026, 9, 10)))
        }

        // A jump from zero is not "up 100%" — it is the first period with anything
        // in it, and dressing that up as a percentage says nothing.
        assertEquals(0L, result.previousExpenseMinor)
        assertNull(result.expenseChangePercent)
    }

    @Test
    fun `a period too long to compare reports no comparison at all`() = runTest {
        val result = report(period = PeriodFilter.ThisYear) {
            save(transaction("a", 300_000, LocalDate.of(2026, 9, 10)))
        }

        // Doubling the read to compare a year against the year before is a lot of
        // file I/O for one percentage.
        assertNull(result.previousExpenseMinor)
        assertNull(result.expenseChangePercent)
    }

    // -- All time -----------------------------------------------------------

    @Test
    fun `an all-time report spans the data and has no bounding range`() = runTest {
        val result = report(period = PeriodFilter.AllTime) {
            save(transaction("old", 100_000, LocalDate.of(2025, 3, 4)))
            save(transaction("new", 200_000, LocalDate.of(2026, 9, 10)))
        }

        assertNull(result.range)
        assertEquals(300_000L, result.totals.expense.amountMinor)
        assertEquals(2, result.totals.count)
        // Far more than 62 days, so no daily bars.
        assertTrue(result.dailySeries.isEmpty())
        assertNull(result.previousExpenseMinor)
        // The trend covers the months the data actually spans.
        assertEquals(YearMonth.of(2025, 3), result.monthlySeries.first().month)
        assertEquals(YearMonth.of(2026, 9), result.monthlySeries.last().month)
    }

    @Test
    fun `an empty period is empty rather than broken`() = runTest {
        val result = report()

        assertFalse(result.hasAnyTransactions)
        assertEquals(0L, result.totals.expense.amountMinor)
        assertEquals(0L, result.averageExpensePerDayMinor)
        assertEquals(emptyList<CategorySlice>(), result.categories)
        assertNull(result.busiestDay)
        assertTrue(result.topExpenses.isEmpty())
        // The daily series is still laid out, so the chart renders a flat month
        // rather than disappearing.
        assertEquals(30, result.dailySeries.size)
    }

    @Test
    fun `a custom range is honoured exactly`() = runTest {
        val range = DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12))
        val result = report(period = PeriodFilter.Custom(range)) {
            save(transaction("before", 100_000, LocalDate.of(2026, 9, 9)))
            save(transaction("inside", 200_000, LocalDate.of(2026, 9, 11)))
            save(transaction("after", 400_000, LocalDate.of(2026, 9, 13)))
        }

        assertEquals(200_000L, result.totals.expense.amountMinor)
        assertEquals(3, result.dayCount)
        assertEquals(3, result.dailySeries.size)
        // The preceding three days, so the 9th is the comparison window.
        assertEquals(100_000L, result.previousExpenseMinor)
    }
}
