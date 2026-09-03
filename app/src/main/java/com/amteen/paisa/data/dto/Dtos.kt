package com.amteen.paisa.data.dto

import com.amteen.paisa.data.file.JsonFileStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk wire format.
 *
 * These are deliberately separate from the domain models, and deliberately dumb:
 * primitives and strings only, no `java.time` types, no computed properties, no
 * behaviour. That separation is what lets the JSON schema evolve without churning
 * the domain — and what lets the domain use rich types without dragging
 * serialization annotations through it. See CLAUDE.md.
 *
 * **Every field must have a default.** A file written by an older build has to keep
 * parsing, and a missing key must resolve to something sensible rather than throwing.
 *
 * Dates and times are ISO-8601 strings (`2026-09-02`, `14:30`, and an instant as
 * `2026-09-02T09:30:00Z`). Parsing lives in the mappers.
 */

/** Wrapper for every top-level file, carrying the version the reader checks first. */
private const val V = JsonFileStore.SCHEMA_VERSION

@Serializable
data class TransactionDto(
    val id: String = "",
    /** `EXPENSE` or `INCOME`. Unknown values fall back to EXPENSE in the mapper. */
    val type: String = "EXPENSE",
    val amountMinor: Long = 0L,
    val currencyCode: String = "",
    val categoryId: String = "",
    val subcategoryId: String? = null,
    val description: String = "",
    /** ISO local date, `uuuu-MM-dd`. */
    val date: String = "",
    /** ISO local time, `HH:mm` or `HH:mm:ss`. */
    val time: String = "00:00",
    val paymentMethodId: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SubcategoryDto(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
data class CategoryDto(
    val id: String = "",
    val name: String = "",
    /** `EXPENSE`, `INCOME` or `BOTH`. */
    val applicableTo: String = "EXPENSE",
    val iconKey: String = "category",
    /**
     * Stored as a signed 32-bit ARGB int. Kept as `Int` rather than a hex string
     * because it round-trips exactly and needs no parsing on the hot path.
     */
    val colorArgb: Int = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val subcategories: List<SubcategoryDto> = emptyList(),
)

@Serializable
data class CurrencyDto(
    val code: String = "",
    val name: String = "",
    val symbol: String = "",
    val decimalDigits: Int = 2,
    val rateToBase: Double = 1.0,
    val archived: Boolean = false,
)

@Serializable
data class BudgetDto(
    val id: String = "",
    val categoryId: String = "",
    val subcategoryId: String? = null,
    val limitMinor: Long = 0L,
    val currencyCode: String = "",
    /** `uuuu-MM`, or null for a budget that recurs every month. */
    val period: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class PaymentMethodDto(
    val id: String = "",
    val name: String = "",
    val iconKey: String = "wallet",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

@Serializable
data class SettingsDto(
    val baseCurrencyCode: String = "PKR",
    /** `SYSTEM`, `LIGHT` or `DARK`. */
    val themeMode: String = "SYSTEM",
    /** `MONDAY` … `SUNDAY`. */
    val firstDayOfWeek: String = "MONDAY",
    /** `DATE_DESC`, `DATE_ASC`, `AMOUNT_DESC`, `AMOUNT_ASC`. */
    val defaultSortOrder: String = "DATE_DESC",
    val defaultPaymentMethodId: String? = null,
    val budgetAlertsEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = true,
    val backupsToKeep: Int = 5,
    val initialized: Boolean = false,
)

// -- File roots -------------------------------------------------------------
//
// Each file's root object carries `schemaVersion` as its first key, which is what
// JsonFileStore reads before attempting to deserialize the rest.

@Serializable
data class TransactionsFile(
    @SerialName(JsonFileStore.KEY_SCHEMA_VERSION) val schemaVersion: Int = V,
    /** `uuuu-MM` — redundant with the filename, but makes a stray file diagnosable. */
    val month: String = "",
    val transactions: List<TransactionDto> = emptyList(),
)

@Serializable
data class CategoriesFile(
    @SerialName(JsonFileStore.KEY_SCHEMA_VERSION) val schemaVersion: Int = V,
    val categories: List<CategoryDto> = emptyList(),
)

@Serializable
data class CurrenciesFile(
    @SerialName(JsonFileStore.KEY_SCHEMA_VERSION) val schemaVersion: Int = V,
    val currencies: List<CurrencyDto> = emptyList(),
)

@Serializable
data class BudgetsFile(
    @SerialName(JsonFileStore.KEY_SCHEMA_VERSION) val schemaVersion: Int = V,
    val budgets: List<BudgetDto> = emptyList(),
)

@Serializable
data class PaymentMethodsFile(
    @SerialName(JsonFileStore.KEY_SCHEMA_VERSION) val schemaVersion: Int = V,
    val paymentMethods: List<PaymentMethodDto> = emptyList(),
)

@Serializable
data class SettingsFile(
    @SerialName(JsonFileStore.KEY_SCHEMA_VERSION) val schemaVersion: Int = V,
    val settings: SettingsDto = SettingsDto(),
)
