package com.amteen.paisa.domain.usecase

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
import com.amteen.paisa.domain.model.TransactionQuery
import com.amteen.paisa.domain.model.TransactionSort
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Filtering, sorting, grouping and totals.
 *
 * Runs against the real file-backed transaction repository so the month-shard
 * behaviour is exercised alongside the query logic.
 */
class ObserveTransactionsUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val today = LocalDate.of(2026, 9, 15)

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)

    private val food = Category(
        id = "cat-food",
        name = "Food & Drink",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0,
        subcategories = listOf(Subcategory("sub-fastfood", "Fast Food")),
    )
    private val transport = Category(
        id = "cat-transport",
        name = "Transport",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "car",
        colorArgb = 0,
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
    private lateinit var observe: ObserveTransactionsUseCase

    @Before
    fun setUp() {
        repository = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        observe = ObserveTransactionsUseCase(
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
        currencyCode: String = "PKR",
        categoryId: String = "cat-food",
        subcategoryId: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        date: LocalDate = today,
        description: String = "",
        paymentMethodId: String? = null,
        hour: Int = 12,
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        description = description,
        date = date,
        time = LocalTime.of(hour, 0),
        paymentMethodId = paymentMethodId,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    private suspend fun run(query: TransactionQuery) = observe(flowOf(query)).first()

    @Test
    fun `resolves category, subcategory and payment method onto each row`() = runTest {
        repository.save(
            transaction(
                "a", 80_000,
                subcategoryId = "sub-fastfood",
                paymentMethodId = "pm-cash",
                description = "Burger",
            ),
        )

        val item = run(TransactionQuery()).items.single()

        assertEquals("Food & Drink", item.category?.name)
        assertEquals("Fast Food", item.subcategory?.name)
        assertEquals("Cash", item.paymentMethod?.name)
        assertEquals("Burger", item.title)
        assertEquals("Food & Drink · Fast Food", item.subtitle)
    }

    @Test
    fun `falls back to the category name when there is no description`() = runTest {
        repository.save(transaction("a", 80_000))
        assertEquals("Food & Drink", run(TransactionQuery()).items.single().title)
    }

    @Test
    fun `filters by type`() = runTest {
        repository.save(transaction("expense", 80_000))
        repository.save(
            transaction("income", 500_000, categoryId = "cat-salary", type = TransactionType.INCOME),
        )

        val result = run(TransactionQuery(types = setOf(TransactionType.INCOME)))
        assertEquals(listOf("income"), result.items.map { it.id })
    }

    @Test
    fun `searches description, notes, category and payment method`() = runTest {
        repository.save(transaction("a", 80_000, description = "Burger"))
        repository.save(transaction("b", 30_000, categoryId = "cat-transport", description = "Uber"))

        assertEquals(listOf("a"), run(TransactionQuery(text = "burg")).items.map { it.id })
        // Matching the category name, not just the description.
        assertEquals(listOf("b"), run(TransactionQuery(text = "Transport")).items.map { it.id })
        assertEquals(emptyList<String>(), run(TransactionQuery(text = "zzz")).items.map { it.id })
    }

    @Test
    fun `search is case-insensitive and ignores surrounding space`() = runTest {
        repository.save(transaction("a", 80_000, description = "Burger"))
        assertEquals(listOf("a"), run(TransactionQuery(text = "  BURGER ")).items.map { it.id })
    }

    @Test
    fun `filters by category and by payment method`() = runTest {
        repository.save(transaction("a", 80_000, paymentMethodId = "pm-cash"))
        repository.save(transaction("b", 30_000, categoryId = "cat-transport"))

        assertEquals(
            listOf("b"),
            run(TransactionQuery(categoryIds = setOf("cat-transport"))).items.map { it.id },
        )
        assertEquals(
            listOf("a"),
            run(TransactionQuery(paymentMethodIds = setOf("pm-cash"))).items.map { it.id },
        )
    }

    @Test
    fun `the amount filter excludes what falls outside the bounds`() = runTest {
        repository.save(transaction("big", 500_000)) // Rs. 5,000
        repository.save(transaction("small", 50_000)) // Rs. 500

        assertEquals(
            listOf("big"),
            run(TransactionQuery(minAmountMinorBase = 100_000)).items.map { it.id },
        )
        assertEquals(
            listOf("small"),
            run(TransactionQuery(maxAmountMinorBase = 100_000)).items.map { it.id },
        )
    }

    @Test
    fun `sorting by amount runs both ways`() = runTest {
        repository.save(transaction("smaller", 200_000))
        repository.save(transaction("bigger", 280_000))

        val high = run(TransactionQuery(sort = TransactionSort.AMOUNT_HIGH_FIRST))
        assertEquals(listOf("bigger", "smaller"), high.items.map { it.id })

        val low = run(TransactionQuery(sort = TransactionSort.AMOUNT_LOW_FIRST))
        assertEquals(listOf("smaller", "bigger"), low.items.map { it.id })
    }

    @Test
    fun `sorting by date runs both ways`() = runTest {
        repository.save(transaction("older", 100, date = today.minusDays(3)))
        repository.save(transaction("newer", 100, date = today))

        assertEquals(
            listOf("newer", "older"),
            run(TransactionQuery(sort = TransactionSort.NEWEST_FIRST)).items.map { it.id },
        )
        assertEquals(
            listOf("older", "newer"),
            run(TransactionQuery(sort = TransactionSort.OLDEST_FIRST)).items.map { it.id },
        )
    }

    @Test
    fun `totals net income against expense`() = runTest {
        repository.save(transaction("a", 200_000))
        repository.save(transaction("b", 280_000))
        repository.save(
            transaction(
                "income", 1_000_000,
                categoryId = "cat-salary", type = TransactionType.INCOME,
            ),
        )

        val totals = run(TransactionQuery()).totals

        assertEquals(1_000_000L, totals.income.amountMinor)
        assertEquals(480_000L, totals.expense.amountMinor)
        assertEquals(520_000L, totals.net.amountMinor)
        assertEquals("PKR", totals.net.currencyCode)
        assertEquals(3, totals.count)
    }

    @Test
    fun `groups by day with relative labels, newest day first`() = runTest {
        repository.save(transaction("today", 100, date = today))
        repository.save(transaction("yesterday", 100, date = today.minusDays(1)))
        repository.save(transaction("older", 100, date = today.minusDays(5)))

        val sections = run(TransactionQuery()).sections

        assertEquals(3, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals("Yesterday", sections[1].label)
        assertEquals(listOf("today"), sections[0].items.map { it.id })
    }

    @Test
    fun `a day's net nets income against expense in base currency`() = runTest {
        repository.save(transaction("spend", 200_000))
        repository.save(
            transaction(
                "earn", 500_000,
                categoryId = "cat-salary", type = TransactionType.INCOME,
            ),
        )

        val section = run(TransactionQuery()).sections.single()
        assertEquals(300_000L, section.netMinor)
    }

    @Test
    fun `amount sorts collapse to a single unlabelled section`() = runTest {
        repository.save(transaction("a", 100, date = today))
        repository.save(transaction("b", 200, date = today.minusDays(4)))

        // Day headers scattered through an amount-sorted list would be noise.
        val sections = run(TransactionQuery(sort = TransactionSort.AMOUNT_HIGH_FIRST)).sections
        assertEquals(1, sections.size)
        assertEquals(null, sections.single().date)
    }

    @Test
    fun `the period filter spans shards and excludes what falls outside`() = runTest {
        repository.save(transaction("thismonth", 100, date = LocalDate.of(2026, 9, 4)))
        repository.save(transaction("lastmonth", 100, date = LocalDate.of(2026, 8, 20)))

        assertEquals(
            listOf("thismonth"),
            run(TransactionQuery(period = PeriodFilter.ThisMonth)).items.map { it.id },
        )
        assertEquals(
            listOf("lastmonth"),
            run(TransactionQuery(period = PeriodFilter.LastMonth)).items.map { it.id },
        )
        assertEquals(
            setOf("thismonth", "lastmonth"),
            run(TransactionQuery(period = PeriodFilter.AllTime)).items.map { it.id }.toSet(),
        )
    }

    @Test
    fun `a transaction whose category was deleted still renders`() = runTest {
        repository.save(transaction("orphan", 100, categoryId = "cat-gone"))

        val item = run(TransactionQuery()).items.single()

        // Dropping the row would silently remove money from every total.
        assertEquals("Uncategorised", item.title)
        assertEquals(null, item.category)
    }

    @Test
    fun `an unknown currency falls back instead of throwing`() = runTest {
        repository.save(transaction("odd", 100, currencyCode = "XXX"))

        val result = run(TransactionQuery())
        assertEquals("XXX", result.items.single().currency.code)
        assertEquals(1, result.totals.count)
    }
}
