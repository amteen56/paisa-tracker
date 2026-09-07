package com.amteen.paisa.data.repository

import com.amteen.paisa.data.file.FilePaths
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.domain.model.Loan
import com.amteen.paisa.domain.model.LoanDirection
import com.amteen.paisa.domain.model.Repayment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/**
 * The loans file.
 *
 * A loan is the one record in Paisa the user cannot reconstruct from anything else —
 * there is no transaction behind it — so losing one to a bad write or an unreadable
 * field would be losing money they are owed. Every test here reopens the store to
 * prove the write actually reached disk.
 */
class FileLoanRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File

    private fun repository(): FileLoanRepositoryImpl {
        root = temp.newFolder()
        return FileLoanRepositoryImpl(JsonFileStore(root))
    }

    /** A second instance over the same directory — proves a write reached disk. */
    private fun reopened() = FileLoanRepositoryImpl(JsonFileStore(root))

    private fun writeLoansFile(json: String) {
        val file = File(root, FilePaths.LOANS)
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    private fun loan(
        id: String,
        counterparty: String = "Ali",
        direction: LoanDirection = LoanDirection.LENT,
        principalMinor: Long = 500_000,
        date: LocalDate = LocalDate.of(2026, 9, 1),
        dueDate: LocalDate? = null,
        repayments: List<Repayment> = emptyList(),
    ) = Loan(
        id = id,
        counterparty = counterparty,
        direction = direction,
        principalMinor = principalMinor,
        currencyCode = "PKR",
        date = date,
        dueDate = dueDate,
        repayments = repayments,
    )

    @Test
    fun `a loan survives a reload`() = runTest {
        val repository = repository()
        repository.upsert(
            loan("l1", dueDate = LocalDate.of(2026, 10, 1)),
        )

        val reopened = reopened()
        reopened.load()

        val stored = reopened.loans.value.single()
        assertEquals("Ali", stored.counterparty)
        assertEquals(500_000L, stored.principalMinor)
        assertEquals(LocalDate.of(2026, 9, 1), stored.date)
        assertEquals(LocalDate.of(2026, 10, 1), stored.dueDate)
        assertEquals(LoanDirection.LENT, stored.direction)
    }

    @Test
    fun `repayments round trip with their date and payment method`() = runTest {
        val repository = repository()
        repository.upsert(
            loan(
                "l1",
                repayments = listOf(
                    Repayment("r1", 200_000, LocalDate.of(2026, 9, 5), "pm-cash", "part"),
                    Repayment("r2", 100_000, LocalDate.of(2026, 9, 9), null),
                ),
            ),
        )

        val reopened = reopened()
        reopened.load()

        val stored = reopened.loans.value.single()
        assertEquals(2, stored.repayments.size)
        assertEquals(300_000L, stored.repaidMinor)
        assertEquals(200_000L, stored.outstandingMinor)
        assertEquals("pm-cash", stored.repayments.first().paymentMethodId)
        assertEquals("part", stored.repayments.first().note)
        // A repayment with no method recorded is still a repayment.
        assertNull(stored.repayments[1].paymentMethodId)
    }

    @Test
    fun `upsert replaces rather than duplicating`() = runTest {
        val repository = repository()
        repository.upsert(loan("l1", counterparty = "Ali"))
        repository.upsert(loan("l1", counterparty = "Ali Raza"))

        assertEquals(1, repository.loans.value.size)
        assertEquals("Ali Raza", repository.loans.value.single().counterparty)
    }

    @Test
    fun `the list is newest first, and stable for loans made the same day`() = runTest {
        val repository = repository()
        repository.upsert(loan("b", date = LocalDate.of(2026, 9, 1)))
        repository.upsert(loan("newest", date = LocalDate.of(2026, 9, 10)))
        repository.upsert(loan("a", date = LocalDate.of(2026, 9, 1)))

        // Same-day ties fall back to the id, so the list does not reshuffle between
        // reads — which on a screen you check repeatedly is disorienting.
        assertEquals(listOf("newest", "a", "b"), repository.loans.value.map { it.id })
    }

    @Test
    fun `deleting a loan takes its repayment history with it`() = runTest {
        val repository = repository()
        repository.upsert(
            loan("l1", repayments = listOf(Repayment("r1", 100, LocalDate.of(2026, 9, 5)))),
        )
        repository.hardDelete("l1")

        val reopened = reopened()
        reopened.load()
        assertTrue(reopened.loans.value.isEmpty())
    }

    @Test
    fun `a file written before dueDate and repayments existed still parses`() = runTest {
        val repository = repository()
        writeLoansFile(
            """
            {
              "schemaVersion": 1,
              "loans": [
                {
                  "id": "l1",
                  "counterparty": "Ali",
                  "direction": "LENT",
                  "principalMinor": 500000,
                  "currencyCode": "PKR",
                  "date": "2026-09-01"
                }
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        val stored = repository.loans.value.single()
        assertNull(stored.dueDate)
        assertTrue(stored.repayments.isEmpty())
        assertEquals(500_000L, stored.outstandingMinor)
    }

    @Test
    fun `a loan with no counterparty is dropped rather than shown anonymously`() = runTest {
        val repository = repository()
        writeLoansFile(
            """
            {
              "schemaVersion": 1,
              "loans": [
                {"id": "good", "counterparty": "Ali", "principalMinor": 100,
                 "currencyCode": "PKR", "date": "2026-09-01"},
                {"id": "nameless", "counterparty": "", "principalMinor": 100,
                 "currencyCode": "PKR", "date": "2026-09-01"},
                {"id": "", "counterparty": "Bilal", "principalMinor": 100,
                 "currencyCode": "PKR", "date": "2026-09-01"},
                {"id": "undated", "counterparty": "Hina", "principalMinor": 100,
                 "currencyCode": "PKR", "date": ""}
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        // The whole point of the record is who owes the money and when. A loan
        // missing either is unusable, and showing it as a balance the user cannot
        // act on is worse than saying nothing.
        assertEquals(listOf("good"), repository.loans.value.map { it.id })
    }

    @Test
    fun `an unreadable repayment is dropped without taking the loan with it`() = runTest {
        val repository = repository()
        writeLoansFile(
            """
            {
              "schemaVersion": 1,
              "loans": [
                {
                  "id": "l1", "counterparty": "Ali", "principalMinor": 500000,
                  "currencyCode": "PKR", "date": "2026-09-01",
                  "repayments": [
                    {"id": "r1", "amountMinor": 200000, "date": "2026-09-05"},
                    {"id": "r2", "amountMinor": 0, "date": "2026-09-06"},
                    {"id": "", "amountMinor": 100, "date": "2026-09-07"},
                    {"id": "r4", "amountMinor": 100, "date": "nonsense"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        // Losing one bad repayment reports too much still owed, which the user can
        // see and correct. Losing the loan loses the debt entirely.
        val stored = repository.loans.value.single()
        assertEquals(listOf("r1"), stored.repayments.map { it.id })
        assertEquals(300_000L, stored.outstandingMinor)
    }

    @Test
    fun `an unreadable due date does not sink the loan`() = runTest {
        val repository = repository()
        writeLoansFile(
            """
            {
              "schemaVersion": 1,
              "loans": [
                {"id": "l1", "counterparty": "Ali", "principalMinor": 100,
                 "currencyCode": "PKR", "date": "2026-09-01", "dueDate": "soon"}
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        // The amount and the person are what matter; a due date is for chasing.
        val stored = repository.loans.value.single()
        assertNull(stored.dueDate)
        assertEquals(100L, stored.principalMinor)
    }

    @Test
    fun `a hand-edited over-repayment reports zero outstanding, never a negative`() = runTest {
        val repository = repository()
        writeLoansFile(
            """
            {
              "schemaVersion": 1,
              "loans": [
                {
                  "id": "l1", "counterparty": "Ali", "principalMinor": 100000,
                  "currencyCode": "PKR", "date": "2026-09-01",
                  "repayments": [{"id": "r1", "amountMinor": 500000, "date": "2026-09-05"}]
                }
              ]
            }
            """.trimIndent(),
        )

        repository.load()

        val stored = repository.loans.value.single()
        assertEquals(0L, stored.outstandingMinor)
        assertTrue(stored.isSettled)
        assertEquals(1f, stored.repaidFraction, 0.0001f)
    }

    @Test
    fun `replaceAll wipes what was there, as an import needs`() = runTest {
        val repository = repository()
        repository.upsert(loan("old"))
        repository.replaceAll(listOf(loan("imported", counterparty = "Hina")))

        val reopened = reopened()
        reopened.load()
        assertEquals(listOf("imported"), reopened.loans.value.map { it.id })
    }

    @Test
    fun `a missing file reads as no loans rather than failing`() = runTest {
        val repository = repository()
        repository.load()

        assertTrue(repository.loans.value.isEmpty())
    }
}
