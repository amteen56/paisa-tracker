# Paisa — Offline Personal Expense & Finance Tracker

An Android personal finance tracker that works entirely offline. No account, no backend, no
internet, no analytics. All of your financial data lives in plain JSON files inside the app's
private storage on your own device, and you can export it, back it up, or take it elsewhere at
any time.

Built with Kotlin, Jetpack Compose, and Material 3.

---

## Philosophy

```
Simple · Offline · Private · Fast · Reliable · File-based
```

The core bet of this app is **speed of entry**. Recording a purchase should take five to ten
seconds:

```
Amount        800
Category      Food
Subcategory   Fast Food
Description   Burger
Date          Today
```

Everything else — dashboards, budgets, reports, charts — is derived from those records rather
than maintained as separate state, so the numbers can never drift out of sync.

### What this app deliberately does not have

No login. No registration. No cloud sync. No REST API or GraphQL. No Room, SQLite, Realm,
Firebase, or Supabase. No ads. No social features. No bank integrations. No tracking or
telemetry of any kind.

The app functions identically in airplane mode.

---

## Features

| # | Acceptance criterion | Status |
|---|---|---|
| 1 | Open the app without creating an account | 🚧 |
| 2 | Add an expense in seconds | 🚧 |
| 3 | Select a category and subcategory | 🚧 |
| 4 | Add income | 🚧 |
| 5 | View all transactions | 🚧 |
| 6 | Edit transactions | 🚧 |
| 7 | Delete transactions | 🚧 |
| 8 | Search transactions | 🚧 |
| 9 | Filter by date / category / type | 🚧 |
| 10 | Calendar view of financial activity | 🚧 |
| 11 | Create monthly budgets | 🚧 |
| 12 | See budget usage | 🚧 |
| 13 | Local budget alerts | 🚧 |
| 14 | Advanced charts | 🚧 |
| 15 | Daily / monthly / category reports | 🚧 |
| ~~16~~ | ~~Multiple currencies~~ | ❌ Cut — PKR only |
| ~~17~~ | ~~Manual exchange rates~~ | ❌ Cut — PKR only |
| 18 | Export all data as JSON | 🚧 |
| 19 | Export transactions as CSV | 🚧 |
| 20 | Import a previous JSON backup | 🚧 |
| 21 | Import the app's own CSV format | 🚧 |
| 22 | Work completely offline | 🚧 |
| 23 | Close and reopen without data loss | 🚧 |
| 24 | Handle corrupt/invalid import files safely | 🚧 |
| 25 | Dark / light / system themes | 🚧 |

