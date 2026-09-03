package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeBudgetRepository
import com.amteen.paisa.testing.FakeCategoryRepository
import com.amteen.paisa.testing.FakePaymentMethodRepository
import com.amteen.paisa.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Archive-don't-delete, from both ends: a record that nothing points at may go for
 * good, and one that something points at must not. See CLAUDE.md rule 4.
 */
class RemovalUseCasesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var transactions: FileTransactionRepositoryImpl
    private lateinit var categories: FakeCategoryRepository
    private lateinit var budgets: FakeBudgetRepository
    private lateinit var paymentMethods: FakePaymentMethodRepository
    private lateinit var settings: FakeSettingsRepository

    private lateinit var countCategoryReferences: CountCategoryReferencesUseCase
    private lateinit var deleteCategory: DeleteCategoryUseCase
    private lateinit var archiveCategory: ArchiveCategoryUseCase
    private lateinit var deletePaymentMethod: DeletePaymentMethodUseCase
    private lateinit var archivePaymentMethod: ArchivePaymentMethodUseCase
    private lateinit var setDefaultPaymentMethod: SetDefaultPaymentMethodUseCase

    private var counter = 0

    @Before
    fun setUp() {
        transactions = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        categories = FakeCategoryRepository(listOf(category("cat-food", "Food")))
        budgets = FakeBudgetRepository()
        paymentMethods = FakePaymentMethodRepository(
            listOf(PaymentMethod("pm-cash", "Cash", "cash", 0)),
        )
        settings = FakeSettingsRepository()
        counter = 0

        countCategoryReferences = CountCategoryReferencesUseCase(transactions, budgets)
        deleteCategory = DeleteCategoryUseCase(categories, countCategoryReferences)
        archiveCategory = ArchiveCategoryUseCase(categories)
        deletePaymentMethod = DeletePaymentMethodUseCase(paymentMethods, transactions, settings)
        archivePaymentMethod = ArchivePaymentMethodUseCase(paymentMethods, settings)
        setDefaultPaymentMethod = SetDefaultPaymentMethodUseCase(settings)
    }

    private suspend fun givenTransaction(
        categoryId: String = "cat-food",
        paymentMethodId: String? = "pm-cash",
    ) {
        transactions.save(
            Transaction(
                id = "txn-${counter++}",
                type = TransactionType.EXPENSE,
                amountMinor = 50_000,
                currencyCode = "PKR",
                categoryId = categoryId,
                subcategoryId = null,
                description = "Lunch",
                date = LocalDate.of(2026, 9, 3),
                time = LocalTime.NOON,
                paymentMethodId = paymentMethodId,
                notes = null,
                createdAt = Instant.parse("2026-09-03T09:00:00Z"),
                updatedAt = Instant.parse("2026-09-03T09:00:00Z"),
            ),
        )
    }

    private fun outcome(result: AppResult<RemovalOutcome>): RemovalOutcome =
        (result as AppResult.Ok).value

    // -- Categories ---------------------------------------------------------

    @Test
    fun `an unreferenced category is deleted for good`() = runTest {
        assertEquals(RemovalOutcome.Deleted, outcome(deleteCategory("cat-food")))
        assertNull(categories.getById("cat-food"))
    }

    @Test
    fun `a category used by a transaction is blocked, not deleted`() = runTest {
        givenTransaction(categoryId = "cat-food")

        val result = outcome(deleteCategory("cat-food"))

        assertTrue(result is RemovalOutcome.Blocked)
        assertEquals(1, (result as RemovalOutcome.Blocked).references.transactions)
        assertNotNull("the category must survive a blocked delete", categories.getById("cat-food"))
    }

    /**
     * Budgets reference categories too. Counting only transactions is the easy
     * version of this check, and it leaves a budget pointing at nothing.
     */
    @Test
    fun `a category used only by a budget is still blocked`() = runTest {
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-food", limitMinor = 2_000_000, currencyCode = "PKR"),
        )

        val result = outcome(deleteCategory("cat-food"))

        assertTrue("a budget alone must block the delete", result is RemovalOutcome.Blocked)
        val references = (result as RemovalOutcome.Blocked).references
        assertEquals(0, references.transactions)
        assertEquals(1, references.budgets)
        assertNotNull(categories.getById("cat-food"))
    }

    @Test
    fun `both kinds of reference are counted and described`() = runTest {
        givenTransaction(categoryId = "cat-food")
        givenTransaction(categoryId = "cat-food")
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-food", limitMinor = 2_000_000, currencyCode = "PKR"),
        )

        val references = countCategoryReferences("cat-food")

        assertEquals(2, references.transactions)
        assertEquals(1, references.budgets)
        assertEquals(3, references.total)
        assertEquals("2 transactions and 1 budget", references.describe())
    }

    @Test
    fun `a budget for a different category does not block`() = runTest {
        budgets.upsert(
            Budget(id = "b1", categoryId = "cat-other", limitMinor = 1_000, currencyCode = "PKR"),
        )

        assertEquals(RemovalOutcome.Deleted, outcome(deleteCategory("cat-food")))
    }

    @Test
    fun `archiving hides a category without removing it`() = runTest {
        assertEquals(RemovalOutcome.Archived, outcome(archiveCategory("cat-food", archived = true)))

        val archived = categories.getById("cat-food")
        assertNotNull(archived)
        assertTrue(archived!!.archived)
    }

    @Test
    fun `an archived category can be restored`() = runTest {
        archiveCategory("cat-food", archived = true)
        archiveCategory("cat-food", archived = false)

        assertFalse(categories.getById("cat-food")!!.archived)
    }

    // -- Payment methods ----------------------------------------------------

    @Test
    fun `an unreferenced payment method is deleted`() = runTest {
        assertEquals(RemovalOutcome.Deleted, outcome(deletePaymentMethod("pm-cash")))
        assertNull(paymentMethods.getById("pm-cash"))
    }

    @Test
    fun `a payment method used by a transaction is blocked`() = runTest {
        givenTransaction(paymentMethodId = "pm-cash")

        val result = outcome(deletePaymentMethod("pm-cash"))

        assertTrue(result is RemovalOutcome.Blocked)
        assertNotNull(paymentMethods.getById("pm-cash"))
    }

    /**
     * A default pointing at a deleted method would silently pre-select nothing on
     * the add screen, with no way for the user to see why.
     */
    @Test
    fun `deleting the default payment method clears it from settings`() = runTest {
        setDefaultPaymentMethod("pm-cash")
        assertEquals("pm-cash", settings.settings.value.defaultPaymentMethodId)

        deletePaymentMethod("pm-cash")

        assertNull(settings.settings.value.defaultPaymentMethodId)
    }

    @Test
    fun `archiving the default payment method also clears it`() = runTest {
        setDefaultPaymentMethod("pm-cash")

        archivePaymentMethod("pm-cash", archived = true)

        assertNull(settings.settings.value.defaultPaymentMethodId)
        assertTrue(paymentMethods.getById("pm-cash")!!.archived)
    }

    @Test
    fun `restoring an archived payment method does not silently make it default again`() = runTest {
        setDefaultPaymentMethod("pm-cash")
        archivePaymentMethod("pm-cash", archived = true)

        archivePaymentMethod("pm-cash", archived = false)

        assertFalse(paymentMethods.getById("pm-cash")!!.archived)
        assertNull(settings.settings.value.defaultPaymentMethodId)
    }

    @Test
    fun `removing a different payment method leaves the default alone`() = runTest {
        paymentMethods.upsert(PaymentMethod("pm-card", "Debit Card", "card", 1))
        setDefaultPaymentMethod("pm-cash")

        deletePaymentMethod("pm-card")

        assertEquals("pm-cash", settings.settings.value.defaultPaymentMethodId)
    }

    @Test
    fun `a blocked payment method delete leaves the default in place`() = runTest {
        setDefaultPaymentMethod("pm-cash")
        givenTransaction(paymentMethodId = "pm-cash")

        val result = outcome(deletePaymentMethod("pm-cash"))

        assertTrue(result is RemovalOutcome.Blocked)
        assertEquals(
            "nothing was removed, so the default must survive",
            "pm-cash",
            settings.settings.value.defaultPaymentMethodId,
        )
    }

    @Test
    fun `the default can be cleared on its own`() = runTest {
        setDefaultPaymentMethod("pm-cash")
        setDefaultPaymentMethod(null)

        assertNull(settings.settings.value.defaultPaymentMethodId)
        assertNotNull("clearing the default must not remove the method", paymentMethods.getById("pm-cash"))
    }

    @Test
    fun `settings start with no default`() {
        assertNull(AppSettings().defaultPaymentMethodId)
    }

    private fun category(id: String, name: String) = Category(
        id = id,
        name = name,
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0xFFEF6C00.toInt(),
    )
}
