# CLAUDE.md

Working contract for this repository. Read this before making changes.

**Project:** Paisa — an offline-first personal expense and finance tracker for Android.
**Stack:** Kotlin · Jetpack Compose · Material 3 · Coroutines/StateFlow · Navigation Compose ·
kotlinx.serialization · file-based JSON storage.
**Package:** `com.amteen.paisa` · **minSdk 26 / compileSdk 36 / targetSdk 36** · **JDK 21**

See [README.md](README.md) for the user-facing description, the data model, and the file formats.

---

## Hard constraints

These are product requirements, not preferences. Do not violate them, and do not "temporarily"
violate them to get something working.

### Never add

- **Any database** — Room, SQLite, Realm, ObjectBox, DataStore-as-a-database. Storage is JSON
  files, full stop. (`Preferences DataStore` is also out; settings live in `settings.json`.)
- **Any backend or network call** — REST, GraphQL, Firebase, Supabase, remote config, exchange-rate
  APIs. The app must work in airplane mode. There is no `INTERNET` permission.
- **Any account system** — login, registration, OAuth, cloud sync.
- **Any analytics, telemetry, crash reporting, or advertising SDK.** Zero. This is a privacy
  guarantee made to the user in the README.
- **Hilt/Dagger/Koin.** DI is the hand-written `AppContainer` in `di/`. The graph is small; a
  framework would add build cost for nothing.
- **A charting library.** Charts are hand-drawn on Compose `Canvas` in `ui/charts/`.

Before adding *any* new dependency, stop and ask. The dependency list is deliberately tiny.

### Money is `Long` minor units. Never `Double`, never `Float`.

```kotlin
// Rs. 350.50  →  amountMinor = 35050  with  decimalDigits = 2
```

- Amounts are stored, passed, summed, and compared as `Long`.
- `Double` appears in exactly one place: `Currency.rateToBase`, a user-entered exchange rate.
- Rounding happens in exactly one place: `CurrencyConverter`, half-up, at the target currency's
  precision.
- Formatting for display happens in exactly one place: `MoneyFormatter`. Composables must never
  build an amount string themselves.
- `Money` arithmetic across two different currencies throws. Do not add an implicit coercion.

### The dependency direction is one-way

```
Composable  →  ViewModel  →  UseCase  →  Repository (interface)  →  JsonFileStore  →  file
```

- A Composable must not contain business logic, do file I/O, perform currency conversion, or
  compute totals. It renders state and emits events.
- A ViewModel must not touch `File`, `Context`, or JSON. It calls use cases.
- Repository *interfaces* live in `domain/repository/` and must not reference Android types,
  `File`, or DTOs. Implementations live in `data/repository/`.
- Domain models must not carry serialization annotations. DTOs in `data/dto/` are the wire format,
  and `data/mapper/` maps between them. This separation is what lets the JSON schema evolve
  without churning the domain.

---

## Core rules

### 1. All file writes are atomic

Never write directly over a live file. Always:

```
serialize  →  write X.json.tmp  →  flush() + fd.sync()  →  atomic rename over X.json
```

Go through `JsonFileStore`. Do not open a `FileOutputStream` anywhere else. Every file is guarded
by a per-file `Mutex`, and all I/O runs on `Dispatchers.IO` — never on the main thread.

### 2. Reads recover; they do not crash

`JsonFileStore.read()` handles missing, empty, truncated, and unparseable files, plus a
`schemaVersion` newer than the app understands. A corrupt file is moved aside to
`X.json.corrupt-<timestamp>` and recovery is attempted from `backup/` before falling back to
defaults. Never let a parse exception reach the UI as a crash.

### 3. Transactions are sharded by month

`app-data/transactions/YYYY-MM.json`. A save touches only the affected shard.

**The easy bug here:** editing a transaction's date across a month boundary must *remove* it from
the old shard and *add* it to the new one, and both months' derived figures must refresh. There is
a unit test for this — keep it passing.

### 4. Archive, don't delete

Categories, subcategories, payment methods, and currencies use an `archived: Boolean`.

- Hard delete is permitted **only** when the reference count is zero.
- Otherwise the UI offers Archive, which hides the item from pickers but keeps it rendering
  correctly in history and reports.
- Existing transactions must always remain valid. A transaction must never end up pointing at a
  `categoryId` that no longer resolves.

### 5. Currency conversion happens at read time only

Stored transactions keep their original currency and amount forever. Changing the base currency
rebases the rate table — it never rewrites transaction amounts.

Each currency stores one `rateToBase`. Cross-conversion derives from those anchors; do not
introduce a pairwise rate table.

Any aggregate combining more than one currency must be visibly labelled as converted using the
user's manual rates.

### 6. Derive, don't store

Totals, balances, budget usage, category breakdowns, and report figures are computed from
transactions. Do not persist a running total — that is how numbers drift. Cache in memory only if
profiling shows an actual problem, and make the cache invalidation obvious.

### 7. Budget usage is computed in the budget's own currency

Convert each expense into the budget's currency before summing. Never compare a USD expense
against a PKR limit as if the numbers were the same unit.

