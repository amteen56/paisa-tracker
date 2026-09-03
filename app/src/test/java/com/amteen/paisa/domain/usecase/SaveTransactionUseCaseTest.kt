package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class SaveTransactionUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var repository: FileTransactionRepositoryImpl
    private lateinit var save: SaveTransactionUseCase

    private val fixedNow = Instant.parse("2026-09-03T09:00:00Z")
    private val today = LocalDate.of(2026, 9, 3)

    @Before
    fun setUp() {
        repository = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder("app-data")))
        var counter = 0
        save = SaveTransactionUseCase(
            transactions = repository,
            now = { fixedNow },
            newId = { "generated-${counter++}" },
            today = { today },
        )
    }

    private fun input(
        id: String? = null,
        amountMinor: Long = 80_000,
        categoryId: String = "cat-food",
        date: LocalDate = today,
        description: String = "Burger",
        notes: String? = null,
    ) = TransactionInput(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = amountMinor,
        currencyCode = "PKR",
        categoryId = categoryId,
        description = description,
        date = date,
        time = LocalTime.of(13, 30),
        notes = notes,
    )

    @Test
    fun `saves a valid transaction and generates an id`() = runTest {
        val result = save(input())

        assertTrue(result is AppResult.Ok)
        val saved = (result as AppResult.Ok).value
        assertEquals("generated-0", saved.id)
        assertEquals(fixedNow, saved.createdAt)
        assertEquals(fixedNow, saved.updatedAt)
        assertEquals(1, repository.getMonth(YearMonth.of(2026, 9)).size)
    }

    @Test
    fun `an edit keeps the original creation instant`() = runTest {
        val created = (save(input()) as AppResult.Ok).value

        val later = SaveTransactionUseCase(
            transactions = repository,
            now = { Instant.parse("2026-09-10T18:00:00Z") },
            newId = { "unused" },
            today = { today },
        )
        val edited = (later(input(id = created.id, amountMinor = 95_000)) as AppResult.Ok).value

        // "Added on" must stay truthful across an edit; only updatedAt moves.
        assertEquals(fixedNow, edited.createdAt)
        assertEquals(Instant.parse("2026-09-10T18:00:00Z"), edited.updatedAt)
        assertEquals(95_000L, edited.amountMinor)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun `rejects a zero or negative amount with a field error`() = runTest {
        val zero = save(input(amountMinor = 0)) as AppResult.Err
        assertEquals(
            SaveTransactionUseCase.FIELD_AMOUNT,
            (zero.error as AppError.Validation).field,
        )

        val negative = save(input(amountMinor = -100)) as AppResult.Err
        assertTrue(negative.error is AppError.Validation)

        // Nothing invalid reaches disk.
        assertEquals(0, repository.getAll().size)
    }

    @Test
    fun `rejects a missing category`() = runTest {
        val result = save(input(categoryId = "")) as AppResult.Err
        assertEquals(
            SaveTransactionUseCase.FIELD_CATEGORY,
            (result.error as AppError.Validation).field,
        )
    }

    @Test
    fun `rejects an absurd date`() = runTest {
        val ancient = save(input(date = LocalDate.of(1900, 1, 1))) as AppResult.Err
        assertEquals(
            SaveTransactionUseCase.FIELD_DATE,
            (ancient.error as AppError.Validation).field,
        )

        val distant = save(input(date = today.plusYears(50))) as AppResult.Err
        assertTrue(distant.error is AppError.Validation)
    }

    @Test
    fun `accepts a date within the allowed window`() = runTest {
        assertTrue(save(input(date = LocalDate.of(1970, 1, 1))) is AppResult.Ok)
        assertTrue(save(input(date = today.plusYears(1))) is AppResult.Ok)
    }

    @Test
    fun `rejects an over-long description`() = runTest {
        val result = save(
            input(description = "x".repeat(SaveTransactionUseCase.MAX_DESCRIPTION + 1)),
        ) as AppResult.Err
        assertEquals(
            SaveTransactionUseCase.FIELD_DESCRIPTION,
            (result.error as AppError.Validation).field,
        )
    }

    @Test
    fun `trims whitespace and drops empty notes`() = runTest {
        val saved = (save(input(description = "  Burger  ", notes = "   ")) as AppResult.Ok).value

        assertEquals("Burger", saved.description)
        assertEquals(null, saved.notes)
    }

    @Test
    fun `an edit that changes the month moves the record`() = runTest {
        val created = (save(input()) as AppResult.Ok).value

        save(input(id = created.id, date = LocalDate.of(2026, 8, 20)))

        assertEquals(0, repository.getMonth(YearMonth.of(2026, 9)).size)
        assertEquals(1, repository.getMonth(YearMonth.of(2026, 8)).size)
        assertNotNull(repository.getById(created.id))
        assertEquals(1, repository.getAll().size)
    }
}
