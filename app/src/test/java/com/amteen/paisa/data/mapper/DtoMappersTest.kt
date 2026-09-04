package com.amteen.paisa.data.mapper

import com.amteen.paisa.data.dto.BudgetDto
import com.amteen.paisa.data.dto.CategoryDto
import com.amteen.paisa.data.dto.CurrencyDto
import com.amteen.paisa.data.dto.PaymentMethodDto
import com.amteen.paisa.data.dto.SettingsDto
import com.amteen.paisa.data.dto.SubcategoryDto
import com.amteen.paisa.data.dto.TransactionDto
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.SortOrder
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.ThemeMode
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * The wire-format boundary.
 *
 * Worth testing directly because a mapper bug is silent: the file parses, the app
 * starts, and a field is quietly wrong or a record has vanished. The two rules that
 * matter are that a **malformed record is dropped rather than invented**, and that a
 * domain object survives a round trip unchanged.
 */
class DtoMappersTest {

    private val instant = Instant.parse("2026-09-04T09:30:00Z")

    // -- Transactions --------------------------------------------------------

    @Test
    fun `a transaction survives a round trip unchanged`() {
        val original = Transaction(
            id = "t1",
            type = TransactionType.INCOME,
            amountMinor = 1_234_567,
            currencyCode = "PKR",
            categoryId = "cat-salary",
            subcategoryId = "sub-base",
            description = "Salary",
            date = LocalDate.of(2026, 9, 4),
            time = LocalTime.of(13, 45),
            paymentMethodId = "pm-bank",
            notes = "with a comma, and \"quotes\"",
            createdAt = instant,
            updatedAt = instant,
        )

        assertEquals(original, original.toDto().toDomain())
    }

    @Test
    fun `a transaction with no readable date is dropped`() {
        // An amount has to sit on some day to be summed at all, so inventing one
        // would silently move money into a month the user never spent it in.
        assertNull(TransactionDto(id = "t", categoryId = "c", currencyCode = "PKR", date = "").toDomain())
        assertNull(
            TransactionDto(id = "t", categoryId = "c", currencyCode = "PKR", date = "nonsense")
                .toDomain(),
        )
        assertNull(
            TransactionDto(id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-13-45")
                .toDomain(),
        )
    }

    @Test
    fun `a transaction missing an identifying field is dropped`() {
        val good = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
        )
        assertTrue(good.toDomain() != null)

        assertNull(good.copy(id = "").toDomain())
        assertNull(good.copy(categoryId = "").toDomain())
        assertNull(good.copy(currencyCode = "").toDomain())
    }

    @Test
    fun `a negative stored amount is corrected rather than trusted`() {
        val parsed = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
            amountMinor = -5_000,
        ).toDomain()

