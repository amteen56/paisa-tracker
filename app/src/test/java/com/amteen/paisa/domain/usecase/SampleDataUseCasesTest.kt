package com.amteen.paisa.domain.usecase

import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.data.seed.SampleData
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeBudgetRepository
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
import java.time.YearMonth

/**
 * The sample-data seeder.
 *
 * Worth testing despite being a convenience: it writes to the real ledger, and both
 * of its promises — "adds plausible data" and "takes only its own data back out" —
 * are the kind that quietly stop being true.
 */
class SampleDataUseCasesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val today = LocalDate.of(2026, 9, 15)
    private val september = YearMonth.of(2026, 9)

    private lateinit var transactions: FileTransactionRepositoryImpl
    private lateinit var budgets: FakeBudgetRepository
    private lateinit var seed: SeedSampleDataUseCase
    private lateinit var clear: ClearSampleDataUseCase

    @Before
    fun setUp() {
        transactions = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder()))
        budgets = FakeBudgetRepository()
        seed = SeedSampleDataUseCase(transactions, budgets)
        clear = ClearSampleDataUseCase(transactions, budgets)
    }

    private fun real(id: String, date: LocalDate = today) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 12_345,
        currencyCode = "PKR",
        categoryId = "cat-food",
        description = "Something I actually bought",
        date = date,
        time = LocalTime.NOON,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    // -- The generator -------------------------------------------------------

    @Test
    fun `the generator is deterministic`() {
        val first = SampleData.transactions(months = 3, endMonth = september, today = today)
        val second = SampleData.transactions(months = 3, endMonth = september, today = today)

        // A screenshot has to be repeatable, and "the average looks wrong" is only
        // actionable if the data can be regenerated exactly.
        assertEquals(first.size, second.size)
        assertEquals(first.map { it.id to it.amountMinor }, second.map { it.id to it.amountMinor })
        assertEquals(first.map { it.date }, second.map { it.date })
    }

    @Test
    fun `nothing is dated in the future`() {
        val generated = SampleData.transactions(months = 3, endMonth = september, today = today)

        // A forward-dated record skews the dashboard's rolling average and reads as
        // a bug rather than as sample data.
        assertTrue(generated.isNotEmpty())
        assertTrue(generated.none { it.date.isAfter(today) })
    }

    @Test
    fun `every amount is positive PKR minor units`() {
        val generated = SampleData.transactions(months = 6, endMonth = september, today = today)

        // amountMinor is always positive; direction comes from `type`.
        assertTrue(generated.all { it.amountMinor > 0L })
        assertTrue(generated.all { it.currencyCode == "PKR" })
    }

    @Test
    fun `the shape is varied enough for the charts to differ`() {
        val generated = SampleData.transactions(months = 3, endMonth = september, today = today)

        // Income and expense both present, or the trend chart has one flat line.
        assertTrue(generated.any { it.type.isIncome })
        assertTrue(generated.any { it.type.isExpense })

        // More than one category, or the donut is a single ring.
        assertTrue(generated.map { it.categoryId }.distinct().size >= 4)

        // Some days empty, so the calendar has gaps to show.
        val daysCovered = generated.map { it.date }.distinct().size
        assertTrue(daysCovered < generated.size)

        // A real spread of amounts, so "biggest expenses" is not ten identical rows.
        val expenses = generated.filter { it.type.isExpense }.map { it.amountMinor }
        assertTrue(expenses.max() > expenses.min() * 10)
    }

    @Test
    fun `every id carries the sample prefix`() {
        val generated = SampleData.transactions(months = 2, endMonth = september, today = today)
        // This prefix is the only thing that makes removal able to be surgical.
        assertTrue(generated.all { it.id.startsWith(SampleData.ID_PREFIX) })
    }

    // -- Seeding -------------------------------------------------------------

    @Test
    fun `seeding adds transactions and budgets`() = runTest {
        val added = (seed() as com.amteen.paisa.core.result.AppResult.Ok).value

        assertTrue(added > 0)
        assertEquals(added, transactions.getAll().size)
        assertEquals(SampleData.budgets().size, budgets.budgets.value.size)
    }

    @Test
    fun `seeding twice does not double the data`() = runTest {
        seed()
        val afterFirst = transactions.getAll().size

        seed()

        // Ids are stable, so the second pass finds everything already present.
        assertEquals(afterFirst, transactions.getAll().size)
        assertEquals(SampleData.budgets().size, budgets.budgets.value.size)
    }

    @Test
    fun `seeding leaves what the user recorded alone`() = runTest {
        transactions.save(real("mine"))

        seed()

        // Merging rather than replacing: wiping someone's real entries to make room
        // for fake ones would be indefensible.
        assertEquals("Something I actually bought", transactions.getById("mine")?.description)
    }

    // -- Clearing ------------------------------------------------------------

    @Test
    fun `clearing removes only the sample records`() = runTest {
        transactions.save(real("mine-1"))
        transactions.save(real("mine-2", date = today.minusMonths(2)))
        seed()

        val removed = (clear() as com.amteen.paisa.core.result.AppResult.Ok).value

        assertTrue(removed > 0)
        // Anything else would make "add sample data" a one-way door.
        assertEquals(setOf("mine-1", "mine-2"), transactions.getAll().map { it.id }.toSet())
        assertTrue(budgets.budgets.value.isEmpty())
    }

    @Test
    fun `clearing leaves a user's own budgets alone`() = runTest {
        val mine = SampleData.budgets().first().copy(id = "my-budget")
        budgets.upsert(mine)
        seed()

        clear()

        assertEquals(listOf("my-budget"), budgets.budgets.value.map { it.id })
    }

    @Test
    fun `clearing when there is no sample data is harmless`() = runTest {
        transactions.save(real("mine"))

        val removed = (clear() as com.amteen.paisa.core.result.AppResult.Ok).value

        assertEquals(0, removed)
        assertEquals(listOf("mine"), transactions.getAll().map { it.id })
    }

    @Test
    fun `a seed and clear round trip returns the ledger to exactly where it was`() = runTest {
        transactions.save(real("mine"))
        val before = transactions.getAll()

        seed()
        clear()

        val after = transactions.getAll()
        assertEquals(before, after)
        // And the shard for a month that only ever held sample data is gone rather
        // than left behind empty.
        assertFalse(transactions.availableMonths().isEmpty())
    }
}
