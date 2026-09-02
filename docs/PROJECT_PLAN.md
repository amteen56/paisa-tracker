# Project Plan — Paisa

The agreed architecture and phased build plan. Current progress is tracked in [STATUS.md](STATUS.md).

---

## Goal

A polished Android personal finance app that works with **zero backend, zero account, zero
network**. All state lives in local JSON files owned by the user. The product bet is *speed of
entry*: recording "Rs. 800, Food / Fast Food, Burger" must take 5–10 seconds.

## Decisions locked with the user

1. **Charts** — hand-rolled Compose `Canvas`. No charting dependency.
2. **Storage** — transactions sharded by month; everything else single-file.
3. **Delivery** — phased, with a checkpoint after each milestone.
4. **Optional scope** — budget notifications ✅, auto local backups ✅, sample data ✅,
   **app lock ❌ (out of V1)**.

---

## Architectural review

Problems found in the original requirements, and how this plan resolves them.

**P1 — Whole-file rewrites don't survive 10,000 transactions.**
A single `transactions.json` grows to ~3 MB and every add rewrites all of it. Resolved by month
shards: a write touches only `2026-09.json` (~300 records). Reads are served from an in-memory
`StateFlow` cache hydrated at startup, so the UI never blocks on I/O.

**P2 — Budgets vs. multi-currency was under-specified.**
A budget is `Rs. 20,000 PKR` but expenses may be in USD. Rule: **budget usage is always computed
in the budget's own currency**, converting each expense at read time. Historical amounts are never
rewritten. The UI shows a "converted using manual rates" badge whenever a period mixes currencies.

**P3 — Exchange rates need an anchor.**
Storing arbitrary pairs is O(n²) and can go inconsistent. Each currency instead stores **one rate
relative to the base currency**; cross-conversion derives as `amount / rate[from] * rate[to]`.
Changing the base currency rebases the table in one pass.

**P4 — "Don't hard-delete referenced categories" needed a concrete rule.**
Categories, subcategories, payment methods and currencies get `archived: Boolean`. Hard delete is
permitted only at reference count 0; otherwise the UI offers Archive. Archived items disappear
from pickers but still render in history and reports.

**P5 — Import must be all-or-nothing.**
Parse → validate → build a complete in-memory candidate state → only then commit. A snapshot is
written to `backup/` before any Replace import.

**P6 — Money must never be a `Double`.**
`Long` minor units throughout with per-currency `decimalDigits`. Conversion is the only place
rounding occurs, half-up at the target currency's precision.

---

## Project structure

```
app/src/main/java/com/amteen/paisa/
  PaisaApp.kt          Application; owns AppContainer
  MainActivity.kt      single Activity, hosts NavHost

  core/
    money/   Money, MoneyFormatter, CurrencyConverter
    time/    DateRange, PeriodFilter, DateFormatters
    result/  AppResult, AppError

  domain/
    model/       Transaction, TransactionType, Category, Subcategory, Budget,
                 Currency, PaymentMethod, AppSettings, ThemeMode
    repository/  6 interfaces — no Android/File/DTO types in the signatures
    usecase/     SaveTransaction, DeleteTransaction, GetDashboardSummary,
                 GetBudgetStatus, BuildReport, SearchTransactions,
                 ExportJson, ImportJson, ExportCsv, ImportCsv, SeedSampleData

  data/
    file/        JsonFileStore (atomic tmp+fsync+rename, recovery), FilePaths, BackupManager
    dto/         *Dto + BackupDto — wire format, decoupled from domain
    mapper/      DtoMappers
    repository/  File*RepositoryImpl (6)
    seed/        DefaultData — predefined categories, currencies, payment methods
    csv/         CsvWriter, CsvReader (RFC 4180)
    migration/   SchemaMigrations

  di/            AppContainer, ViewModelFactories — manual DI, no Hilt
  notification/  BudgetAlertNotifier, NotificationChannels

  ui/
    theme/       Color, Type, Shape, Theme
    navigation/  Routes, PaisaNavHost, BottomBar
    components/  AmountText, EmptyState, ErrorState, CategoryChip,
                 TransactionRow, SectionCard, ConfirmDialog, AmountKeypad
    charts/      DonutChart, BarChart, LineChart, ChartAxis, ChartLegend
    screen/      home, transaction, history, calendar, budget, reports,
                 category, currency, paymentmethod, settings, backup, more
```

### Layering

```
Composable → ViewModel → UseCase → Repository → JsonFileStore → JSON file
```

Strictly one direction. Enforcement rules live in [../CLAUDE.md](../CLAUDE.md).

---

## Data models

```kotlin
data class Transaction(
    val id: String,                 // UUID
    val type: TransactionType,      // EXPENSE | INCOME
    val amountMinor: Long,          // always > 0; direction comes from `type`
    val currencyCode: String,
    val categoryId: String,
    val subcategoryId: String?,
    val description: String,
    val date: LocalDate,
    val time: LocalTime,
    val paymentMethodId: String?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Category(
    val id: String, val name: String,
    val applicableTo: CategoryScope,    // EXPENSE | INCOME | BOTH
    val iconKey: String, val colorArgb: Int,
    val sortOrder: Int, val archived: Boolean,
    val subcategories: List<Subcategory>,
)

data class Currency(
    val code: String, val name: String, val symbol: String,
    val decimalDigits: Int,             // PKR 2, USD 2, JPY 0
    val rateToBase: Double,             // manual; base currency == 1.0
    val archived: Boolean,
)

data class Budget(
    val id: String, val categoryId: String, val subcategoryId: String?,
    val limitMinor: Long, val currencyCode: String,
    val period: YearMonth?,             // null = recurring monthly
    val archived: Boolean,
)
```

