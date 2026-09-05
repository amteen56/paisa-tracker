package com.amteen.paisa.ui.screen.calendar

import com.amteen.paisa.data.file.JsonFileStore
import com.amteen.paisa.data.repository.FileTransactionRepositoryImpl
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import com.amteen.paisa.domain.usecase.GetMonthCalendarUseCase
import com.amteen.paisa.testing.FakeCategoryRepository
import com.amteen.paisa.testing.FakeCurrencyRepository
import com.amteen.paisa.testing.FakePaymentMethodRepository
import com.amteen.paisa.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Opening the calendar on a particular day.
 *
 * Home's seven-day strip hands the calendar a date, and the screen shows a day sheet
 * whenever one is selected. The trap this guards is the month: the ViewModel drops a
 * selected day the grid does not contain, so seeding the date without also seeding
 * the month would silently swallow the sheet for any day in the previous month — and
 * in the first week of a month that is most of the strip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A Tuesday, in a month that starts on a Tuesday. */
    private val today = LocalDate.of(2026, 9, 15)

    private val pkr = Currency("PKR", "Pakistani Rupee", "Rs.", 2, 1.0)
    private val food = Category(
        id = "cat-food",
        name = "Food & Drink",
        applicableTo = CategoryScope.EXPENSE,
        iconKey = "restaurant",
        colorArgb = 0,
    )
    private val cash = PaymentMethod("pm-cash", "Cash", "cash")

    private lateinit var repository: FileTransactionRepositoryImpl

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which does not exist on the JVM.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FileTransactionRepositoryImpl(JsonFileStore(temp.newFolder()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(initialDate: LocalDate?) = CalendarViewModel(
        getMonthCalendar = GetMonthCalendarUseCase(
            transactions = repository,
            categories = FakeCategoryRepository(listOf(food)),
            paymentMethods = FakePaymentMethodRepository(listOf(cash)),
            currencies = FakeCurrencyRepository(listOf(pkr)),
            settings = FakeSettingsRepository(
                AppSettings(baseCurrencyCode = "PKR", firstDayOfWeek = DayOfWeek.MONDAY),
            ),
            today = { today },
        ),
        categoryRepository = FakeCategoryRepository(listOf(food)),
        paymentMethodRepository = FakePaymentMethodRepository(listOf(cash)),
        currencyRepository = FakeCurrencyRepository(listOf(pkr)),
        settingsRepository = FakeSettingsRepository(
            AppSettings(baseCurrencyCode = "PKR", firstDayOfWeek = DayOfWeek.MONDAY),
        ),
        initialDate = initialDate,
    )

    private fun transaction(id: String, amountMinor: Long, date: LocalDate) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = amountMinor,
        currencyCode = "PKR",
        categoryId = "cat-food",
        date = date,
        time = LocalTime.of(12, 0),
        paymentMethodId = "pm-cash",
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    // -- Arriving with a day ------------------------------------------------

    @Test
    fun `an initial date opens that day's sheet on that day's month`() = runTest {
        val day = LocalDate.of(2026, 9, 11)
        repository.save(transaction("lunch", 45_000, day))

        val state = viewModel(day).uiState.first { !it.isLoading }

        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(day, state.selectedDate)
        // The sheet renders off selectedDay, not selectedDate — it has to resolve.
        assertNotNull(state.selectedDay)
        assertEquals(45_000L, state.selectedDay?.expenseMinor)
    }

    @Test
    fun `a day in the previous month moves the grid rather than losing the sheet`() = runTest {
        // The case that breaks if only selectedDate is seeded: in the first week of a
        // month most of Home's seven-day strip belongs to the month before.
        val day = LocalDate.of(2026, 8, 31)
        repository.save(transaction("august", 12_000, day))

        val state = viewModel(day).uiState.first { !it.isLoading }

        assertEquals(YearMonth.of(2026, 8), state.month)
        assertEquals(day, state.selectedDate)
        assertNotNull(state.selectedDay)
        assertEquals(12_000L, state.selectedDay?.expenseMinor)
    }

    @Test
    fun `a day with nothing on it still opens`() = runTest {
        // Tapping an empty bar is how the user records against that day.
        val day = LocalDate.of(2026, 9, 3)

        val state = viewModel(day).uiState.first { !it.isLoading }

        assertEquals(day, state.selectedDay?.date)
        assertEquals(0L, state.selectedDay?.expenseMinor)
        assertEquals(0, state.selectedDay?.count)
    }

    // -- Arriving without one -----------------------------------------------

    @Test
    fun `no initial date leaves the sheet closed on the current month`() = runTest {
        // The More menu's path into the calendar.
        val state = viewModel(null).uiState.first { !it.isLoading }

        assertEquals(YearMonth.now(), state.month)
        assertNull(state.selectedDate)
        assertNull(state.selectedDay)
    }

    @Test
    fun `dismissing a seeded day closes the sheet for good`() = runTest {
        val day = LocalDate.of(2026, 9, 11)
        repository.save(transaction("lunch", 45_000, day))
        val model = viewModel(day)
        model.uiState.first { !it.isLoading }

        model.onEvent(CalendarEvent.DayDismissed)

        val state = model.uiState.first { it.selectedDate == null }
        assertNull(state.selectedDay)
        // The month the user was taken to stays put; only the sheet closed.
        assertEquals(YearMonth.of(2026, 9), state.month)
    }
}