        // The domain guarantees a positive amount with direction carried by `type`.
        // A negative on disk is a hand-edit or a corruption, and letting it through
        // would flip the sign of every total it touches.
        assertEquals(5_000L, parsed?.amountMinor)
    }

    @Test
    fun `an unknown type falls back to expense rather than throwing`() {
        val parsed = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
            type = "TRANSFER",
        ).toDomain()

        assertEquals(TransactionType.EXPENSE, parsed?.type)
    }

    @Test
    fun `type parsing tolerates case and padding`() {
        val parsed = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
            type = "  income  ",
        ).toDomain()

        assertEquals(TransactionType.INCOME, parsed?.type)
    }

    @Test
    fun `an unreadable time becomes midnight rather than dropping the record`() {
        val parsed = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
            time = "half past two",
        ).toDomain()

        // Unlike the date, a missing time costs nothing: the record still belongs to
        // a known day, so it is kept.
        assertEquals(LocalTime.MIDNIGHT, parsed?.time)
    }

    @Test
    fun `blank optional strings become null, not empty`() {
        val parsed = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
            subcategoryId = "", paymentMethodId = "  ", notes = "",
        ).toDomain()

        // An empty subcategoryId would look like a real reference to the pickers and
        // to the reports' "Unspecified" bucket.
        assertNull(parsed?.subcategoryId)
        assertNull(parsed?.paymentMethodId)
        assertNull(parsed?.notes)
    }

    @Test
    fun `a missing updatedAt falls back to createdAt`() {
        val parsed = TransactionDto(
            id = "t", categoryId = "c", currencyCode = "PKR", date = "2026-09-04",
            createdAt = instant.toString(), updatedAt = null,
        ).toDomain()

        // A record written before updatedAt existed is not "never updated at the
        // epoch" — it was last touched when it was created.
        assertEquals(instant, parsed?.updatedAt)
    }

    @Test
    fun `an empty dto parses to nothing at all`() {
        // Every DTO field is defaulted so an old file keeps parsing, which means the
        // all-defaults case has to be rejected explicitly.
        assertNull(TransactionDto().toDomain())
    }

    // -- Categories ----------------------------------------------------------

    @Test
    fun `a category survives a round trip with its subcategories`() {
        val original = Category(
            id = "cat-food",
            name = "Food & Drink",
            applicableTo = CategoryScope.BOTH,
            iconKey = "restaurant",
            colorArgb = 0xFFE07A5F.toInt(),
            sortOrder = 3,
            archived = true,
            subcategories = listOf(
                Subcategory("sub-a", "Groceries", 0, false),
                Subcategory("sub-b", "Coffee", 1, true),
            ),
        )

        assertEquals(original, original.toDto().toDomain())
    }

    @Test
    fun `a category without an id is dropped, and so is a subcategory`() {
        assertNull(CategoryDto(id = "", name = "Orphan").toDomain())

        val parsed = CategoryDto(
            id = "cat", name = "Cat",
            subcategories = listOf(SubcategoryDto(id = "", name = "nameless"), SubcategoryDto("ok", "Fine")),
        ).toDomain()

        // The category survives; only the unusable subcategory is dropped.
        assertEquals(listOf("ok"), parsed?.subcategories?.map { it.id })
    }

    @Test
    fun `an unknown category scope falls back to expense`() {
        val parsed = CategoryDto(id = "c", applicableTo = "SOMETHING").toDomain()
        assertEquals(CategoryScope.EXPENSE, parsed?.applicableTo)
    }

    // -- Currency, payment method, budget ------------------------------------

    @Test
    fun `a currency survives a round trip and a codeless one is dropped`() {
        val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
        assertEquals(pkr, pkr.toDto().toDomain())
        assertNull(CurrencyDto(code = "").toDomain())
    }

    @Test
    fun `a payment method survives a round trip and an idless one is dropped`() {
        val cash = PaymentMethod("pm-cash", "Cash", "cash", 2, archived = true)
        assertEquals(cash, cash.toDto().toDomain())
        assertNull(PaymentMethodDto(id = "").toDomain())
    }

    @Test
    fun `a budget survives a round trip, recurring or pinned`() {
        val recurring = Budget(
            id = "b1", categoryId = "cat-food", subcategoryId = "sub-a",
            limitMinor = 300_000, currencyCode = "PKR", period = null, archived = false,
        )
        val pinned = recurring.copy(id = "b2", period = YearMonth.of(2026, 9), archived = true)

        assertEquals(recurring, recurring.toDto().toDomain())
        assertEquals(pinned, pinned.toDto().toDomain())
        // Null period is the real "every month" value, not a parse failure.
        assertNull(recurring.toDto().toDomain()?.period)
    }

    @Test
    fun `a budget with an unreadable period becomes recurring rather than being dropped`() {
        val parsed = BudgetDto(
            id = "b", categoryId = "c", limitMinor = 100, currencyCode = "PKR",
            period = "not-a-month",
        ).toDomain()

        assertEquals(null, parsed?.period)
    }

    // -- Settings ------------------------------------------------------------

    @Test
    fun `settings survive a round trip`() {
        val original = AppSettings(
            baseCurrencyCode = "PKR",
            themeMode = ThemeMode.DARK,
            firstDayOfWeek = DayOfWeek.SUNDAY,
            defaultSortOrder = SortOrder.AMOUNT_DESC,
            defaultPaymentMethodId = "pm-cash",
            budgetAlertsEnabled = false,
            autoBackupEnabled = false,
            backupsToKeep = 7,
            initialized = true,
        )

        assertEquals(original, original.toDto().toDomain())
    }

    @Test
    fun `unreadable settings values fall back instead of throwing`() {
        val parsed = SettingsDto(
            baseCurrencyCode = "",
            themeMode = "NEON",
            firstDayOfWeek = "FUNDAY",
            defaultSortOrder = "SIDEWAYS",
        ).toDomain()

        // A settings file the user hand-edited must not stop the app from starting.
        assertEquals(AppSettings.DEFAULT_BASE_CURRENCY, parsed.baseCurrencyCode)
        assertEquals(ThemeMode.SYSTEM, parsed.themeMode)
        assertEquals(DayOfWeek.MONDAY, parsed.firstDayOfWeek)
        assertEquals(SortOrder.DATE_DESC, parsed.defaultSortOrder)
    }

    @Test
    fun `backupsToKeep is clamped to something sane`() {
        // Zero would silently disable the rolling snapshot; a huge number would let
        // the backup folder grow without bound.
        assertEquals(1, SettingsDto(backupsToKeep = 0).toDomain().backupsToKeep)
        assertEquals(1, SettingsDto(backupsToKeep = -5).toDomain().backupsToKeep)
        assertEquals(50, SettingsDto(backupsToKeep = 9_999).toDomain().backupsToKeep)
    }
}
