package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.testing.FakeCategoryRepository
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
 * The category editor's rules.
 *
 * The transaction repository here is the **real** file-backed one against a temp
 * directory, not a fake, because the behaviour under test is precisely "does
 * anything still point at this subcategory" — a fake that always answers zero would
 * make every one of these tests pass while the app silently deleted referenced data.
 */
class SaveCategoryUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var transactions: FileTransactionRepositoryImpl
    private lateinit var categories: FakeCategoryRepository
    private lateinit var save: SaveCategoryUseCase

    private var idCounter = 0

    @Before
    fun setUp() {
        transactions = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        categories = FakeCategoryRepository()
        idCounter = 0
        save = SaveCategoryUseCase(
            categories = categories,
            transactions = transactions,
            newId = { "generated-${idCounter++}" },
        )
    }

    private fun input(
        id: String? = null,
        name: String = "Food",
        scope: CategoryScope = CategoryScope.EXPENSE,
        subcategories: List<SubcategoryInput> = emptyList(),
    ) = CategoryInput(
        id = id,
        name = name,
        applicableTo = scope,
        iconKey = "restaurant",
        colorArgb = 0xFFEF6C00.toInt(),
        subcategories = subcategories,
    )

    private suspend fun givenTransaction(categoryId: String, subcategoryId: String?) {
        transactions.save(
            Transaction(
                id = "txn-${idCounter++}",
                type = TransactionType.EXPENSE,
                amountMinor = 50_000,
                currencyCode = "PKR",
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                description = "Lunch",
                date = LocalDate.of(2026, 9, 3),
                time = LocalTime.NOON,
                paymentMethodId = "pm-cash",
                notes = null,
                createdAt = Instant.parse("2026-09-03T09:00:00Z"),
                updatedAt = Instant.parse("2026-09-03T09:00:00Z"),
            ),
        )
    }

    private fun validationError(result: AppResult<*>): AppError.Validation {
        val error = (result as AppResult.Err).error
        assertTrue("expected a validation error, got $error", error is AppError.Validation)
        return error as AppError.Validation
    }

    // -- Validation ---------------------------------------------------------

    @Test
    fun `a blank name is rejected`() = runTest {
        val error = validationError(save(input(name = "   ")))
        assertEquals(SaveCategoryUseCase.FIELD_NAME, error.field)
    }

    @Test
    fun `a name longer than the limit is rejected`() = runTest {
        val error = validationError(save(input(name = "x".repeat(41))))
        assertEquals(SaveCategoryUseCase.FIELD_NAME, error.field)
    }

    @Test
    fun `a duplicate name is rejected regardless of case`() = runTest {
        categories.upsert(category(id = "existing", name = "Food"))

        val error = validationError(save(input(name = "  fOOd ")))
        assertEquals(SaveCategoryUseCase.FIELD_NAME, error.field)
    }

    @Test
    fun `a clash with an archived category points the user at restoring it`() = runTest {
        categories.upsert(category(id = "existing", name = "Food").copy(archived = true))

        val error = validationError(save(input(name = "Food")))
        assertTrue(
            "message should mention restoring, was: ${error.message}",
            error.message.contains("archived") && error.message.contains("Restore"),
        )
    }

    @Test
    fun `renaming a category does not clash with itself`() = runTest {
        categories.upsert(category(id = "cat-1", name = "Food"))

        val result = save(input(id = "cat-1", name = "Food & Drink"))

        assertTrue(result is AppResult.Ok)
        assertEquals("Food & Drink", categories.getById("cat-1")?.name)
    }

    @Test
    fun `a blank subcategory row is rejected and names the row`() = runTest {
        val result = save(
            input(
                subcategories = listOf(
                    SubcategoryInput(name = "Groceries"),
                    SubcategoryInput(name = "  "),
                ),
            ),
        )

        val error = validationError(result)
        assertEquals(1, SaveCategoryUseCase.subcategoryIndex(error.field))
    }

    @Test
    fun `duplicate subcategory names within one category are rejected`() = runTest {
        val result = save(
            input(
                subcategories = listOf(
                    SubcategoryInput(name = "Groceries"),
                    SubcategoryInput(name = "groceries"),
                ),
            ),
        )

        assertEquals(1, SaveCategoryUseCase.subcategoryIndex(validationError(result).field))
    }

    @Test
    fun `nothing is written when validation fails`() = runTest {
        save(input(name = ""))
        assertTrue(categories.categories.value.isEmpty())
    }

    // -- Subcategory reconciliation ----------------------------------------

    @Test
    fun `new subcategories get ids and existing ones keep theirs`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(
                subcategories = listOf(Subcategory("sub-keep", "Groceries", 0)),
            ),
        )

        save(
            input(
                id = "cat-1",
                subcategories = listOf(
                    SubcategoryInput(id = "sub-keep", name = "Groceries"),
                    SubcategoryInput(id = null, name = "Restaurants"),
                ),
            ),
        )

        val saved = categories.getById("cat-1")!!
        assertEquals(listOf("sub-keep", "generated-0"), saved.subcategories.map { it.id })
        assertEquals(listOf(0, 1), saved.subcategories.map { it.sortOrder })
    }

    /**
     * The rule that matters. Removing a referenced subcategory in the editor must
     * archive it, or every transaction pointing at it renders a blank.
     */
    @Test
    fun `removing a referenced subcategory archives it instead of deleting it`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(
                subcategories = listOf(
                    Subcategory("sub-groceries", "Groceries", 0),
                    Subcategory("sub-restaurants", "Restaurants", 1),
                ),
            ),
        )
        givenTransaction(categoryId = "cat-1", subcategoryId = "sub-restaurants")

        // The editor submits only Groceries — the user deleted the other row.
        save(
            input(
                id = "cat-1",
                subcategories = listOf(SubcategoryInput(id = "sub-groceries", name = "Groceries")),
            ),
        )

        val saved = categories.getById("cat-1")!!
        val restaurants = saved.subcategories.firstOrNull { it.id == "sub-restaurants" }
        assertNotNull("a referenced subcategory must survive", restaurants)
        assertTrue("and it must be archived", restaurants!!.archived)
        // It is gone from the picker but still resolvable by history.
        assertEquals(listOf("sub-groceries"), saved.activeSubcategories.map { it.id })
    }

    @Test
    fun `removing an unreferenced subcategory really deletes it`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(
                subcategories = listOf(
                    Subcategory("sub-groceries", "Groceries", 0),
                    Subcategory("sub-unused", "Never Used", 1),
                ),
            ),
        )

        save(
            input(
                id = "cat-1",
                subcategories = listOf(SubcategoryInput(id = "sub-groceries", name = "Groceries")),
            ),
        )

        val saved = categories.getById("cat-1")!!
        assertEquals(listOf("sub-groceries"), saved.subcategories.map { it.id })
    }

    @Test
    fun `a transaction in another category does not keep a subcategory alive`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(
                subcategories = listOf(Subcategory("sub-unused", "Never Used", 0)),
            ),
        )
        // Same *category*, different subcategory: must not count as a reference.
        givenTransaction(categoryId = "cat-1", subcategoryId = null)

        save(input(id = "cat-1", subcategories = emptyList()))

        assertTrue(categories.getById("cat-1")!!.subcategories.isEmpty())
    }

    @Test
    fun `submitting an archived subcategory as a live row restores it`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(
                subcategories = listOf(
                    Subcategory("sub-old", "Office Canteen", 0, archived = true),
                ),
            ),
        )
        givenTransaction(categoryId = "cat-1", subcategoryId = "sub-old")

        save(
            input(
                id = "cat-1",
                subcategories = listOf(SubcategoryInput(id = "sub-old", name = "Office Canteen")),
            ),
        )

        val restored = categories.getById("cat-1")!!.subcategories.single()
        assertEquals("sub-old", restored.id)
        assertFalse(restored.archived)
    }

    @Test
    fun `archived leftovers are ordered after the live rows`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(
                subcategories = listOf(
                    Subcategory("sub-a", "A", 0),
                    Subcategory("sub-b", "B", 1),
                    Subcategory("sub-c", "C", 2),
                ),
            ),
        )
        givenTransaction(categoryId = "cat-1", subcategoryId = "sub-a")

        save(
            input(
                id = "cat-1",
                subcategories = listOf(
                    SubcategoryInput(id = "sub-b", name = "B"),
                    SubcategoryInput(id = "sub-c", name = "C"),
                ),
            ),
        )

        val saved = categories.getById("cat-1")!!.subcategories
        assertEquals(listOf("sub-b", "sub-c", "sub-a"), saved.map { it.id })
        assertEquals(listOf(0, 1, 2), saved.map { it.sortOrder })
    }

    // -- Field handling -----------------------------------------------------

    @Test
    fun `names are trimmed on the way in`() = runTest {
        save(input(name = "  Food  ", subcategories = listOf(SubcategoryInput(name = " Groceries "))))

        val saved = categories.categories.value.single()
        assertEquals("Food", saved.name)
        assertEquals("Groceries", saved.subcategories.single().name)
    }

    @Test
    fun `an edit preserves the archived flag and sort order`() = runTest {
        categories.upsert(
            category(id = "cat-1", name = "Food").copy(archived = true, sortOrder = 7),
        )

        save(input(id = "cat-1", name = "Food & Drink"))

        val saved = categories.getById("cat-1")!!
        assertTrue(saved.archived)
        assertEquals(7, saved.sortOrder)
    }

    @Test
    fun `a blank icon key falls back to the default rather than rendering nothing`() = runTest {
        val result = save(
            CategoryInput(
                name = "Food",
                applicableTo = CategoryScope.EXPENSE,
                iconKey = "",
                colorArgb = 0xFFEF6C00.toInt(),
            ),
        )

        assertTrue(result is AppResult.Ok)
        assertEquals(SaveCategoryUseCase.DEFAULT_ICON, categories.categories.value.single().iconKey)
    }

    @Test
    fun `a new category gets a generated id`() = runTest {
        save(input())

        assertEquals("generated-0", categories.categories.value.single().id)
        assertNull(categories.getById("nope"))
    }

    private fun category(id: String, name: String) = Category(
        id = id,
        name = name,
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0xFFEF6C00.toInt(),
    )
}
