# Project Status

**Last updated:** 2026-09-03 (Phase 4)
**Repo:** `paisa-tracker` · **Package:** `com.amteen.paisa` · **Branch:** `main`

---

## Where we are

| Phase | Scope | Status |
|---|---|---|
| **0** | README, CLAUDE.md, .gitignore | ✅ **Done** |
| **1** | Gradle setup, theme, navigation skeleton | ✅ **Done — `BUILD SUCCESSFUL`** |
| **2** | Data layer (models, Money, JsonFileStore, repositories) | ✅ **Done — 55 tests green** |
| **3** | Transactions (add/edit/delete/list/search) | ✅ **Done — 81 tests green, verified on device** |
| **4** | Categories, subcategories & payment methods | ✅ **Done — 120 tests green, lint clean** |
| 5 | Dashboard | ⬜ **Next** |
| 6 | Budgets + local alerts | ⬜ |
| 7 | Calendar | ⬜ |
| 8 | Reports & charts | ⬜ |
| 9 | Currencies & manual exchange rates | ⬜ |
| 10 | Import/Export (JSON + CSV) | ⬜ |
| 11 | Polish | ⬜ |
| 12 | Unit tests & sample data | ⬜ |

Full phase detail: [PROJECT_PLAN.md](PROJECT_PLAN.md).

---

## Commits

One commit per phase, pushed to `origin/main` as soon as it is made.

| Commit | Phase |
|---|---|
| `cf67f8a` | Phase 0–1: docs, Gradle setup, Material 3 theme, navigation skeleton |
| `8e3eee8` | Phase 2: data layer — money, file store, repositories |
| `6dbd470` | Phase 3: transactions — add, edit, detail, history |

Verify the identity before committing — this repo must **not** be authored by the global work
account:

```powershell
git config --local user.email     # must print mateena946@gmail.com
```

| Scope | Value |
|---|---|
| Global (work — not for this repo) | `Abdul Mateen <amateen@devnext.net>` |
| **This repo (local)** | `AbdulMateen <mateena946@gmail.com>` |
| Remote | `git@github-personal:amteen56/paisa-tracker.git` |

---

## What was built in Phase 2

The whole data layer, and it is testable on the JVM with no emulator — `JsonFileStore` takes a
plain `File` root, so the storage tests run against a real temp directory rather than a mock.

- `core/money/` — `Money` (`Long` minor units, throws on mixed-currency arithmetic, `Math.addExact`
  so overflow is reported rather than wrapped), `MoneyFormatter` (the only place a money string is
  built), `CurrencyConverter` (the only place rounding happens — `BigDecimal`, HALF_UP, once, at
  the target precision), `AmountParser` (grouping commas, leading/trailing dots, non-Latin digits)
- `core/time/` — `DateRange`, `PeriodFilter` (takes `today` as a parameter, so it is deterministic),
  `DateFormatters`
- `core/result/` — `AppResult` / `AppError`, so storage failures reach the UI as a message
- `domain/model/` — Transaction, Category, Budget, Currency, PaymentMethod, AppSettings,
  `CurrencyTable`, `TransactionDetails` / `TransactionQuery` / `TransactionTotals`
- `domain/repository/` — the 6 interfaces, free of Android, `File` and DTO types
- `data/file/JsonFileStore.kt` — atomic `tmp → fsync → rename`, a `.bak` sidecar kept on every
  write, per-file `Mutex`, and the full recovery ladder
- `data/dto/` + `data/mapper/` — wire format decoupled from the domain; every field defaulted
- `data/repository/` — 6 implementations; transactions sharded by month
- `data/migration/SchemaMigrations.kt` — empty chain, ready for the first bump
- `di/AppContainer.kt` — wired into `PaisaApp.onCreate()`, exposes `ready`

**55 unit tests, all green.** The ones that earn their keep: cross-month edit moves the record
between shards (and survives a restart), deleting the last transaction removes the shard file, a
truncated file is quarantined and recovered from the sidecar, a newer `schemaVersion` is neither
read nor overwritten, and 20 concurrent writes to one file do not interleave.

### Two decisions worth knowing about

