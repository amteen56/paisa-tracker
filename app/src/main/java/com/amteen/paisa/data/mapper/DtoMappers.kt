package com.amteen.paisa.data.mapper

import com.amteen.paisa.data.dto.BudgetAlertDto
import com.amteen.paisa.data.dto.BudgetDto
import com.amteen.paisa.data.dto.CategoryDto
import com.amteen.paisa.data.dto.CurrencyDto
import com.amteen.paisa.data.dto.PaymentMethodDto
import com.amteen.paisa.data.dto.SettingsDto
import com.amteen.paisa.data.dto.SubcategoryDto
import com.amteen.paisa.data.dto.TransactionDto
import com.amteen.paisa.domain.model.AppSettings
import com.amteen.paisa.domain.model.Budget
import com.amteen.paisa.domain.model.BudgetAlert
import com.amteen.paisa.domain.model.BudgetAlertThresholds
import com.amteen.paisa.domain.model.Category
import com.amteen.paisa.domain.model.CategoryScope
import com.amteen.paisa.domain.model.Currency
import com.amteen.paisa.domain.model.PaymentMethod
import com.amteen.paisa.domain.model.SortOrder
import com.amteen.paisa.domain.model.Subcategory
import com.amteen.paisa.domain.model.ThemeMode
import com.amteen.paisa.domain.model.Transaction
import com.amteen.paisa.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * DTO <-> domain conversion.
 *
 * Parsing is **lenient in one direction only**: reading tolerates a missing or
 * malformed field by substituting a documented default, because refusing to load
 * would mean the user cannot open their own data over one bad character. Writing
 * is always strict and canonical.
 *
 * A transaction with an unparseable date is the one exception — a transaction has
 * to sit on some day to be summed at all, so [TransactionDto.toDomain] returns
 * null and the caller drops it rather than inventing a date.
 */

// -- Transaction ------------------------------------------------------------

fun TransactionDto.toDomain(): Transaction? {
    val parsedDate = parseDate(date) ?: return null
    if (id.isBlank() || categoryId.isBlank() || currencyCode.isBlank()) return null

    return Transaction(
        id = id,
        type = parseEnum(type, TransactionType.EXPENSE) { TransactionType.valueOf(it) },
        // A negative amount on disk is a corruption or a hand-edit; the domain
        // guarantees positive amounts with direction carried by `type`.
        amountMinor = if (amountMinor < 0) -amountMinor else amountMinor,
        currencyCode = currencyCode,
        categoryId = categoryId,
        subcategoryId = subcategoryId?.takeIf { it.isNotBlank() },
        description = description,
        date = parsedDate,
        time = parseTime(time) ?: LocalTime.MIDNIGHT,
        paymentMethodId = paymentMethodId?.takeIf { it.isNotBlank() },
        notes = notes?.takeIf { it.isNotBlank() },
        createdAt = parseInstant(createdAt) ?: Instant.EPOCH,
        updatedAt = parseInstant(updatedAt) ?: parseInstant(createdAt) ?: Instant.EPOCH,
    )
}

