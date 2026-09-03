package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.testing.FakeBudgetRepository
import com.amteen.paisa.testing.FakeCategoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

/**
 * Budget validation.
 *
 * The duplicate check is the one that earns its keep: two live budgets over the same
 * category would both be counted, both be shown, and both fire their own alerts for
 * the same spending.
 */
class SaveBudgetUseCaseTest {

    private val food = Category(
        id = "cat-food",
        name = "Food",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0,
        subcategories = listOf(Subcategory("sub-fast", "Fast Food")),
    )
    private val gifts = Category(
        id = "cat-gifts",
        name = "Gifts",
        applicableTo = CategoryScope.BOTH,
        iconKey = "gift",
        colorArgb = 0,
    )
    private val salary = Category(
        id = "cat-salary",
        name = "Salary",
        applicableTo = CategoryScope.INCOME,
        iconKey = "wallet",
        colorArgb = 0,
    )

    private lateinit var budgets: FakeBudgetRepository
    private lateinit var save: SaveBudgetUseCase
    private var nextId = 0

    @Before
    fun setUp() {
        budgets = FakeBudgetRepository()
        nextId = 0
        save = SaveBudgetUseCase(
            budgets = budgets,
            categories = FakeCategoryRepository(listOf(food, gifts, salary)),
            newId = { "generated-${nextId++}" },
        )
    }

    private fun input(
        id: String? = null,
        categoryId: String = "cat-food",
        subcategoryId: String? = null,
        limitMinor: Long = 300_000,
        currencyCode: String = "PKR",
        period: YearMonth? = null,
    ) = BudgetInput(id, categoryId, subcategoryId, limitMinor, currencyCode, period)

    private fun errorOf(result: AppResult<*>): AppError.Validation =
        result.errorOrNull() as AppError.Validation

    // -- Happy path ---------------------------------------------------------

    @Test
    fun `saves a recurring budget and generates an id`() = runTest {
        val result = save(input())

        val budget = result.valueOrNull()!!
        assertEquals("generated-0", budget.id)
        assertTrue(budget.isRecurring)
        assertEquals(300_000L, budget.limitMinor)
        assertEquals(listOf(budget), budgets.budgets.value)
    }

    @Test
    fun `editing keeps the id rather than creating a second budget`() = runTest {
        val first = save(input()).valueOrNull()!!

        val edited = save(input(id = first.id, limitMinor = 500_000)).valueOrNull()!!

        assertEquals(first.id, edited.id)
        assertEquals(500_000L, edited.limitMinor)
        assertEquals(1, budgets.budgets.value.size)
    }

    @Test
    fun `saving an archived budget brings it back`() = runTest {
        budgets.upsert(
            Budget("b1", "cat-food", limitMinor = 100_000, currencyCode = "PKR", archived = true),
        )

        val saved = save(input(id = "b1")).valueOrNull()!!

        assertFalse(saved.archived)
    }

    @Test
    fun `a budget on a BOTH category is allowed`() = runTest {
        assertTrue(save(input(categoryId = "cat-gifts")).isOk)
    }

    // -- Validation ---------------------------------------------------------

    @Test
    fun `a zero limit is rejected`() = runTest {
        val error = errorOf(save(input(limitMinor = 0)))

        assertEquals(SaveBudgetUseCase.FIELD_LIMIT, error.field)
        assertTrue(budgets.budgets.value.isEmpty())
    }

    @Test
    fun `a negative limit is rejected`() = runTest {
        assertEquals(
            SaveBudgetUseCase.FIELD_LIMIT,
            errorOf(save(input(limitMinor = -1))).field,
        )
    }

    @Test
    fun `a blank currency is rejected`() = runTest {
        assertEquals(
            SaveBudgetUseCase.FIELD_CURRENCY,
            errorOf(save(input(currencyCode = " "))).field,
        )
    }

    @Test
    fun `a category that does not exist is rejected`() = runTest {
        assertEquals(
            SaveBudgetUseCase.FIELD_CATEGORY,
            errorOf(save(input(categoryId = "cat-gone"))).field,
        )
    }

    @Test
    fun `an income-only category cannot have a spending limit`() = runTest {
        val error = errorOf(save(input(categoryId = "cat-salary")))

        assertEquals(SaveBudgetUseCase.FIELD_CATEGORY, error.field)
        assertTrue(error.message.contains("income", ignoreCase = true))
    }

    @Test
    fun `a subcategory from another category is rejected`() = runTest {
        assertEquals(
            SaveBudgetUseCase.FIELD_SUBCATEGORY,
            errorOf(save(input(categoryId = "cat-gifts", subcategoryId = "sub-fast"))).field,
        )
    }

    @Test
    fun `a subcategory of the chosen category is accepted`() = runTest {
        val budget = save(input(subcategoryId = "sub-fast")).valueOrNull()!!

        assertEquals("sub-fast", budget.subcategoryId)
    }

    // -- Duplicates ---------------------------------------------------------

    @Test
    fun `a second recurring budget on the same category is refused`() = runTest {
        save(input())

        val error = errorOf(save(input()))

        assertEquals(SaveBudgetUseCase.FIELD_CATEGORY, error.field)
        assertEquals(1, budgets.budgets.value.size)
    }

    @Test
    fun `a subcategory budget does not clash with its parent category budget`() = runTest {
        save(input())

        // "Food" and "Food · Fast Food" are different limits over different things,
        // and having both is a legitimate setup.
        assertTrue(save(input(subcategoryId = "sub-fast")).isOk)
        assertEquals(2, budgets.budgets.value.size)
    }

    @Test
    fun `budgets pinned to different months do not clash`() = runTest {
        assertTrue(save(input(period = YearMonth.of(2026, 9))).isOk)
        assertTrue(save(input(period = YearMonth.of(2026, 10))).isOk)
    }

    @Test
    fun `a pinned budget does not clash with a recurring one`() = runTest {
        // A recurring limit with a one-off override for a heavy month is exactly
        // what pinning is for.
        assertTrue(save(input()).isOk)
        assertTrue(save(input(period = YearMonth.of(2026, 12))).isOk)
    }

    @Test
    fun `editing a budget does not clash with itself`() = runTest {
        val budget = save(input()).valueOrNull()!!

        assertTrue(save(input(id = budget.id, limitMinor = 900_000)).isOk)
    }

    @Test
    fun `an archived budget does not block re-creating the same limit`() = runTest {
        val budget = save(input()).valueOrNull()!!
        budgets.archive(budget.id)

        val result = save(input())

        assertNull(result.errorOrNull())
        assertEquals(2, budgets.budgets.value.size)
    }
}