**Recovery reads a `.bak` sidecar, not `backup/`.** `JsonFileStore` keeps the previous version of
each file as `X.json.bak` on every write and recovers from that. Restoring a single file out of a
whole-app `backup/backup-<date>.json` snapshot needs the import pipeline, which is Phase 10. The
sidecar gives real per-file recovery now, at the cost of one extra rename per write.

**A corrupt file is not re-seeded.** A *missing* file writes the seed; a *corrupt* one does not.
Silently replacing quarantined data with defaults would look to the user like their data vanished.

---

## Verified toolchain

| Tool | Value |
|---|---|
| JDK | 21.0.10 — `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `C:\Users\HP\AppData\Local\Android\Sdk` |
| Gradle | 9.0.0 (wrapper committed) |
| AGP | 8.13.0 |
| Kotlin | 2.2.20 |
| Compose BOM | 2025.09.00 · Navigation 2.9.5 · Lifecycle 2.9.4 |

Build output confirmed: `app/build/outputs/apk/debug/app-debug.apk` — 17.06 MB.

### Building from the command line

`gradle.properties` sets `org.gradle.java.home`, but that only configures the Gradle **daemon**.
The `gradlew.bat` **launcher** still needs `JAVA_HOME` or it exits immediately:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

To avoid setting it every session:

```powershell
[Environment]::SetEnvironmentVariable(
    "JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
```

Android Studio is unaffected — it supplies its own JDK.

---

## What was built in Phase 1

- [settings.gradle.kts](../settings.gradle.kts), [build.gradle.kts](../build.gradle.kts),
  [gradle/libs.versions.toml](../gradle/libs.versions.toml) — version catalog, all deps centralized
- [app/build.gradle.kts](../app/build.gradle.kts) — `compileSdk 36` / `minSdk 26` / `targetSdk 36`,
  config cache on, R8 + resource shrinking on release, `.debug` applicationId suffix
- [app/proguard-rules.pro](../app/proguard-rules.pro) — keeps kotlinx.serialization serializers
  (without these, release builds crash the first time they read `app-data`)
- [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) — **no `INTERNET` permission**;
  cloud backup disabled in `data_extraction_rules.xml`, device-to-device transfer allowed
- `ui/theme/` — full light/dark Material 3 schemes, plus an `ExpenseColors` composition local for
  income/expense/budget-status/chart colors. Material You is **off** by default so wallpaper can
  never make income and expense hard to distinguish. Amounts use `"tnum"` tabular figures.
- `ui/navigation/` — 20 destinations with `create(...)` helpers, animated bottom bar + FAB
- `ui/screen/more/MoreScreen.kt` — built for real; every other screen is a `PlaceholderScreen`
  that names the phase which will replace it

---

## What was built in Phase 3

The first phase that is visible on screen, and the app is now genuinely usable for recording money.

- `ui/components/` — `AmountText` / `NetAmountText`, `TransactionRow`, `ConfirmDialog`,
  `ErrorState`, `LoadingState`
- `ui/icons/CategoryIcons.kt` — stable `iconKey` → `ImageVector` with a fallback, so a category
  from a newer build still renders instead of crashing
- `ui/screen/transaction/` — one type-switched form backing both Add and Edit, plus the detail
  screen with delete-and-confirm
- `ui/screen/history/` — search across description/notes/category/payment method, filters by type,
  category, payment method and currency, 4 sort modes, period chips, sticky date headers
- `di/ViewModelFactories.kt` — hand-written factories; each names its exact dependencies rather
  than taking the whole container, so every ViewModel is constructible in a test

**Entry speed:** the amount field is first and autofocused so the keypad is already up, categories
are one-tap chips rather than a dropdown, the date defaults to today, the default payment method is
pre-selected, and notes stay collapsed until asked for.

### The layout bug worth remembering

Amounts rendered as `+Rs.` on the list and summary card while the detail screen showed
`+Rs. 150,000.00` correctly. `MoneyFormatter` was right the whole time — the text had `maxLines = 1`
with no overflow handling, so anything too wide was **silently** cut.

Three fixes: the row's amount column went from a fixed `116.dp` to `widthIn(min = 96.dp)`; the
summary card gives Net the full width and drops Income/Expense to a second row; and `AmountText`
now sets `TextOverflow.Ellipsis`, so a future overflow reads `+Rs. 150,0…` — visibly incomplete —
rather than looking like a legitimate smaller number.

**No unit test could have caught this.** It is a layout constraint, not logic. Screenshot review on
a real device is the only thing that finds it, which is why the definition of done includes it.

---

## What was built in Phase 4

Categories, subcategories and payment methods — the setup screens that make the rest of the app
configurable rather than fixed.

- `domain/usecase/CategoryUseCases.kt` — save (with subcategory reconciliation), count references,
  delete, archive, reorder
- `domain/usecase/PaymentMethodUseCases.kt` — the same set, plus set/clear the default
- `domain/usecase/ReferenceCounts.kt` — `ReferenceCount` and `RemovalOutcome`, shared by both
- `ui/screen/category/` — list (Expense/Income tabs, drag reorder, archived section) and editor
  (name, scope, colour, icon, subcategory rows)
- `ui/screen/paymentmethod/` — list plus an inline bottom-sheet editor and the default marker
- `ui/components/DragDropList.kt` — hand-rolled long-press drag reordering for `LazyColumn`
- `ui/components/IconPicker.kt`, `ColorPicker.kt`, `ui/theme/CategoryPalette.kt`

**120 unit tests, all green** (39 new). Lint is clean.

### Three decisions worth knowing about

**Reference counting includes budgets, not just transactions.** A budget names a `categoryId` too,
and a budget pointing at a deleted category is exactly as broken as a transaction pointing at one.
`CountCategoryReferencesUseCase` counts both; there is a test that a category referenced *only* by
a budget still blocks the delete, because counting transactions alone is the easy version of this
bug.

**Removing a subcategory in the editor may archive it rather than delete it.** The editor submits
the list of rows the user wants; `SaveCategoryUseCase` diffs that against what is on disk and, for
each removed row, asks whether any transaction still points at it. Referenced ones are kept with
`archived = true` and listed back to the user under "Kept for your history" with a Restore button.
Unreferenced ones are genuinely deleted. This is CLAUDE.md rule 4 applied one level down from
categories.

**Deleting or archiving a payment method clears it as the default.** `settings.json` names a
`defaultPaymentMethodId`, and leaving it pointing at something archived would pre-select an option
the user cannot see or change from the add screen.

### Reordering, and why there are two ways to do it

Drag-and-drop is written by hand in `DragDropList.kt` rather than pulled in as a dependency — the
dependency list is deliberately tiny and this is the only place that needs it. It works in item
**keys, not indices**, because a list with section headers and a trailing hint has indices that
mean nothing to the data behind it.

A long-press drag cannot be performed with TalkBack running, so it is never the only route:
every row also carries Move up / Move down in its overflow menu and publishes the same two as
semantics custom actions.

The new order is held in the ViewModel and written once when the finger lifts, not on every swap —
otherwise a drag across ten rows would be ten file writes for an order the user is still choosing.

### Two fixes outside the phase's own scope

**Archived items now stay visible in the form that already uses them.** Once categories can be
archived, editing an older transaction whose category has since been archived would have rendered
a chip row with nothing selected — the selection was intact, but invisible. Both
`AddEditTransactionViewModel.categoriesFor` and `AddEditTransactionUiState.subcategories` now
re-admit the currently-selected item even when archived. The type switch still drops a category
that does not apply to the new type; archiving is forgiven, inapplicability is not.

**The bottom navigation bar was overlapping list content.** `PaisaNavHost` discarded the outer
`Scaffold`'s padding entirely, so the bar sat on top of the last row of any list. It now consumes
the bottom inset. This was a pre-existing Phase 1 bug that Android lint flags as an error;
`./gradlew lint` passes as of this phase.

---

## Next: Phase 5 — dashboard

- Balance / income / expense card, today's spend, monthly average
- Top categories, budget strip, recent transactions, quick actions
- First screen where the hand-drawn `Canvas` work starts paying off

The architectural rules every phase is held to are in [../CLAUDE.md](../CLAUDE.md) — money is never
a `Double`, writes are always atomic, reads recover rather than crash, and a composable renders
state rather than computing it.