`Money(amountMinor, currencyCode)` arithmetic across two different currencies **throws** rather
than silently coercing.

---

## File storage

```
<app internal files>/app-data/
    transactions/2026-08.json     one shard per month
    transactions/2026-09.json
    categories.json
    budgets.json
    currencies.json
    paymentmethods.json
    settings.json
    backup/backup-2026-09-02.json rolling, N kept (default 5)
```

**Writes:** serialize → `X.json.tmp` → `flush()` + `fd.sync()` → atomic rename over `X.json`.
Per-file `Mutex`, always on `Dispatchers.IO`.

**Reads recover rather than crash:**

| Situation | Behaviour |
|---|---|
| Missing / empty | Defaults, seed written |
| Invalid JSON | Move aside to `X.json.corrupt-<ts>`, restore newest backup, else defaults + warning |
| `schemaVersion` too new | Refuse to read or overwrite; prompt to update the app |
| Interrupted write | `.tmp` discarded; last good file used |

Startup hydrates the current and previous month eagerly; older shards load on demand.

---

## Budgets

Derived, never stored:

```
spent  = Σ convert(expense.amount → budget.currency) for the budget's month & category
usage  = spent / limit
status = <75 Normal | <90 Warning | <100 Critical | >=100 Exceeded
```

A subcategory budget does not double-count against its parent category budget; both are shown
independently.

---

## Import / export

`BackupDto` carries `schemaVersion = 1`, `exportedAt`, `appVersion`, plus settings, currencies,
categories, transactions, budgets and payment methods. `SchemaMigrations.migrate(json, from, to)`
is a chain of pure functions, so v1→v2 is one appended function — never edit an existing migration.

Pipeline: **Validate → Preview (counts + duplicates) → Confirm (Merge | Replace) → Commit.**
Duplicates are detected by `id`, then by `(type, date, time, amountMinor, currency, description)`;
the user picks Skip / Keep both / Overwrite. Replace snapshots existing data to `backup/` first.

CSV uses the spec's column order with RFC 4180 quoting and a UTF-8 BOM for Excel.

---

## Edge cases the tests must cover

Amount `0` / negative / `"1.2.3"` / non-Latin digits / `Long` overflow · leading-zero and
comma-grouped input · far-future and far-past dates · **editing a transaction across a month
boundary** (must move shards *and* recompute both months) · deleting the last transaction in a
shard · deleting a referenced category · archiving the base currency · rate of `0` or negative ·
budget on an archived category · CSV with comma + quote + newline in one field · truncated JSON
mid-write · import referencing an unknown `categoryId` · duplicate IDs within one import file.

---

## Phases

Each ends with a compiling build and a review checkpoint.

**Phase 0 — Docs.** README, CLAUDE.md, .gitignore. ✅

**Phase 1 — Skeleton.** Gradle KTS + version catalog, `compileSdk 36` / `minSdk 26`, Material 3
theme (light/dark + `ExpenseColors`), NavHost + bottom bar + FAB, all screens stubbed. ✅

**Phase 2 — Data layer.** Models, `Money`, DTOs + mappers, `JsonFileStore` with atomic writes and
the recovery ladder, `DefaultData` seeds, 6 repositories, `AppContainer`, first-run init.
*Checkpoint: unit tests for `Money` and `JsonFileStore` corruption recovery.*

**Phase 3 — Transactions.** Add Expense / Add Income (shared type-switched form), edit, details,
delete-with-confirm, history with search + all filters + 4 sort modes, paged `LazyColumn` with
sticky date headers, numeric keypad + autofocus for ~5-tap entry.

**Phase 4 — Categories.** Categories/subcategories, icons, colors, drag-reorder, archive rule.

**Phase 5 — Dashboard.** Balance/income/expense card, today's spend, monthly average, top
categories, budget strip, recent transactions, quick actions.
*Checkpoint: first genuinely usable app.*

**Phase 6 — Budgets.** CRUD, derived usage, 4-state status, history, `BudgetAlertNotifier` at
75/90/100% (once per threshold per period; `POST_NOTIFICATIONS` on 13+).

**Phase 7 — Calendar.** Month grid with per-day totals, day detail sheet, prev/next/today.

**Phase 8 — Reports.** Overview, category donut, daily bar, monthly income-vs-expense trend, top
categories, top expenses, subcategory drill-down, period filters incl. custom range. All charts
hand-drawn on `Canvas`, animated, theme-aware, with text fallbacks for accessibility.
*Checkpoint: charts reviewed on device.*

**Phase 9 — Currencies.** Management, set base (rebases the rate table), archive, manual rate
editor with a live "1 USD = 280 PKR" preview.

**Phase 10 — Import/Export.** JSON backup/restore, CSV export/import, validate→preview→confirm→
commit, duplicate handling, `BackupManager` rolling backups, SAF so no storage permission is needed.

**Phase 11 — Polish.** Empty/error/loading states everywhere, transitions, TalkBack pass, touch
targets, dark-mode audit, sample-data seeder.

**Phase 12 — Tests + docs.** Full unit-test suite, README updates, `docs/sample-data/`.

---

## Verification

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat installDebug        # check `adb devices` first
```

Manual acceptance beyond the 25 criteria:

1. Add "Burger, 800, Food/Fast Food" and time it — target < 10 s.
2. Force-stop and reopen — data intact.
3. **Airplane mode on** — every feature still works.
4. Export JSON → wipe app data → import → totals identical.
5. Export CSV → open in Excel → verify quoting and Unicode.
6. Import a truncated JSON → refused cleanly, existing data untouched.
7. Toggle system/light/dark on every screen.
8. Seed sample data → every chart renders, no empty months.
