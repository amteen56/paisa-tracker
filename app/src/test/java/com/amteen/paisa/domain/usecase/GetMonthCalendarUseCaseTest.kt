package com.amteen.paisa.domain.usecase

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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * The month grid: layout, per-day figures and what the borrowed days do and do not
 * count towards.
 *
 * Runs against the real file-backed transaction repository, so the grid's reach into
 * the neighbouring months' shards is exercised rather than assumed.
 */
class GetMonthCalendarUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A Tuesday. September 2026 starts on a Tuesday, which is why it is used here. */
    private val today = LocalDate.of(2026, 9, 15)
    private val september = YearMonth.of(2026, 9)

    /** The only currency the app has. See CLAUDE.md — Paisa is PKR-only. */
    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)

    private val food = Category(
        id = "cat-food",
        name = "Food & Drink",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0,
        subcategories = listOf(Subcategory("sub-fastfood", "Fast Food")),
    )
    private val salary = Category(
        id = "cat-salary",
        name = "Salary",
        applicableTo = CategoryScope.INCOME,
        iconKey = "wallet",
        colorArgb = 0,
    )
    private val cash = PaymentMethod("pm-cash", "Cash", "cash")

    private lateinit var repository: FileTransactionRepositoryImpl

    private fun useCase(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): GetMonthCalendarUseCase {
        repository = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder()))
        return GetMonthCalendarUseCase(
            transactions = repository,
            categories = FakeCategoryRepository(listOf(food, salary)),
            paymentMethods = FakePaymentMethodRepository(listOf(cash)),
            currencies = FakeCurrencyRepository(listOf(pkr)),
            settings = FakeSettingsRepository(
                AppSettings(baseCurrencyCode = "PKR", firstDayOfWeek = firstDayOfWeek),
            ),
            today = { today },
        )
    }

    private fun transaction(
        id: String,
        amountMinor: Long,
        date: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
        currencyCode: String = "PKR",
        categoryId: String = "cat-food",
        hour: Int = 12,
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        categoryId = categoryId,
        date = date,
        time = LocalTime.of(hour, 0),
        paymentMethodId = "pm-cash",
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private suspend fun calendar(
        month: YearMonth = september,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        seed: suspend FileTransactionRepositoryImpl.() -> Unit = {},
    ): MonthCalendar {
        val use = useCase(firstDayOfWeek)
        repository.seed()
        return use(flowOf(month)).first()
    }

    // -- Grid layout --------------------------------------------------------

    @Test
    fun `every row is a whole week and every day belongs to exactly one cell`() = runTest {
        val result = calendar()

        assertTrue(result.weeks.all { it.size == 7 })
        // 1 September 2026 is a Tuesday, so one leading day plus 30 days is 31 cells,
        // which fits in five Monday-start rows.
        assertEquals(5, result.weeks.size)
        assertEquals(35, result.days.size)
        // Contiguous and ascending, with no repeats.
        result.days.zipWithNext { a, b -> assertEquals(b.date, a.date.plusDays(1)) }
        assertEquals(september.lengthOfMonth(), result.days.count { it.inMonth })
    }

    @Test
    fun `a month that overflows five rows gets a sixth`() = runTest {
        // 1 March 2026 is a Sunday, so a Monday-start grid needs six leading days
        // before a 31-day month — 37 cells, which will not fit in five rows.
        val result = calendar(month = YearMonth.of(2026, 3))

        assertEquals(6, result.weeks.size)
        assertEquals(42, result.days.size)
        assertEquals(LocalDate.of(2026, 2, 23), result.days.first().date)
        assertEquals(31, result.days.count { it.inMonth })
    }

    @Test
    fun `the grid starts on the user's first day of week`() = runTest {
        val monday = calendar(firstDayOfWeek = DayOfWeek.MONDAY)
        assertEquals(DayOfWeek.MONDAY, monday.days.first().date.dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, monday.weekdays.first())
        assertEquals(LocalDate.of(2026, 8, 31), monday.days.first().date)

        // A Sunday-start week shifts the whole grid back a day, borrowing two days
        // of August instead of one.
        val sunday = calendar(firstDayOfWeek = DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, sunday.days.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, sunday.weekdays.first())
        assertEquals(LocalDate.of(2026, 8, 30), sunday.days.first().date)

        // Saturday-start, to prove the modulo does not go negative when the month
        // starts before the chosen first day of the week.
        val saturday = calendar(firstDayOfWeek = DayOfWeek.SATURDAY)
        assertEquals(DayOfWeek.SATURDAY, saturday.days.first().date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 8, 29), saturday.days.first().date)
    }

    @Test
    fun `a month starting exactly on the first day of week borrows no leading days`() = runTest {
        // 1 June 2026 is a Monday.
        val result = calendar(month = YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), result.days.first().date)
        assertTrue(result.days.first().inMonth)
        assertEquals(5, result.weeks.size)
    }

    @Test
    fun `the weekday order has seven distinct days in calendar order`() {
        val order = GetMonthCalendarUseCase.weekdayOrder(DayOfWeek.WEDNESDAY)
        assertEquals(
            listOf(
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            ),
            order,
        )
    }

    @Test
    fun `today is marked, and only in the month that holds it`() = runTest {
        val current = calendar()
        assertEquals(listOf(today), current.days.filter { it.isToday }.map { it.date })

        // October's grid borrows the end of September, which includes nothing near
        // the 15th, so no cell is today.
        val october = calendar(month = YearMonth.of(2026, 10))
        assertTrue(october.days.none { it.isToday })
        assertEquals(today, october.today)
    }

    // -- Per-day figures ----------------------------------------------------

    @Test
    fun `income and expense on one day are kept apart, not pre-netted`() = runTest {
        val date = LocalDate.of(2026, 9, 10)
        val result = calendar {
            save(transaction("spend", 200_000, date))
            save(transaction("earn", 200_000, date, TransactionType.INCOME, categoryId = "cat-salary"))
        }

        val day = result.day(date)!!
        // A day that took in Rs. 2,000 and spent Rs. 2,000 is not a quiet day, and a
        // single net figure of zero cannot tell the two apart.
        assertEquals(200_000L, day.expenseMinor)
        assertEquals(200_000L, day.incomeMinor)
        assertEquals(0L, day.netMinor)
        assertTrue(day.hasActivity)
        assertTrue(day.hasExpense)
        assertTrue(day.hasIncome)
        assertEquals(2, day.count)
    }

    @Test
    fun `a day sums every transaction on it`() = runTest {
        val date = LocalDate.of(2026, 9, 10)
        val result = calendar {
            save(transaction("a", 200_000, date))
            save(transaction("b", 280_000, date))
        }

        val day = result.day(date)!!
        assertEquals(480_000L, day.expenseMinor)
        assertEquals("PKR", day.currencyCode)
        assertEquals(2, day.count)
    }

    @Test
    fun `a day carries its transactions with references resolved`() = runTest {
        val date = LocalDate.of(2026, 9, 10)
        val result = calendar { save(transaction("a", 80_000, date)) }

        // The day sheet must need no second read — the day is already in the grid.
        val item = result.day(date)!!.items.single()
        assertEquals("Food & Drink", item.category?.name)
        assertEquals("Cash", item.paymentMethod?.name)
        assertEquals("PKR", item.currency.code)
    }

    @Test
    fun `a day's transactions are newest first`() = runTest {
        val date = LocalDate.of(2026, 9, 10)
        val result = calendar {
            save(transaction("morning", 100, date, hour = 8))
            save(transaction("evening", 100, date, hour = 20))
        }

        assertEquals(listOf("evening", "morning"), result.day(date)!!.items.map { it.id })
    }

    @Test
    fun `a quiet day is zero rather than absent`() = runTest {
        val result = calendar { save(transaction("a", 100, LocalDate.of(2026, 9, 10))) }

        val quiet = result.day(LocalDate.of(2026, 9, 11))!!
        assertEquals(0L, quiet.expenseMinor)
        assertEquals(0L, quiet.incomeMinor)
        assertFalse(quiet.hasActivity)
        assertEquals(emptyList<String>(), quiet.items.map { it.id })
    }

    // -- The borrowed days --------------------------------------------------

    @Test
    fun `leading and trailing days show real figures from the neighbouring shards`() = runTest {
        val result = calendar {
            save(transaction("august", 500_000, LocalDate.of(2026, 8, 31)))
            save(transaction("october", 300_000, LocalDate.of(2026, 10, 2)))
        }

        val august = result.day(LocalDate.of(2026, 8, 31))!!
        val october = result.day(LocalDate.of(2026, 10, 2))!!

        // Spending on the 31st and the 1st is one continuous stretch to the person
        // who lived it, so the borrowed cells are not blanked out.
        assertFalse(august.inMonth)
        assertEquals(500_000L, august.expenseMinor)
        assertFalse(october.inMonth)
        assertEquals(300_000L, october.expenseMinor)
    }

    @Test
    fun `borrowed days are excluded from the month's totals`() = runTest {
        val result = calendar {
            save(transaction("august", 500_000, LocalDate.of(2026, 8, 31)))
            save(transaction("september", 200_000, LocalDate.of(2026, 9, 10)))
            save(transaction("october", 300_000, LocalDate.of(2026, 10, 2)))
        }

        // The grid shows all three; only one is September's.
        assertEquals(200_000L, result.totals.expense.amountMinor)
        assertEquals(1, result.totals.count)
        assertEquals(1, result.activeDayCount)
    }

    @Test
    fun `a borrowed day does not become the busiest day or set the peak`() = runTest {
        val result = calendar {
            // Far bigger than anything in September, and in the grid's first cell.
            save(transaction("august", 5_000_000, LocalDate.of(2026, 8, 31)))
            save(transaction("september", 200_000, LocalDate.of(2026, 9, 10)))
        }

        // Otherwise one big day either side would flatten the whole month's bars.
        assertEquals(LocalDate.of(2026, 9, 10), result.busiestDay?.date)
        assertEquals(200_000L, result.peakExpenseMinor)
    }

    // -- Month summary ------------------------------------------------------

    @Test
    fun `month totals net income against expense`() = runTest {
        val result = calendar {
            save(transaction("spend", 200_000, LocalDate.of(2026, 9, 3)))
            save(transaction("more", 280_000, LocalDate.of(2026, 9, 4)))
            save(
                transaction(
                    "earn", 1_000_000, LocalDate.of(2026, 9, 1),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        assertEquals(480_000L, result.totals.expense.amountMinor)
        assertEquals(1_000_000L, result.totals.income.amountMinor)
        assertEquals(520_000L, result.totals.net.amountMinor)
        assertEquals("PKR", result.totals.net.currencyCode)
        assertEquals(3, result.totals.count)
    }

    @Test
    fun `the busiest day is the biggest spender, and ties go to the earlier date`() = runTest {
        val result = calendar {
            save(transaction("small", 100_000, LocalDate.of(2026, 9, 2)))
            save(transaction("equal-later", 400_000, LocalDate.of(2026, 9, 20)))
            save(transaction("equal-earlier", 400_000, LocalDate.of(2026, 9, 8)))
        }

        // A stable answer matters: this is the label the summary card shows and the
        // shortcut a screen reader user reaches the month through.
        assertEquals(LocalDate.of(2026, 9, 8), result.busiestDay?.date)
        assertEquals(400_000L, result.peakExpenseMinor)
    }

    @Test
    fun `an income-only month has no busiest day and no peak`() = runTest {
        val result = calendar {
            save(
                transaction(
                    "earn", 1_000_000, LocalDate.of(2026, 9, 5),
                    TransactionType.INCOME, categoryId = "cat-salary",
                ),
            )
        }

        // The bars measure spending, so a month with none has nothing to scale by —
        // and "busiest day: nothing spent" would be a lie.
        assertNull(result.busiestDay)
        assertEquals(0L, result.peakExpenseMinor)
        assertEquals(1, result.activeDayCount)
        assertTrue(result.hasAnyTransactions)
    }

    @Test
    fun `active days counts days, not transactions`() = runTest {
        val result = calendar {
            save(transaction("a", 100, LocalDate.of(2026, 9, 4)))
            save(transaction("b", 100, LocalDate.of(2026, 9, 4)))
            save(transaction("c", 100, LocalDate.of(2026, 9, 5)))
        }

        assertEquals(2, result.activeDayCount)
        assertEquals(3, result.totals.count)
        assertEquals(30, result.daysInMonth)
    }

    @Test
    fun `an empty month is empty rather than absent`() = runTest {
        val result = calendar()

        assertFalse(result.hasAnyTransactions)
        assertEquals(0L, result.totals.expense.amountMinor)
        assertEquals(0, result.activeDayCount)
        assertNull(result.busiestDay)
        // The grid is still laid out, so the screen can show the month and let the
        // user tap a day to record against it.
        assertEquals(35, result.days.size)
    }

    @Test
    fun `a transaction whose category was deleted still counts`() = runTest {
        val date = LocalDate.of(2026, 9, 10)
        val result = calendar { save(transaction("orphan", 100_000, date, categoryId = "cat-gone")) }

        // Dropping the row would silently remove money from the month's total.
        assertEquals(100_000L, result.totals.expense.amountMinor)
        assertEquals("Uncategorised", result.day(date)!!.items.single().title)
    }

    @Test
    fun `stepping to the previous month re-derives against that month`() = runTest {
        val use = useCase()
        repository.save(transaction("august", 500_000, LocalDate.of(2026, 8, 20)))
        repository.save(transaction("september", 200_000, LocalDate.of(2026, 9, 10)))

        val august = use(flowOf(YearMonth.of(2026, 8))).first()
        assertEquals(YearMonth.of(2026, 8), august.month)
        assertEquals(500_000L, august.totals.expense.amountMinor)
        assertEquals(31, august.daysInMonth)

        val september = use(flowOf(this@GetMonthCalendarUseCaseTest.september)).first()
        assertEquals(200_000L, september.totals.expense.amountMinor)
    }

    @Test
    fun `a record carrying an unknown currency code still renders and still counts`() = runTest {
        val date = LocalDate.of(2026, 9, 10)
        // Not reachable through the UI — the app is PKR-only — but a hand-edited or
        // imported file can carry anything, and reads recover rather than crash.
        val result = calendar { save(transaction("odd", 100_000, date, currencyCode = "XXX")) }

        assertEquals("XXX", result.day(date)!!.items.single().currency.code)
        assertEquals(1, result.totals.count)
    }

    // -- The grid range the repository is asked for -------------------------

    @Test
    fun `the grid range spans whole weeks and reaches the neighbouring months`() {
        val range = GetMonthCalendarUseCase.gridRange(september, DayOfWeek.MONDAY)

        assertEquals(LocalDate.of(2026, 8, 31), range.start)
        assertEquals(LocalDate.of(2026, 10, 4), range.endInclusive)
        assertEquals(35, range.dayCount)
        // At most three shards, which is what keeps a month step cheap.
        assertEquals(
            listOf(YearMonth.of(2026, 8), september, YearMonth.of(2026, 10)),
            range.months(),
        )
    }

    @Test
    fun `a grid that needs no borrowed days reads one shard`() {
        // 1 February 2026 is a Sunday; 28 days from a Sunday-start week is exactly
        // four rows, so neither neighbour is touched.
        val range = GetMonthCalendarUseCase.gridRange(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
        assertEquals(listOf(YearMonth.of(2026, 2)), range.months())
    }

    @Test
    fun `the grid range is always a multiple of seven days`() {
        for (offset in 0L until 36L) {
            val month = YearMonth.of(2024, 1).plusMonths(offset)
            for (firstDay in DayOfWeek.entries) {
                val range = GetMonthCalendarUseCase.gridRange(month, firstDay)
                assertEquals(
                    "$month from $firstDay",
                    0,
                    range.dayCount % GetMonthCalendarUseCase.COLUMNS,
                )
                assertEquals("$month from $firstDay", firstDay, range.start.dayOfWeek)
                // Every day of the month has to be inside the grid, or a cell is
                // missing and the day is unreachable.
                assertTrue("$month from $firstDay", month.atDay(1) in range)
                assertTrue("$month from $firstDay", month.atEndOfMonth() in range)
            }
        }
    }
}