fun Transaction.toDto() = TransactionDto(
    id = id,
    type = type.name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    categoryId = categoryId,
    subcategoryId = subcategoryId,
    description = description,
    date = date.toString(),
    time = time.toString(),
    paymentMethodId = paymentMethodId,
    notes = notes,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

// -- Category ---------------------------------------------------------------

fun CategoryDto.toDomain(): Category? {
    if (id.isBlank()) return null
    return Category(
        id = id,
        name = name,
        applicableTo = parseEnum(applicableTo, CategoryScope.EXPENSE) { CategoryScope.valueOf(it) },
        iconKey = iconKey,
        colorArgb = colorArgb,
        sortOrder = sortOrder,
        archived = archived,
        subcategories = subcategories.mapNotNull { it.toDomain() },
    )
}

fun Category.toDto() = CategoryDto(
    id = id,
    name = name,
    applicableTo = applicableTo.name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    archived = archived,
    subcategories = subcategories.map { it.toDto() },
)

fun SubcategoryDto.toDomain(): Subcategory? =
    if (id.isBlank()) null else Subcategory(id, name, sortOrder, archived)

fun Subcategory.toDto() = SubcategoryDto(id, name, sortOrder, archived)

// -- Currency ---------------------------------------------------------------

fun CurrencyDto.toDomain(): Currency? {
    if (code.isBlank()) return null
    return Currency(
        code = code,
        name = name.ifBlank { code },
        symbol = symbol.ifBlank { code },
        decimalDigits = decimalDigits.coerceIn(0, Currency.MAX_DECIMAL_DIGITS),
        // A non-positive or non-finite rate would make every conversion throw.
        // Fall back to 1.0 so the app opens, and let the user fix it in Settings.
        rateToBase = if (rateToBase.isFinite() && rateToBase > 0.0) rateToBase else 1.0,
        archived = archived,
    )
}

fun Currency.toDto() = CurrencyDto(code, name, symbol, decimalDigits, rateToBase, archived)

// -- Budget -----------------------------------------------------------------

fun BudgetDto.toDomain(): Budget? {
    if (id.isBlank() || categoryId.isBlank()) return null
    return Budget(
        id = id,
        categoryId = categoryId,
        subcategoryId = subcategoryId?.takeIf { it.isNotBlank() },
        limitMinor = if (limitMinor < 0) 0L else limitMinor,
        currencyCode = currencyCode,
        period = parseYearMonth(period),
        sortOrder = sortOrder,
        archived = archived,
    )
}

fun Budget.toDto() = BudgetDto(
    id = id,
    categoryId = categoryId,
    subcategoryId = subcategoryId,
    limitMinor = limitMinor,
    currencyCode = currencyCode,
    period = period?.toString(),
    sortOrder = sortOrder,
    archived = archived,
)

// -- Budget alert state -----------------------------------------------------

fun BudgetAlertDto.toDomain(): BudgetAlert? {
    if (budgetId.isBlank()) return null
    val month = parseYearMonth(period) ?: return null
    // A threshold this build does not recognise is dropped rather than kept: it
    // could only have come from a newer build, and holding it would suppress an
    // alert this build does not know it is suppressing.
    if (threshold !in BudgetAlertThresholds.all) return null
    return BudgetAlert(budgetId = budgetId, period = month, threshold = threshold)
}

fun BudgetAlert.toDto() = BudgetAlertDto(
    budgetId = budgetId,
    period = period.toString(),
    threshold = threshold,
)

// -- Payment method ---------------------------------------------------------

fun PaymentMethodDto.toDomain(): PaymentMethod? =
    if (id.isBlank()) null else PaymentMethod(id, name, iconKey, sortOrder, archived)

fun PaymentMethod.toDto() = PaymentMethodDto(id, name, iconKey, sortOrder, archived)

// -- Settings ---------------------------------------------------------------

fun SettingsDto.toDomain() = AppSettings(
    baseCurrencyCode = baseCurrencyCode.ifBlank { AppSettings.DEFAULT_BASE_CURRENCY },
    themeMode = parseEnum(themeMode, ThemeMode.SYSTEM) { ThemeMode.valueOf(it) },
    firstDayOfWeek = parseEnum(firstDayOfWeek, DayOfWeek.MONDAY) { DayOfWeek.valueOf(it) },
    defaultSortOrder = parseEnum(defaultSortOrder, SortOrder.DATE_DESC) { SortOrder.valueOf(it) },
    defaultPaymentMethodId = defaultPaymentMethodId?.takeIf { it.isNotBlank() },
    budgetAlertsEnabled = budgetAlertsEnabled,
    autoBackupEnabled = autoBackupEnabled,
    backupsToKeep = backupsToKeep.coerceIn(1, 50),
    initialized = initialized,
)

fun AppSettings.toDto() = SettingsDto(
    baseCurrencyCode = baseCurrencyCode,
    themeMode = themeMode.name,
    firstDayOfWeek = firstDayOfWeek.name,
    defaultSortOrder = defaultSortOrder.name,
    defaultPaymentMethodId = defaultPaymentMethodId,
    budgetAlertsEnabled = budgetAlertsEnabled,
    autoBackupEnabled = autoBackupEnabled,
    backupsToKeep = backupsToKeep,
    initialized = initialized,
)

// -- Parsing helpers --------------------------------------------------------

private inline fun <T> parseEnum(raw: String, fallback: T, parse: (String) -> T): T = try {
    parse(raw.trim().uppercase())
} catch (e: IllegalArgumentException) {
    fallback
}

internal fun parseDate(raw: String): LocalDate? = try {
    if (raw.isBlank()) null else LocalDate.parse(raw.trim())
} catch (e: DateTimeParseException) {
    null
}

internal fun parseTime(raw: String): LocalTime? = try {
    if (raw.isBlank()) null else LocalTime.parse(raw.trim())
} catch (e: DateTimeParseException) {
    null
}

internal fun parseInstant(raw: String?): Instant? = try {
    if (raw.isNullOrBlank()) null else Instant.parse(raw.trim())
} catch (e: DateTimeParseException) {
    null
}

internal fun parseYearMonth(raw: String?): YearMonth? = try {
    if (raw.isNullOrBlank()) null else YearMonth.parse(raw.trim())
} catch (e: DateTimeParseException) {
    null
}
