package com.amteen.paisa.domain.usecase

import com.amteen.paisa.core.result.AppError
import com.amteen.paisa.core.result.AppResult
import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileBackupRepositoryImpl
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Subcategory
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
 * Export, and the validate → preview → commit contract.
 *
 * Runs against the real file store and the real backup repository, so the JSON that
 * gets written is the JSON that gets parsed — a fake codec would let a serialization
 * mistake through, and that is the one mistake that costs the user their data.
 */
class BackupUseCasesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val exportedAt = Instant.parse("2026-09-04T10:00:00Z")

    private val food = Category(
        id = "cat-food",
        name = "Food & Drink",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0x11,
        subcategories = listOf(Subcategory("sub-fast", "Fast Food")),
    )
    private val salary = Category(
        id = "cat-salary",
        name = "Salary",
        applicableTo = CategoryScope.INCOME,
        iconKey = "wallet",
        colorArgb = 0x22,
    )
    private val cash = PaymentMethod("pm-cash", "Cash", "cash")
    private val budget = Budget(
        id = "b1",
        categoryId = "cat-food",
        limitMinor = 3_000_00,
        currencyCode = "PKR",
        period = YearMonth.of(2026, 9),
    )

    private lateinit var store: JsonFileStore
    private lateinit var transactions: FileTransactionRepositoryImpl
    private lateinit var backups: FileBackupRepositoryImpl
    private lateinit var categories: FakeCategoryRepository
    private lateinit var methods: FakePaymentMethodRepository
    private lateinit var budgets: FakeBudgetRepository
    private lateinit var settings: FakeSettingsRepository

    private lateinit var exportBackup: ExportBackupUseCase
    private lateinit var exportCsv: ExportCsvUseCase
    private lateinit var prepare: PrepareImportUseCase
    private lateinit var commit: CommitImportUseCase

    @Before
    fun setUp() {
        store = JsonFileStore(temp.newFolder())
        transactions = FileTransactionRepositoryImpl(store)
        backups = FileBackupRepositoryImpl(store) { exportedAt }
        categories = FakeCategoryRepository(listOf(food, salary))
        methods = FakePaymentMethodRepository(listOf(cash))
        budgets = FakeBudgetRepository(listOf(budget))
        settings = FakeSettingsRepository(AppSettings(baseCurrencyCode = "PKR"))

        exportBackup = ExportBackupUseCase(
            transactions, categories, methods, budgets, settings, backups,
        ) { exportedAt }
        exportCsv = ExportCsvUseCase(transactions, categories, methods, backups)
        prepare = PrepareImportUseCase(
            transactions, categories, methods, budgets, settings, backups,
        )
        commit = CommitImportUseCase(
            transactions, categories, methods, budgets, settings, backups, exportBackup,
        )
    }

    private fun transaction(
        id: String,
        amountMinor: Long = 200_000,
        date: LocalDate = LocalDate.of(2026, 9, 4),
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: String = "cat-food",
        subcategoryId: String? = null,
        description: String = "Burger",
        notes: String? = null,
    ) = Transaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = "PKR",
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        description = description,
        date = date,
        time = LocalTime.of(13, 45),
        paymentMethodId = "pm-cash",
        notes = notes,
        createdAt = exportedAt,
        updatedAt = exportedAt,
    )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Ok<T>).value

    // -- Export -------------------------------------------------------------

    @Test
    fun `a JSON backup round-trips every record exactly`() = runTest {
        transactions.save(transaction("t1", subcategoryId = "sub-fast", notes = "with cheese"))
        transactions.save(
            transaction("t2", 1_000_000, type = TransactionType.INCOME, categoryId = "cat-salary"),
        )

        val document = exportBackup().value()
        val snapshot = backups.decodeJson(document)

        assertEquals(2, snapshot.transactions.size)
        assertEquals(2, snapshot.categories.size)
        assertEquals(1, snapshot.paymentMethods.size)
        assertEquals(1, snapshot.budgets.size)
        assertEquals(exportedAt, snapshot.exportedAt)

        // The whole point of JSON over CSV: nothing is lost.
        val restored = snapshot.transactions.first { it.id == "t1" }
        assertEquals(200_000L, restored.amountMinor)
        assertEquals("sub-fast", restored.subcategoryId)
        assertEquals("with cheese", restored.notes)
        assertEquals(LocalTime.of(13, 45), restored.time)
        assertEquals(LocalDate.of(2026, 9, 4), restored.date)
    }

    @Test
    fun `CSV writes amounts as minor units, not as a decimal`() = runTest {
        transactions.save(transaction("t1", amountMinor = 35_050))

        val csv = exportCsv().value()
        val rows = com.amteen.paisa.data.csv.Csv.parse(csv)
        val amountColumn = rows.first().indexOf("amountminor")

        // A spreadsheet that reformats 350.50 is exactly the drift the Long model
        // exists to prevent.
        assertEquals("35050", rows[1][amountColumn])
        assertFalse(csv.contains("350.50"))
    }

    @Test
    fun `CSV resolves ids to names so a person can read it`() = runTest {
        transactions.save(transaction("t1", subcategoryId = "sub-fast"))

        val rows = com.amteen.paisa.data.csv.Csv.parse(exportCsv().value())
        val header = rows.first()
        val row = rows[1]

        assertEquals("Food & Drink", row[header.indexOf("category")])
        assertEquals("Fast Food", row[header.indexOf("subcategory")])
        assertEquals("Cash", row[header.indexOf("paymentmethod")])
    }

    @Test
    fun `a description containing a comma survives the CSV round trip`() = runTest {
        transactions.save(transaction("t1", description = "Lunch, then coffee"))

        val preview = prepare.fromCsv(exportCsv().value(), ImportMode.REPLACE).value()

        // Already on the device, so it counts as a duplicate rather than new — which
        // is itself the proof that the id column round-tripped.
        assertEquals(1, preview.duplicateTransactions)
    }

    // -- Import: validation --------------------------------------------------

    @Test
    fun `a file that is not a backup is refused with a readable message`() = runTest {
        val result = prepare.fromJson("this is not json at all", ImportMode.MERGE)

        assertTrue(result is AppResult.Err)
        val message = (result as AppResult.Err).error.displayMessage
        assertTrue(message.contains("Paisa backup"))
    }

    @Test
    fun `a backup from a newer build is refused rather than half-read`() = runTest {
        // Hand-built so the version is above whatever this build understands.
        val future = """
            {"schemaVersion": 99, "exportedAt": "2026-09-04T10:00:00Z",
             "categories": [], "transactions": []}
        """.trimIndent()

        val result = prepare.fromJson(future, ImportMode.REPLACE)

        // Guessing could silently drop fields this build cannot represent, and
        // dropping data during a Replace cannot be undone.
        assertTrue(result is AppResult.Err)
        assertTrue((result as AppResult.Err).error is AppError.SchemaTooNew)
    }

    @Test
    fun `nothing is written during validation`() = runTest {
        transactions.save(transaction("mine"))
        val incoming = incomingDocument(listOf(transaction("theirs")))

        prepare.fromJson(incoming, ImportMode.REPLACE)

        // The preview step must be side-effect free, or a user who backs out has
        // already lost something.
        assertEquals(listOf("mine"), transactions.getAll().map { it.id })
        assertEquals(2, categories.categories.value.size)
    }

    // -- Import: merge -------------------------------------------------------

    @Test
    fun `a merge adds what is new and skips what is already here`() = runTest {
        transactions.save(transaction("shared"))
        val incoming = incomingDocument(listOf(transaction("shared"), transaction("fresh")))

        val preview = prepare.fromJson(incoming, ImportMode.MERGE).value()

        assertEquals(1, preview.incomingTransactions)
        assertEquals(1, preview.duplicateTransactions)
        assertEquals(0, preview.replacedTransactions)

        commit(preview)
        assertEquals(setOf("shared", "fresh"), transactions.getAll().map { it.id }.toSet())
    }

    @Test
    fun `importing the same file twice changes nothing the second time`() = runTest {
        val incoming = incomingDocument(listOf(transaction("t1"), transaction("t2")))

        commit(prepare.fromJson(incoming, ImportMode.MERGE).value())
        val afterFirst = transactions.getAll().map { it.id }.toSet()

        val second = prepare.fromJson(incoming, ImportMode.MERGE).value()
        assertEquals(0, second.incomingTransactions)
        assertEquals(2, second.duplicateTransactions)

        commit(second)
        // A doubling here would be the worst kind of import bug: silent, and it
        // corrupts every total the user looks at afterwards.
        assertEquals(afterFirst, transactions.getAll().map { it.id }.toSet())
    }

    @Test
    fun `a merge never overwrites a record the user already has`() = runTest {
        transactions.save(transaction("shared", description = "mine"))
        val incoming = incomingDocument(listOf(transaction("shared", description = "theirs")))

        commit(prepare.fromJson(incoming, ImportMode.MERGE).value())

        // "Add what is new" must not be quietly destructive.
        assertEquals("mine", transactions.getById("shared")?.description)
    }

    // -- Import: replace -----------------------------------------------------

    @Test
    fun `a replace says how much it will delete before it does`() = runTest {
        transactions.save(transaction("old-1"))
        transactions.save(transaction("old-2"))
        val incoming = incomingDocument(listOf(transaction("new-1")))

        val preview = prepare.fromJson(incoming, ImportMode.REPLACE).value()

        assertTrue(preview.isDestructive)
        assertEquals(2, preview.replacedTransactions)
        assertEquals(1, preview.incomingTransactions)
    }

    @Test
    fun `a replace snapshots to backup before wiping anything`() = runTest {
        transactions.save(transaction("doomed"))
        val incoming = incomingDocument(listOf(transaction("replacement")))

        assertTrue(backups.listLocalBackups().isEmpty())

        commit(prepare.fromJson(incoming, ImportMode.REPLACE).value())

        // CLAUDE.md rule 8: a Replace the user regrets has to be recoverable.
        val snapshots = backups.listLocalBackups()
        assertEquals(1, snapshots.size)
        val saved = backups.decodeJson(backups.readLocalBackup(snapshots.single())!!)
        assertEquals(listOf("doomed"), saved.transactions.map { it.id })

        assertEquals(listOf("replacement"), transactions.getAll().map { it.id })
    }

    @Test
    fun `a replace from CSV leaves budgets and settings alone`() = runTest {
        transactions.save(transaction("old"))
        val csv = exportCsv().value()
        transactions.replaceAll(emptyList())

        val preview = prepare.fromCsv(csv, ImportMode.REPLACE).value()
        commit(preview)

        // A CSV carries neither, so a Replace driven by one must not wipe them.
        assertEquals(1, budgets.budgets.value.size)
        assertEquals("PKR", settings.settings.value.baseCurrencyCode)
    }

    // -- Import: repair ------------------------------------------------------

    @Test
    fun `a backup missing its own categories is repaired, not dropped`() = runTest {
        // A transaction pointing at a category the document does not contain.
        val incoming = incomingDocument(
            transactions = listOf(transaction("orphan", categoryId = "cat-vanished")),
            categories = emptyList(),
        )

        val preview = prepare.fromJson(incoming, ImportMode.REPLACE).value()
        assertEquals(1, preview.repairedReferences)

        commit(preview)

        // Rule 4: a transaction must never end up pointing at a categoryId that no
        // longer resolves — and the money must not be thrown away to achieve that.
        assertEquals(1, transactions.getAll().size)
        assertNotNull(categories.getById("cat-vanished"))
    }

    @Test
    fun `a foreign currency code is normalised to PKR on the way in`() = runTest {
        val incoming = incomingDocument(
            listOf(transaction("t1").copy(currencyCode = "USD")),
        )

        commit(prepare.fromJson(incoming, ImportMode.REPLACE).value())

        // The app is PKR-only; keeping USD would render the amount against a
        // currency that does not exist here.
        assertEquals("PKR", transactions.getById("t1")?.currencyCode)
    }

    @Test
    fun `an imported base currency is never adopted`() = runTest {
        val incoming = incomingDocument(listOf(transaction("t1")), baseCurrency = "USD")

        commit(prepare.fromJson(incoming, ImportMode.REPLACE).value())

        assertEquals("PKR", settings.settings.value.baseCurrencyCode)
    }

    // -- Import: CSV ---------------------------------------------------------

    @Test
    fun `a CSV naming an unknown category creates it rather than dropping the row`() = runTest {
        val csv = """
            date,time,type,amountminor,category,paymentmethod,description
            2026-09-05,10:00,EXPENSE,45000,Gardening,Cash,Seeds
        """.trimIndent()

        val preview = prepare.fromCsv(csv, ImportMode.MERGE).value()
        assertEquals(1, preview.incomingTransactions)
        assertEquals(1, preview.incomingCategories)

        commit(preview)

        // Dropping it would move the money to "Uncategorised" and quietly change
        // every report.
        val created = categories.categories.value.first { it.name == "Gardening" }
        assertEquals(created.id, transactions.getAll().single().categoryId)
    }

    @Test
    fun `a CSV matches existing names case-insensitively`() = runTest {
        val csv = """
            date,amountminor,category,paymentmethod
            2026-09-05,45000,food & drink,cash
        """.trimIndent()

        val preview = prepare.fromCsv(csv, ImportMode.MERGE).value()

        // No new category or method: both already exist under a different casing.
        assertEquals(0, preview.incomingCategories)
        assertEquals(0, preview.incomingPaymentMethods)

        commit(preview)
        assertEquals("cat-food", transactions.getAll().single().categoryId)
        assertEquals("pm-cash", transactions.getAll().single().paymentMethodId)
    }

    @Test
    fun `unreadable CSV rows are reported and the rest still import`() = runTest {
        val csv = """
            date,amountminor,category
            2026-09-05,45000,Food & Drink
            not-a-date,45000,Food & Drink
            2026-09-06,zero,Food & Drink
            2026-09-07,-500,Food & Drink
            2026-09-08,12000,Food & Drink
        """.trimIndent()

        val preview = prepare.fromCsv(csv, ImportMode.MERGE).value()

        assertEquals(2, preview.incomingTransactions)
        // Reported rather than swallowed, and each names its line so the user can
        // find it in the file.
        assertEquals(3, preview.unreadable.size)
        assertTrue(preview.unreadable.any { it.contains("Line 3") })
        assertTrue(preview.unreadable.any { it.contains("Line 4") })
        assertTrue(preview.unreadable.any { it.contains("Line 5") })
    }

    @Test
    fun `a CSV with columns in a different order still imports`() = runTest {
        val csv = """
            description,amountminor,category,date
            Seeds,45000,Food & Drink,2026-09-05
        """.trimIndent()

        val preview = prepare.fromCsv(csv, ImportMode.MERGE).value()
        assertEquals(1, preview.incomingTransactions)

        commit(preview)
        assertEquals("Seeds", transactions.getAll().single().description)
        assertEquals(45_000L, transactions.getAll().single().amountMinor)
    }

    @Test
    fun `a CSV with no date column is refused`() = runTest {
        val result = prepare.fromCsv("name,amount\nfoo,100", ImportMode.MERGE)
        assertTrue(result is AppResult.Err)
    }

    @Test
    fun `an empty CSV is refused rather than importing nothing silently`() = runTest {
        assertTrue(prepare.fromCsv("", ImportMode.MERGE) is AppResult.Err)
    }

    @Test
    fun `a hand-made CSV without ids does not duplicate on a second import`() = runTest {
        val csv = """
            date,amountminor,category,description
            2026-09-05,45000,Food & Drink,Seeds
        """.trimIndent()

        commit(prepare.fromCsv(csv, ImportMode.MERGE).value())
        assertEquals(1, transactions.getAll().size)

        val second = prepare.fromCsv(csv, ImportMode.MERGE).value()
        // The id is derived from the row's own content, so re-importing is a no-op.
        assertEquals(0, second.incomingTransactions)
        assertEquals(1, second.duplicateTransactions)
    }

    @Test
    fun `a CSV row with no time lands at midday, not midnight`() = runTest {
        val csv = """
            date,amountminor,category
            2026-09-05,45000,Food & Drink
        """.trimIndent()

        commit(prepare.fromCsv(csv, ImportMode.MERGE).value())

        // Midnight would sort a date-only row ahead of everything genuinely
        // recorded that morning.
        assertEquals(LocalTime.NOON, transactions.getAll().single().time)
    }

    // -- Local snapshots -----------------------------------------------------

    @Test
    fun `local snapshots are pruned to the configured count, oldest first`() = runTest {
        transactions.save(transaction("t1"))

        var clock = exportedAt
        val rolling = FileBackupRepositoryImpl(store) { clock }
        val writer = WriteLocalBackupUseCase(exportBackup, rolling, settings)
        settings.update { it.copy(backupsToKeep = 3) }

        repeat(5) { index ->
            clock = exportedAt.plusSeconds((index + 1) * 60L)
            writer("manual")
        }

        val kept = rolling.listLocalBackups()
        // Bounded, so years of use cannot fill the device.
        assertEquals(3, kept.size)
        // Newest first, and the newest is the last one written.
        assertTrue(kept.first().contains("10-05-00"))
    }

    // -- Helpers -------------------------------------------------------------

    /** Builds a backup document as if another install had exported it. */
    private suspend fun incomingDocument(
        transactions: List<Transaction>,
        categories: List<Category> = listOf(food, salary),
        baseCurrency: String = "PKR",
    ): String = backups.encodeJson(
        com.amteen.paisa.domain.model.AppSnapshot(
            schemaVersion = backups.schemaVersion,
            exportedAt = exportedAt,
            settings = AppSettings(baseCurrencyCode = baseCurrency),
            categories = categories,
            paymentMethods = listOf(cash),
            budgets = emptyList(),
            transactions = transactions,
        ),
    )
}
