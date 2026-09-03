package com.amteen.paisa.data.repository

import com.amteen.paisa.core.time.DateRange
import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Month-shard bookkeeping.
 *
 * The cross-month edit is the bug CLAUDE.md rule 3 exists to prevent: if an edit
 * only writes the new shard, the record stays behind in the old one too, and two
 * months of totals are silently wrong.
 */
class FileTransactionRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var repository: FileTransactionRepositoryImpl

    @Before
    fun setUp() {
        root = temp.newFolder("app-data")
        repository = FileTransactionRepositoryImpl(JsonFileStore(root))
    }

    private fun transaction(
        id: String,
        date: LocalDate,
        amountMinor: Long = 80_000,
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = "PKR",
        categoryId = "cat-food",
        description = "Burger",
        date = date,
        time = LocalTime.of(13, 30),
        createdAt = Instant.parse("2026-09-01T10:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T10:00:00Z"),
    )

    private fun shard(month: String) = File(root, "${FilePaths.TRANSACTIONS_DIR}/$month.json")

    @Test
    fun `a save writes only the affected month's shard`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 9, 2)))
        repository.save(transaction("b", LocalDate.of(2026, 8, 15)))

        assertTrue(shard("2026-09").exists())
        assertTrue(shard("2026-08").exists())
        assertFalse(shard("2026-07").exists())

        assertEquals(1, repository.getMonth(YearMonth.of(2026, 9)).size)
        assertEquals(1, repository.getMonth(YearMonth.of(2026, 8)).size)
    }

    @Test
    fun `editing a date across a month boundary moves the record between shards`() = runTest {
        val original = transaction("a", LocalDate.of(2026, 9, 2))
        repository.save(original)
        repository.save(transaction("b", LocalDate.of(2026, 9, 20)))

        // Move it back into August.
        repository.save(original.copy(date = LocalDate.of(2026, 8, 30)))

        val september = repository.getMonth(YearMonth.of(2026, 9))
        val august = repository.getMonth(YearMonth.of(2026, 8))

        assertEquals(listOf("b"), september.map { it.id })
        assertEquals(listOf("a"), august.map { it.id })

        // And exactly once overall — no duplicate left behind.
        assertEquals(1, repository.getAll().count { it.id == "a" })
    }

    @Test
    fun `a cross-month move survives a restart`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 9, 2)))
        repository.save(transaction("a", LocalDate.of(2026, 8, 30)))

        // A fresh repository reads only what actually reached disk.
        val reopened = FileTransactionRepositoryImpl(JsonFileStore(root))

        assertEquals(emptyList<String>(), reopened.getMonth(YearMonth.of(2026, 9)).map { it.id })
        assertEquals(listOf("a"), reopened.getMonth(YearMonth.of(2026, 8)).map { it.id })
        assertEquals(1, reopened.getAll().size)
    }

    @Test
    fun `editing within the same month replaces rather than duplicates`() = runTest {
        val original = transaction("a", LocalDate.of(2026, 9, 2))
        repository.save(original)
        repository.save(original.copy(amountMinor = 95_000, description = "Pizza"))

        val september = repository.getMonth(YearMonth.of(2026, 9))
        assertEquals(1, september.size)
        assertEquals(95_000L, september.first().amountMinor)
    }

    @Test
    fun `deleting the last transaction removes the shard file`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 9, 2)))
        assertTrue(shard("2026-09").exists())

        repository.delete("a")

        // An empty shard for every month ever touched is just litter.
        assertFalse(shard("2026-09").exists())
        assertEquals(emptyList<Transaction>(), repository.getMonth(YearMonth.of(2026, 9)))
    }

    @Test
    fun `deleting one of several leaves the rest intact`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 9, 2)))
        repository.save(transaction("b", LocalDate.of(2026, 9, 5)))

        repository.delete("a")

        assertEquals(listOf("b"), repository.getMonth(YearMonth.of(2026, 9)).map { it.id })
    }

    @Test
    fun `a range query spans shards and excludes days outside it`() = runTest {
        repository.save(transaction("jul", LocalDate.of(2026, 7, 31)))
        repository.save(transaction("aug", LocalDate.of(2026, 8, 15)))
        repository.save(transaction("sep", LocalDate.of(2026, 9, 2)))
        repository.save(transaction("oct", LocalDate.of(2026, 10, 1)))

        val result = repository.getRange(
            DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30)),
        )

        assertEquals(setOf("aug", "sep"), result.map { it.id }.toSet())
    }

    @Test
    fun `available months are listed newest first`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 7, 1)))
        repository.save(transaction("b", LocalDate.of(2026, 9, 1)))
        repository.save(transaction("c", LocalDate.of(2026, 8, 1)))

        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7)),
            repository.availableMonths(),
        )
    }

    @Test
    fun `reference counts see every shard`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 7, 1)))
        repository.save(transaction("b", LocalDate.of(2026, 9, 1)))

        val fresh = FileTransactionRepositoryImpl(JsonFileStore(root))
        assertEquals(2, fresh.countByCategory("cat-food"))
        assertEquals(0, fresh.countByCategory("cat-transport"))
    }

    @Test
    fun `saveAll groups writes and still moves records between months`() = runTest {
        repository.save(transaction("a", LocalDate.of(2026, 9, 2)))
        repository.save(transaction("b", LocalDate.of(2026, 9, 3)))

        repository.saveAll(
            listOf(
                transaction("a", LocalDate.of(2026, 10, 1)),
                transaction("c", LocalDate.of(2026, 10, 2)),
            ),
        )

        assertEquals(listOf("b"), repository.getMonth(YearMonth.of(2026, 9)).map { it.id })
        assertEquals(setOf("a", "c"), repository.getMonth(YearMonth.of(2026, 10)).map { it.id }.toSet())
        assertEquals(3, repository.getAll().size)
    }

    @Test
    fun `replaceAll drops months the new data does not cover`() = runTest {
        repository.save(transaction("old", LocalDate.of(2025, 1, 5)))
        repository.save(transaction("keep", LocalDate.of(2026, 9, 5)))

        repository.replaceAll(listOf(transaction("fresh", LocalDate.of(2026, 9, 9))))

        assertFalse(shard("2025-01").exists())
        assertEquals(listOf("fresh"), repository.getAll().map { it.id })
    }

    @Test
    fun `a record filed under the wrong month is re-homed, not dropped`() = runTest {
        // Hand-written file with an October date sitting in the September shard —
        // possible after a bad import or a manual edit.
        File(root, FilePaths.TRANSACTIONS_DIR).mkdirs()
        shard("2026-09").writeText(
            """
            {
              "schemaVersion": 1,
              "month": "2026-09",
              "transactions": [
                { "id": "strays", "type": "EXPENSE", "amountMinor": 5000,
                  "currencyCode": "PKR", "categoryId": "cat-food", "date": "2026-10-04" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), repository.getMonth(YearMonth.of(2026, 9)).map { it.id })
        assertEquals(listOf("strays"), repository.getMonth(YearMonth.of(2026, 10)).map { it.id })
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `a transaction with an unparseable date is skipped rather than crashing`() = runTest {
        File(root, FilePaths.TRANSACTIONS_DIR).mkdirs()
        shard("2026-09").writeText(
            """
            {
              "schemaVersion": 1,
              "month": "2026-09",
              "transactions": [
                { "id": "bad", "type": "EXPENSE", "amountMinor": 100,
                  "currencyCode": "PKR", "categoryId": "cat-food", "date": "not-a-date" },
                { "id": "good", "type": "EXPENSE", "amountMinor": 200,
                  "currencyCode": "PKR", "categoryId": "cat-food", "date": "2026-09-04" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("good"), repository.getMonth(YearMonth.of(2026, 9)).map { it.id })
    }
}
