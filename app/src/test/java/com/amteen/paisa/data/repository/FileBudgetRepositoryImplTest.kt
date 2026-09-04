package com.amteen.paisa.data.repository

import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.domain.model.Budget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.YearMonth

/**
 * Budget ordering.
 *
 * `sortOrder` was added after budgets shipped, so the case that matters most is a
 * file written before the field existed: it has to keep parsing and land somewhere
 * sensible rather than throwing or losing records.
 */
class FileBudgetRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File

    private fun repository(): FileBudgetRepositoryImpl {
        root = temp.newFolder()
        return FileBudgetRepositoryImpl(JsonFileStore(root))
    }

    /** A second instance over the same directory — proves a write reached disk. */
    private fun reopened() = FileBudgetRepositoryImpl(JsonFileStore(root))

    private fun writeBudgetsFile(json: String) {
        val file = File(root, FilePaths.BUDGETS)
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    private fun budget(id: String, sortOrder: Int = 0) = Budget(
        id = id,
        categoryId = "cat-$id",
        limitMinor = 100_000,
        currencyCode = "PKR",
        period = null,
        sortOrder = sortOrder,
    )

    // -- Ordering ------------------------------------------------------------

    @Test
    fun `budgets come back in sortOrder`() = runTest {
        val repository = repository()
        // Non-zero on purpose: sortOrder 0 is the "append me" signal that upsert
        // interprets, so it cannot double as an explicit first position.
        repository.upsert(budget("c", sortOrder = 3))
        repository.upsert(budget("a", sortOrder = 1))
        repository.upsert(budget("b", sortOrder = 2))

        assertEquals(listOf("a", "b", "c"), repository.budgets.value.map { it.id })
    }

    @Test
    fun `reorder rewrites positions and survives a reload`() = runTest {
        val repository = repository()
        repository.upsert(budget("a"))
        repository.upsert(budget("b"))
        repository.upsert(budget("c"))

        repository.reorder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), repository.budgets.value.map { it.id })

        // The order is only real if it is on disk.
        val reopened = reopened()
        reopened.load()
        assertEquals(listOf("c", "a", "b"), reopened.budgets.value.map { it.id })
    }

    @Test
    fun `a new budget goes to the end rather than to the top`() = runTest {
        val repository = repository()
        repository.upsert(budget("a"))
        repository.upsert(budget("b"))
        repository.reorder(listOf("b", "a"))

        repository.upsert(budget("c"))

        // Jumping to the top of an order the user arranged deliberately would be
        // the app overriding a choice they just made.
        assertEquals(listOf("b", "a", "c"), repository.budgets.value.map { it.id })
    }

    @Test
    fun `a budget left out of the reorder keeps the position it had`() = runTest {
        val repository = repository()
        repository.upsert(budget("live-1"))
        repository.upsert(budget("live-2"))
        repository.upsert(budget("archived"))
        repository.archive("archived", archived = true)

        // The screen only reorders the live section, so the archived one is absent
        // from the list handed in and must not be disturbed.
        repository.reorder(listOf("live-2", "live-1"))

        val byId = repository.budgets.value.associateBy { it.id }
        assertEquals(0, byId.getValue("live-2").sortOrder)
        assertEquals(1, byId.getValue("live-1").sortOrder)
        assertEquals(2, byId.getValue("archived").sortOrder)
    }

    @Test
    fun `ties fall back to the id so the order is stable`() = runTest {
        val repository = repository()
        // Everything a pre-sortOrder file produces: all zeros.
        repository.upsert(budget("zeta", sortOrder = 0).copy(sortOrder = 0))
        repository.upsert(budget("alpha", sortOrder = 0).copy(sortOrder = 0))

        val first = repository.budgets.value.map { it.id }
        val second = reopened().also { it.load() }.budgets.value.map { it.id }

        // The same list twice, rather than reshuffling between reads.
        assertEquals(first, second)
    }

    // -- Backwards compatibility ---------------------------------------------

    @Test
    fun `a file written before sortOrder existed still parses`() = runTest {
        val repository = repository()
        // Exactly what an older build wrote: no sortOrder key at all.
        writeBudgetsFile(
            """
            {
              "schemaVersion": 1,
              "budgets": [
                {"id":"b1","categoryId":"cat-food","limitMinor":300000,"currencyCode":"PKR"},
                {"id":"b2","categoryId":"cat-transport","limitMinor":200000,"currencyCode":"PKR","period":"2026-09"}
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        // Adding a defaulted field is not a schema bump — the old file has to keep
        // working, and both records must survive.
        val budgets = repository.budgets.value
        assertEquals(2, budgets.size)
        assertEquals(0, budgets.first { it.id == "b1" }.sortOrder)
        assertEquals(0, budgets.first { it.id == "b2" }.sortOrder)
        assertEquals(YearMonth.of(2026, 9), budgets.first { it.id == "b2" }.period)
    }

    @Test
    fun `an old file can be reordered and the new order persists`() = runTest {
        val repository = repository()
        writeBudgetsFile(
            """
            {
              "schemaVersion": 1,
              "budgets": [
                {"id":"b1","categoryId":"c1","limitMinor":1,"currencyCode":"PKR"},
                {"id":"b2","categoryId":"c2","limitMinor":1,"currencyCode":"PKR"}
              ]
            }
            """.trimIndent(),
        )
        repository.load()

        repository.reorder(listOf("b2", "b1"))

        val reopened = reopened()
        reopened.load()
        assertEquals(listOf("b2", "b1"), reopened.budgets.value.map { it.id })
    }
}