Thresholds: `<75` Normal · `<90` Warning · `<100` Critical · `>=100` Exceeded.

### 8. Import is all-or-nothing

`Validate → Preview → Confirm → Commit`. Build the complete candidate state in memory and
validate it fully before writing anything. A partially applied import is a bug. Snapshot existing
data to `backup/` before a Replace.

---

## Conventions

### Naming

| Kind | Pattern | Example |
|---|---|---|
| Screen composable | `<Name>Screen` | `AddTransactionScreen` |
| ViewModel | `<Name>ViewModel` | `AddTransactionViewModel` |
| UI state | `<Name>UiState` (single immutable data class) | `HomeUiState` |
| UI events | `<Name>Event` (sealed interface) | `AddTransactionEvent` |
| Use case | verb-first, `UseCase` suffix, single `operator fun invoke` | `SaveTransactionUseCase` |
| Repository impl | `File<Name>RepositoryImpl` | `FileTransactionRepositoryImpl` |
| DTO | `<Name>Dto` | `TransactionDto` |

### Compose

- One state object in, one event lambda out. No `ViewModel` passed into child composables.
- Hoist state; keep leaf composables stateless and previewable.
- Use `@Preview` for non-trivial components, in both light and dark.
- Every screen needs an empty state, an error state, and a loading state — not just the happy path.
- Every interactive element needs a `contentDescription` and a touch target of at least 48dp.
- Lists are `LazyColumn` with stable keys. Never render 10,000 items eagerly.

### Coroutines

- ViewModels expose `StateFlow`, collected with `collectAsStateWithLifecycle()`.
- File I/O on `Dispatchers.IO`; no blocking calls on the main dispatcher.
- Repository state is a `MutableStateFlow` exposed as a read-only `StateFlow`.

---

## How to make common changes

**Add a screen** — add a route to `ui/navigation/Routes.kt`; create
`ui/screen/<feature>/<Name>Screen.kt` plus `<Name>ViewModel.kt` and `<Name>UiState.kt`; register
it in `ExpenseNavHost.kt`; add the factory to `ViewModelFactories.kt`. Handle the empty state.

**Add a persisted field** — add it to the domain model, then to the DTO with a default value so
old files still parse, then update the mapper. If old files cannot parse with a default, that is a
schema bump: see below.

**Add a repository** — interface in `domain/repository/`, implementation in `data/repository/`
delegating to `JsonFileStore`, wired in `AppContainer`. Add a file path to `FilePaths.kt`.

**Bump the schema** — increment `schemaVersion`, add a pure migration function to
`SchemaMigrations.kt` mapping `n → n+1`, and add a round-trip test with a real old-format fixture.
Never edit an existing migration; always append.

**Add a chart** — new file in `ui/charts/`, drawn on `Canvas`, driven by a plain data class from a
use case. It must read correctly in light and dark, animate on data change, and expose a text
alternative for accessibility.

---

## Commands

```powershell
.\gradlew.bat assembleDebug        # build
.\gradlew.bat installDebug         # install on the connected device (check: adb devices)
.\gradlew.bat testDebugUnitTest    # JVM unit tests — no emulator needed
.\gradlew.bat lint
```

`gradle.properties` already sets `org.gradle.java.home` to Android Studio's bundled JDK, but that
configures the *daemon* only — the `gradlew` launcher script still needs `JAVA_HOME` before it can
start. For command-line builds on this machine, export it first:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

**Always run `testDebugUnitTest` before considering a change done.** The calculation tests are the
safety net for the entire app — every total, budget, and chart depends on them.

---

## Definition of done for a change

- [ ] `assembleDebug` compiles
- [ ] `testDebugUnitTest` is green
- [ ] New logic has unit tests, especially anything touching money, dates, or files
- [ ] Works with the network fully off
- [ ] Renders correctly in light *and* dark
- [ ] Empty and error states handled
- [ ] No new dependency added without asking
- [ ] No `Double` used for an amount

---

## Git

This repository uses a **personal** identity, deliberately different from the global one.

| Scope | Value |
|---|---|
| Global (work — must not be used here) | `Abdul Mateen <amateen@devnext.net>` |
| **This repo (local)** | `AbdulMateen <mateena946@gmail.com>` |
| Remote | `git@github-personal:amteen56/paisa-tracker.git` |

The `github-personal` host alias in `~/.ssh/config` maps to `github.com` with
`IdentityFile ~/.ssh/id_github_personal` and `IdentitiesOnly yes`, so the work SSH key is never
offered for this repo.

Before the first commit in a fresh clone, verify:

```powershell
git config --local user.email      # must be mateena946@gmail.com
```

Do not change the global git config, and do not commit as the work account.

---

## Product philosophy

```
Simple · Offline · Private · Fast · Reliable · File-based
```

Do not add features merely because finance apps usually have them. Recurring transactions, split
transactions, investment tracking, receipt scanning, multi-account ledgers, bank imports, and
widgets are all **out of scope** unless explicitly requested.

The one metric that matters: adding an expense should take five to ten seconds. Any change that
adds a tap to that flow needs to justify itself.