*(This table is the project's definition of done. Marks flip to ✅ as each phase lands.)*

---

## Screenshots

_To be added once the UI phases land._

| Dashboard | Add Expense | Reports | Calendar |
|---|---|---|---|
| — | — | — | — |

---

## Build & Run

### Requirements

| | |
|---|---|
| JDK | 21 (the JetBrains Runtime bundled with Android Studio works) |
| Android SDK | compileSdk **36**, minSdk **26**, targetSdk **36** |
| Gradle | 9.0.0 (via the wrapper — do not install separately) |
| Kotlin | 2.x with the Compose compiler plugin |

`minSdk 26` is chosen so `java.time` (`LocalDate`, `LocalTime`, `Instant`, `YearMonth`) is
available natively without core-library desugaring. That covers roughly 99% of active devices.

### If `JAVA_HOME` is not set

`gradle.properties` already points the Gradle *daemon* at Android Studio's bundled JDK:

```properties
org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr
```

That is enough for Android Studio, but **the `gradlew` launcher script itself still needs
`JAVA_HOME`** before it can start. For command-line builds, set it for the session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Or set it permanently, once:

```powershell
[Environment]::SetEnvironmentVariable(
    "JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
```

### Commands

```powershell
.\gradlew.bat assembleDebug        # build the debug APK
.\gradlew.bat installDebug         # build and install on the connected device
.\gradlew.bat testDebugUnitTest    # run the JVM unit test suite
.\gradlew.bat lint                 # static analysis
```

Check for a connected device first with `adb devices`.

The debug build installs alongside a release build — it uses the applicationId suffix `.debug`.

### From Android Studio

Open the project root, let Gradle sync, then Run ▸ app.

---

## Project structure

```
app/src/main/java/com/amteen/paisa/
  core/
    money/          Money, MoneyFormatter, CurrencyConverter
    time/           DateRange, PeriodFilter, DateFormatters
    result/         AppResult, AppError

  domain/
    model/          Transaction, Category, Budget, Currency, PaymentMethod, AppSettings
    repository/     Repository interfaces (no Android or file types in the signatures)
    usecase/        SaveTransaction, GetDashboardSummary, GetBudgetStatus, BuildReport,
                    ExportJson, ImportJson, ExportCsv, ImportCsv, SeedSampleData, ...

  data/
    file/           JsonFileStore (atomic writes + recovery), FilePaths, BackupManager
    dto/            Serializable wire models, decoupled from the domain
    mapper/         DTO <-> domain mapping
    repository/     File-backed repository implementations
    seed/           DefaultData — predefined categories, currencies, payment methods
    csv/            CsvWriter, CsvReader (RFC 4180)
    migration/      SchemaMigrations

  di/               AppContainer — manual dependency injection, no Hilt
  notification/     BudgetAlertNotifier, NotificationChannels

  ui/
    theme/          Material 3 colors, typography, shapes
    navigation/     Routes, NavHost, bottom bar
    components/     Shared composables
    charts/         DonutChart, BarChart, LineChart — hand-drawn on Compose Canvas
    screen/         home, transaction, history, calendar, budget, reports,
                    category, currency, paymentmethod, settings, backup
```

### Architecture

```
Composable  →  ViewModel  →  UseCase  →  Repository  →  JsonFileStore  →  JSON file
```

Strictly one direction. Composables never touch files, never contain business logic, and never
format money themselves. Repositories hold the authoritative state in `StateFlow` and are the
single source of truth for the UI; all file I/O runs on `Dispatchers.IO`.

Dependency injection is a hand-written `AppContainer` rather than Hilt — the graph is small
enough that a DI framework would add build complexity without buying anything.

---

## Data model

### Money is never a floating-point number

Every amount is a `Long` count of **minor units**, paired with a currency that knows its own
decimal precision.

```
Rs. 350.50  →  amountMinor = 35050, currencyCode = "PKR", decimalDigits = 2
$10.00      →  amountMinor =  1000, currencyCode = "USD", decimalDigits = 2
¥500        →  amountMinor =   500, currencyCode = "JPY", decimalDigits = 0
```

Adding two `Money` values in different currencies throws rather than silently coercing.
Conversion is always explicit, through `CurrencyConverter`.

### Transaction

```kotlin
data class Transaction(
    val id: String,                 // UUID, generated on device
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
```

---

## File storage

### Layout

Everything lives under the app's private internal storage, so no storage permission is required
and the data is removed cleanly if the app is uninstalled.

```
<app internal files>/app-data/
    transactions/
        2026-08.json          one shard per month
        2026-09.json
    categories.json
    budgets.json
    currencies.json
    paymentmethods.json
    settings.json
    backup/
        backup-2026-09-02.json
```

**Transactions are sharded by month.** A single file would mean rewriting the entire history
(megabytes, at 10,000 transactions) on every single save. With shards, adding an expense
rewrites only the current month — a few hundred records — so saves stay fast no matter how many
years of history accumulate.

### Writes are atomic

A write never truncates the only copy of a file:

```
serialize  →  write X.json.tmp  →  flush + fsync  →  atomic rename over X.json
```

If the process dies mid-write, the original file is still intact — the rename either happened or
it didn't. Each file is guarded by its own mutex.

### Reads recover rather than crash

| Situation | Behaviour |
|---|---|
| File missing | Fall back to defaults, write seed data |
| File empty | Fall back to defaults |
| Invalid JSON | Move aside to `X.json.corrupt-<timestamp>`, restore from newest backup, else defaults — and tell the user |
| `schemaVersion` newer than the app | Refuse to read or overwrite; prompt the user to update the app |
| Interrupted write | The `.tmp` file is discarded; the last good file is used |

### Automatic backups

A rolling snapshot is written to `backup/` on app start, keeping a configurable number of copies
(default 5). This is insurance against corruption, not a substitute for exporting your own JSON.

---

## Multi-currency

Each transaction permanently keeps the currency it was recorded in. Changing your base currency
never rewrites historical amounts.

Because the app has no internet access, exchange rates are **entered manually by you**. Each
currency stores a single rate relative to the base currency:

```
Base currency: PKR
1 USD = 280 PKR
1 AED =  76 PKR
```

Cross-currency conversion derives from those anchors rather than storing every pair, which keeps
the rate table consistent and O(n) instead of O(n²).

Conversion happens at **read time only** — when a report, dashboard total, or budget needs to
combine currencies. Rounding occurs once, half-up, at the target currency's precision. Anywhere
the app shows a converted figure it is labelled:

> Converted using your manual rates.

---

## Budgets

Budget usage is **derived from transactions**, never stored:

```
spent  = Σ convert(transaction.amount → budget.currency)
         for EXPENSE transactions in the budget's month
         matching the budget's category (and subcategory, if set)

usage  = spent / limit
```

| Usage | Status |
|---|---|
| 0–74% | Normal |
| 75–89% | Warning |
| 90–99% | Critical |
| 100%+ | Exceeded |

Because usage is derived, editing or deleting a transaction automatically corrects every budget,
total, and chart — there is no cached number that can go stale.

Budget alerts are generated locally on-device at the 75%, 90%, and 100% thresholds. They fire
once per threshold per period, require no server, and can be turned off in Settings.

---

## JSON backup format

The full backup is the primary export format and is versioned so future releases can migrate old
files.

```json
{
  "schemaVersion": 1,
  "appVersion": "1.0.0",
  "exportedAt": "2026-09-02T14:32:10Z",
  "settings": {
    "baseCurrencyCode": "PKR",
    "themeMode": "SYSTEM",
    "budgetAlertsEnabled": true
  },
  "currencies": [
    { "code": "PKR", "name": "Pakistani Rupee", "symbol": "Rs.", "decimalDigits": 2, "rateToBase": 1.0,   "archived": false },
    { "code": "USD", "name": "US Dollar",       "symbol": "$",   "decimalDigits": 2, "rateToBase": 280.0, "archived": false }
  ],
  "categories": [
    {
      "id": "cat-food",
      "name": "Food",
      "applicableTo": "EXPENSE",
      "iconKey": "restaurant",
      "colorArgb": -1499549,
      "sortOrder": 0,
      "archived": false,
      "subcategories": [
        { "id": "sub-fastfood", "name": "Fast Food", "archived": false }
      ]
    }
  ],
  "paymentMethods": [
    { "id": "pm-cash", "name": "Cash", "archived": false }
  ],
  "transactions": [
    {
      "id": "8f14e45f-ceea-467a-9d0b-3c7e1a2b4c5d",
      "type": "EXPENSE",
      "amountMinor": 80000,
      "currencyCode": "PKR",
      "categoryId": "cat-food",
      "subcategoryId": "sub-fastfood",
      "description": "Burger",
      "date": "2026-09-01",
      "time": "20:30",
      "paymentMethodId": "pm-cash",
      "notes": "Lunch with friends",
      "createdAt": "2026-09-01T20:31:02Z",
      "updatedAt": "2026-09-01T20:31:02Z"
    }
  ],
  "budgets": [
    {
      "id": "bud-food-sep",
      "categoryId": "cat-food",
      "subcategoryId": null,
      "limitMinor": 2000000,
      "currencyCode": "PKR",
      "period": "2026-09",
      "archived": false
    }
  ]
}
```

Note that `amountMinor: 80000` is Rs. 800.00 — minor units, as everywhere else in the app.

---

## CSV export

Columns, in this exact order:

```
ID,Type,Date,Time,Amount,Currency,Category,Subcategory,Description,Payment Method,Notes,Created At,Updated At
```

```csv
ID,Type,Date,Time,Amount,Currency,Category,Subcategory,Description,Payment Method
EXP-001,EXPENSE,2026-09-01,20:30,800.00,PKR,Food,Fast Food,Burger,Cash
EXP-002,EXPENSE,2026-09-01,18:00,350.00,PKR,Shopping,Household,Water Bottle,Cash
```

Escaping follows RFC 4180:

- Fields containing a comma, a double quote, or a newline are wrapped in double quotes
- Literal double quotes are doubled — `say "hi"` becomes `"say ""hi"""`
- Multi-line notes are preserved inside quotes
- Files are UTF-8 with a BOM, so Excel opens Urdu, Arabic, and emoji correctly
- Amounts are written in major units with the currency's own decimal precision, and the currency
  code is always in its own column so mixed-currency exports are unambiguous

---

## Import

Import is **all-or-nothing**. A malformed file never leaves your data half-updated.

```
Validate  →  Preview  →  Confirm  →  Commit
```

The file is fully parsed and validated into an in-memory candidate state before anything is
written. You then see what was found:

```
Found:
  1,250 transactions
     25 categories
      8 budgets

  12 look like duplicates of existing entries

○ Merge with existing data
○ Replace existing data
```

Duplicates are detected first by transaction `id`, then by the tuple
`(type, date, time, amount, currency, description)`. You choose whether to skip them, keep both,
or overwrite.

Before a **Replace**, the current data is snapshotted to `backup/` — so even the destructive
option is recoverable.

CSV import auto-maps the app's own header row and offers manual column mapping for anything else.

---

## Testing

```powershell
.\gradlew.bat testDebugUnitTest
```

The suite is pure JVM — no emulator needed. It covers balance and budget arithmetic, currency
conversion, date and category filtering, CSV round-tripping (including commas, embedded quotes,
newlines, and non-Latin text), JSON serialization and schema migration, duplicate detection,
recalculation after edits and deletions, and `JsonFileStore`'s corruption and interrupted-write
recovery paths.

Representative cases:

```
Income 100,000 − Expenses 40,000            → Balance 60,000
Budget 20,000, Spent 15,000                 → 75% used, 5,000 remaining
$10 at a manual rate of 280                 → Rs. 2,800
```

---

## Privacy

> Your financial data is stored locally on your device. The application does not require an
> account or send financial information to a remote server.

There is no analytics SDK, no crash reporting service, no advertising identifier, and no network
permission used for your data. The app requests `POST_NOTIFICATIONS` on Android 13+ solely to
show your own budget alerts, generated on-device.

Your data is yours: export it as JSON or CSV whenever you like, and take it with you.

---

## License

MIT
