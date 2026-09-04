# Project Status

**Last updated:** 2026-09-04 (Phase 7)
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
| **5** | Dashboard | ✅ **Done — 151 tests green, lint clean** |
| **6** | Budgets + local alerts | ✅ **Done — 192 tests green, lint clean** |
| **7** | Calendar | ✅ **Done — 225 tests green** |
| **8** | Reports & charts | ✅ **Done — 249 tests green** |
| ~~9~~ | ~~Currencies & manual exchange rates~~ | ❌ **Cut — app is PKR-only** |
| **9** | Import/Export (JSON + CSV) | ✅ **Done — 290 tests green** |
| **10** | Polish | ✅ **Done — 298 tests green** |
| **11** | Unit tests & docs | ✅ **Done — 341 tests green, lint clean** |

Full phase detail: [PROJECT_PLAN.md](PROJECT_PLAN.md).

**Multi-currency is cut from the product.** Every amount is PKR. The `Currency` model,
`CurrencyConverter` and `currencies.json` stay in the codebase so the JSON schema does not need a
breaking change, but `DefaultData` seeds PKR alone and no screen ever offers a currency choice.
The last currency affordances are now out of the UI as well — see *Fixing the app to PKR* below —
so this is enforced by the code, not just by convention.

---

## Commits

One commit per phase, pushed to `origin/main` as soon as it is made.

| Commit | Phase |
|---|---|
| `cf67f8a` | Phase 0–1: docs, Gradle setup, Material 3 theme, navigation skeleton |
| `8e3eee8` | Phase 2: data layer — money, file store, repositories |
| `6dbd470` | Phase 3: transactions — add, edit, detail, history |
| `3345dfa` | Phase 4: categories, subcategories and payment methods |
| `ac377de` | Phase 5: dashboard |
| `e44382d` | Dashboard: rolling ten-day average |
| `0325c02` | Phase 6: budgets and local alerts |
| `3721335` | Phase 7: calendar, and the cut to PKR only |
| `52de1ad` | Fix the app to PKR: remove every currency option |
| `463b06c` | Phase 8: reports and charts |
| `97f24e5` | Phase 9: import and export |
| `68a86d0` | Phase 10: polish |
| `a66db6b` | Phase 11: tests, docs and the lint pass |

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
whole-app `backup/backup-<date>.json` snapshot needs the import pipeline, which is Phase 9. The
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

## What was built in Phase 5

The dashboard — the first screen the user sees, and the first that has to *interpret*
their data rather than just list it.

- `domain/usecase/GetDashboardSummaryUseCase.kt` — every figure on the screen, derived
  in one pass over two month shards
- `domain/usecase/GetBudgetStatusUseCase.kt` — budget usage, split out because Phase 6
  needs exactly the same derivation
- `ui/charts/DailySpendBars.kt` — the first hand-drawn `Canvas` work: a seven-day bar
  chart and the share bar used by budgets and the category breakdown
- `ui/screen/home/` — balance card, today / daily average, the week chart, budget strip,
  top categories, recent transactions and quick actions

**151 unit tests, all green** (31 new). Lint is clean.

### Four decisions worth knowing about

**The dashboard loads two month shards, not all of them.** The current month and the one
before it. The previous month earns its place twice: the month-on-month comparison needs
both sides, and for the first week of any month most of the seven-day window is last
month. Older history stays on disk until a report asks for it, so startup cost does not
grow with the size of the ledger.

**A rolling ten-day average rather than a monthly one.** The plan said monthly average,
but a true one means loading every shard the user has ever written — exactly the cost the
lazy-loading design exists to avoid — and averaged over months where the current one is
partial, the figure is not meaningful anyway. A rolling window is also better than
month-to-date, which is one day of noise on the 1st and so smoothed by the 30th that a
change in habit takes weeks to surface.

Ten days rather than seven, deliberately: a seven-day window always holds exactly one of
each weekday, so a weekly rhythm — the big Sunday shop — sits at a fixed weight and the
average barely moves. Ten days cuts across that rhythm. The chart stays at seven so its
weekday labels still mean something, and the average's caption names its own window so
the two cannot be confused.

The divisor is the window length **or** how long the user has actually been tracking,
whichever is shorter: dividing someone's first day by ten would report a figure they have
never spent in a day, on the strength of nine days that predate the app. A future-dated
transaction cannot shorten it, so a mistyped year cannot inflate the average. A real
multi-month average belongs in Phase 8, where the user picks the period explicitly.

**The month-on-month comparison is like-for-like.** Eleven days of this month are
compared against the first eleven days of last month, not against last month's total.
Comparing a part-month against a whole one would report a fall every single month, which
is worse than saying nothing. When there is no previous spending to compare against, the
screen says so instead of reporting "up 100%" — a jump from zero is not a percentage.

**Budget usage is a plain sum.** *(Written when the app was multi-currency; that scope has
since been cut — everything is PKR, so there is no conversion step. See CLAUDE.md rule 7.)*
The budget's `Currency` is still resolved in the use case rather than the screen, because
formatting needs the symbol and decimal digits: a code alone renders a rupee limit as
"PKR 3,000.00" rather than "Rs. 3,000.00".

### The chart, and why it is seven small canvases

There is no charting dependency in this project, so the bars are `Canvas` draws. Each bar
is its own small canvas inside a `Row` rather than one canvas spanning the chart: the day
labels then lay themselves out with real text layout instead of hand-measured glyph
positions, and each bar holds its own animation without the chart having to care how many
bars there are.

Bar heights are relative to the busiest day in the window, so the shape of a week reads
the same whether the user spends hundreds or hundreds of thousands. That means the chart
shows **proportion, not magnitude** — the figures beside it carry the actual amounts. A
day with real spending never rounds away to an invisible bar, and the whole chart has a
spoken alternative, because a bar chart conveys nothing to a screen reader.

### The budget strip is built but cannot appear yet

Nothing can create a budget until Phase 6, so `summary.budgets` is always empty on a real
device today and the section renders nothing. The derivation, the four status thresholds
and the currency handling are all in place and covered by tests — Phase 6 adds the
editor, not the maths.

---

## What was built in Phase 6

Budgets, and the first thing in the app that speaks to the user when they are not
looking at it.

- `domain/usecase/BudgetUseCases.kt` — save (with the duplicate check), archive, delete
- `domain/usecase/EvaluateBudgetAlertsUseCase.kt` — decides which alerts are due
- `domain/usecase/GetBudgetHistoryUseCase.kt` — a budget's recent months
- `domain/model/BudgetAlert.kt` + `data/repository/FileBudgetAlertStateRepositoryImpl.kt` —
  the record of what has already been announced
- `notification/` — `NotificationChannels`, `BudgetAlertNotifier`
- `ui/screen/budget/` — the list with a month stepper and archived section, and the
  editor with the budget's own history under the fields

**192 unit tests, all green** (41 new). Lint is clean.

Phase 5 had already written `GetBudgetStatusUseCase` for the dashboard strip, so the
derivation was not rewritten — it was extended with `progressFor`, which computes one
budget's usage for one month *without* the `appliesTo` filter. History needs that: an
archived budget, or one pinned to a single month, still has real figures for the months
it was in force, and those are exactly the budgets a user looks back at.

### Where "once per threshold per period" lives

In its own file, `app-data/budget-alerts.json`, behind a seventh repository.

It did not belong in `settings.json`. That file is a set of preferences, written on every
preference change; this is a log that gains an entry per budget per threshold per month.
Mixing them would mean rewriting the alert history every time the user flipped a theme.

The file is pruned to three months on every write, so it cannot grow without bound over
years of use, and it is the one file in the app that is **not** re-seeded when missing —
an absent record means "nothing has been announced yet", which is already the correct
starting state.

### The decision and the notification are separate

`EvaluateBudgetAlertsUseCase` decides; `BudgetAlertNotifier` posts. The split is what
makes the fiddly half testable on the JVM — an off-by-one in the threshold comparison
means either silence at 100% or a notification on every app start, and both take weeks to
notice in production.

Three rules came out of that, each with a test:

**Only the highest newly-crossed threshold is announced, but all crossed thresholds are
recorded.** One large expense can cross 75, 90 and 100 at once, and three notifications
about one purchase is three reasons to turn alerts off.

**An alert is recorded as shown only once it has actually been posted.** If the
permission is denied the alert stays pending and fires when it is granted, rather than
being silently consumed while the shade was closed.

**Falling back below a threshold does not re-arm it.** Deleting and re-adding an expense
would otherwise re-announce a crossing the user has already been told about.

Alerts are evaluated on app start and after a transaction saves — spending only changes
when something is recorded, so a background worker would be a dependency and a wakeup
budget spent to learn nothing.

### Permission, asked in context

`POST_NOTIFICATIONS` is requested from the budgets screen, and only once there is at
least one live budget with alerts enabled. Asking on first launch — before the user has
any budgets — is a permission prompt with no visible reason attached to it, and the
easiest kind to deny for good.

### Budgets are not reference-counted

Categories, subcategories and payment methods are archive-don't-delete
because a transaction points at them. Nothing points at a budget: usage is derived from
the ledger, never recorded against the limit, so deleting one orphans nothing. Delete is
therefore a plain delete behind a confirmation, and Archive is offered separately for
anyone who wants the record kept. Deleting also clears that budget's alert records, so a
future budget that reused the id cannot inherit an "already announced" state it never
earned.

---

## What was built in Phase 7

The calendar — a month grid with each day's figures on it, a day sheet, and
previous / next / today.

- `domain/usecase/GetMonthCalendarUseCase.kt` — `MonthCalendar` and `CalendarDay`;
  the grid, the per-day figures, the month summary and the peak, all derived in one
  pass
- `ui/screen/calendar/` — the screen, the month summary card, the grid and the day
  detail sheet
- `core/money/MoneyFormatter.formatCompact` — written in Phase 2 for exactly this and
  used here for the first time; gained a `signed` flag
- `ui/navigation/Routes.kt` — the add route takes an optional `date`

**220 unit tests, all green** (28 new). Lint is deferred: it runs once after Phase 11
and everything it finds is fixed in one pass, rather than per phase.

Nothing was rewritten. The month read is `observeRange` over a `DateRange`, the same
shape `BudgetListViewModel` uses for its stepper. The day sheet's rows are
`TransactionRow`. The per-day bars are `ShareBar` from `ui/charts/` — already a
hand-drawn `Canvas` proportion bar, so the calendar added no drawing code of its own.

### Built twice, because the product changed underneath it

The calendar was finished and green against the old contract, which had multiple
currencies: cells carried a `mixedCurrency` flag and the grid captioned itself
"including amounts converted using your manual rates", per what was then rule 5.

Multi-currency was then cut — **Paisa is PKR-only** — so that came straight back out:
`CalendarDay.mixedCurrency` and `MonthCalendar.mixedCurrency` are gone, the converted
caption and the day sheet's converted note are gone, and the tests that seeded USD
records to prove conversion went with them. A flag that can never be true and a badge
that can never show are worse than nothing: they leave a cut decision lying around
looking like an unfinished feature.

What stayed is `CurrencyTable`. It is still how the screen gets the `Currency` that
`MoneyFormatter` needs for the symbol and `decimalDigits`, and per-day sums still go
through `toBase` — an identity conversion now, kept so a hand-edited or imported file
carrying some other code is normalised rather than silently summed as though it were
rupees. There is one test for exactly that.

Note that `mixedCurrency` still exists on `TransactionTotals`, `DashboardSummary` and
`BudgetProgress` from Phases 2–6, and `home_converted_note` is still wired into the
dashboard and budgets screens. Removing it app-wide is one sweep across every screen
at once, not something to do a phase at a time.

### Why the calendar derives its own days

`ObserveTransactionsUseCase` already groups by day, and its `TransactionSection`
carries a date, a label, the items and the day's net. That is the right shape for a
list and the wrong one for a grid, which needs four things it does not have: whole
rows of seven regardless of where the month starts, days borrowed from the
neighbouring months to fill those rows out, income and expense held **apart** per
day, and a peak to scale the bars against. So `GetMonthCalendarUseCase` derives them,
rather than the grid composable computing anything — CLAUDE.md again.

Income and expense are kept apart deliberately. A day that took in Rs. 5,000 and
spent Rs. 5,000 nets to zero, and a single net figure cannot tell it from a day where
nothing happened at all.

### The borrowed days are real, but they do not count

The first and last rows need days from the months either side, so the grid loads the
range it *displays* rather than the month — at most three shards.

Those days show their true figures. Spending on the 31st and the 1st is one continuous
stretch to the person who lived it, and blanking the cells would make the boundary
look like a gap in the data. They are also tappable, and tapping one **moves the grid
to that month**: opening a sheet for the 1st of October while the header still said
September would leave the user unsure which month they had come back to.

They are excluded from everything that claims to be about *this* month — the totals,
the active-day count, the busiest day, and the peak the bars scale against. The peak
matters most: one large day in August would otherwise flatten every bar in September.

### A day cell has to say two things in about 48dp

Each cell carries the day number, then up to two figures, then a bar.

The figures are `formatCompact` with the symbol dropped — `-1.2K`, `+4.5K` — and one
caption under the grid names the unit instead, because this is the only screen in the
app that shows a bare number and "1.2K of what?" is a fair question.

Each figure always shows its sign. At that size colour is doing most of the work of
separating income from expense, and CLAUDE.md does not allow red/green to be the only
signal, so `formatCompact` gained a `signed` parameter to match `format`. Building the
sign in the composable was the alternative, and money strings come from one place only.

The bar under them is that day's expense as a fraction of the month's busiest day. It
encodes spending as **length**, which survives colour blindness, a greyscale screen
and a glance — and it is what makes the shape of a month readable without reading a
single figure. Today is a filled disc and days with activity a thin ring, so "which
day is it" and "did anything happen" are also not colour alone.

### What a screen reader user actually needs from a month view

A 42-cell grid where every cell reads "12, minus 850 rupees" is accessible and
unusable. Four things, in order of how much they help:

**The month is stated in one focus stop.** The summary card speaks as a single
sentence — the month, what was spent, what came in, and how many of its days had
money on them. That is the shape of the month, and it does not cost 42 swipes.

**The busiest day is a button.** It sits on the summary card as a real, tappable row
that jumps straight to that day's sheet. It is the single day most worth reaching, and
it is now reachable without touring the grid. Sighted users get the shortcut too.

**Each cell speaks once, as a sentence.** `clearAndSetSemantics` collapses the number,
the two figures and the bar into "Tue, 12 Sep. Spent Rs. 850, received Rs. 4,500." —
one stop, not four. A quiet day says "nothing recorded" rather than going silent,
because silence is indistinguishable from a bug.

**Cells that carry nothing are not in the tree at all.** A quiet day borrowed from a
neighbouring month exists only to square off a row. Up to twelve of those are dropped
from the semantics tree entirely, and each week row is an `isTraversalGroup`, so a
swipe past a group skips a whole week instead of stepping through it a day at a time.

The column headings are marked decorative: every cell already speaks its own weekday.

### The add button had to learn a date

The day sheet offers "add an expense", and an add screen that opens on today when the
user is looking at the 3rd is a wrong answer they have to notice and undo. So the add
route took an optional `date`, `AddEditTransactionViewModel` an `initialDate`, and the
sheet passes the day it is showing. An unparseable argument falls back to today rather
than crashing, since it arrives as a string and may have been through process death.

This is the one thing in Phase 7 that reached outside the calendar, and it is a single
optional parameter with a default, so nothing else changed.

---

## What was built in Phase 8

Reports — the first screen that has to make a period of spending *comparable*
rather than just add it up.

- `domain/usecase/BuildReportUseCase.kt` — `Report`, `CategorySlice`,
  `SubcategorySlice`, `DailyPoint`, `MonthlyPoint`; every figure and every series in
  one pass over one read
- `ui/charts/DonutChart.kt` — the category ring plus its legend
- `ui/charts/PeriodCharts.kt` — `DailyExpenseBars`, `IncomeExpenseTrend`,
  `LabelledShareRow`
- `ui/screen/reports/` — period chips, custom range picker, overview, donut with
  subcategory drill-down, daily bars, monthly trend, biggest expenses
- `core/time/PeriodFilter.label` — one place that names a period

**249 unit tests, all green** (24 new).

### The read is bounded on purpose

A report could justify loading every shard the user has ever written. Instead the
read is the union of three spans: the period itself, six months of trend, and — only
for periods of two months or less — the preceding window to compare against. "This
month" is about seven files; "this year" is twelve. Only `AllTime` reads everything,
because that is exactly what the user asked for.

That widening is why the use case filters period membership itself rather than
trusting the repository: records outside the period arrive on purpose, to feed the
trend, and they must not leak into the totals. There is a test for each direction —
August spending shows in the trend and stays out of September's total.

### Two figures that are easy to get wrong

**The daily average divides by the period, not by the days that had spending.**
Rs. 9,000 across two days of a 30-day month is Rs. 300 a day, not Rs. 4,500. Dividing
by the days that happened to have entries reports a number the user has never
averaged. Same half-up `Long` division as the dashboard — no `Double` anywhere near
a displayed figure.

**The comparison is the preceding window of equal length, and it stays absent when it
would lie.** No previous spending means no percentage, because a jump from zero is
not "up 100%". A period longer than 62 days gets no comparison at all rather than a
second year-long read for one number.

### Why the daily chart disappears on long periods

365 bars on a phone is one pixel each — a picture of nothing. Past 62 days
`dailySeries` comes back empty and the screen says so, pointing at the monthly trend
that does cover it. The trend runs the other way: a single-month report would give a
one-point line, which is a dot and says nothing about direction, so a short period
still gets six months of context.

### Colour is never the only signal — three times over

**The donut** pairs every arc with a legend row carrying the name, amount and
percentage, so it reads in greyscale and with any form of colour blindness. The ring
is one animation rather than one per slice: the arcs share a running start angle, and
animating them independently tears the ring apart mid-transition. A non-zero slice
never sweeps less than 1.5°, so a small real amount cannot render as absent.

**The trend** separates income from expense by three things, not one: solid line with
filled dots against dashed line with hollow rings, plus a named legend whose key
repeats the actual dash pattern. Someone who cannot separate the two hues still has
the pattern and the marker shape.

**The daily bars** are one canvas for the whole row, not one per bar — unlike the
dashboard's seven-day chart this can carry sixty, and sixty composables each with
their own animation is a lot of machinery to draw sixty rectangles. Every chart has a
spoken alternative, and each says what the *shape* is telling a sighted reader — the
span, how many days had spending, and the peak — rather than reading sixty values.

### The drill-down keeps its own arithmetic honest

Tapping a category breaks it into subcategories whose shares are of **that category**,
not of the period. Spending filed under a category with no subcategory chosen becomes
a real named "Unspecified" bucket rather than being dropped, so the breakdown still
adds up to the category total — there is a test for exactly that sum.

Changing period clears the drill-down. A subcategory breakdown for a category that
may not even appear in the new period is stale figures under a heading that still
looks current.

### One small thing about the range picker

Material's date range picker hands back UTC-midnight millis, so the dates are read
back in UTC. Reading them in the device zone shifts the boundary by a day for anyone
west of Greenwich. Both ends are required before Save enables — a half-chosen range
has no meaning, and defaulting the missing end to today would quietly report a period
the user never asked for.

---

## What was built in Phase 9

Import and export — the first phase whose failure mode is losing the user's data
rather than showing them a wrong number.

- `data/csv/Csv.kt` — RFC 4180, written by hand
- `data/dto/Dtos.kt` — `BackupFile`, the whole-app document
- `domain/model/AppSnapshot.kt` — the same thing as domain objects, plus `CsvRow`
- `domain/model/ImportPlaceholders.kt` — the records an import invents so nothing
  goes missing
- `domain/repository/BackupRepository` + `data/repository/FileBackupRepositoryImpl` —
  the codec and the rolling snapshots
- `domain/usecase/BackupUseCases.kt` — export JSON, export CSV, prepare (validate and
  preview), commit, and the local snapshot
- `ui/screen/backup/` — the screen, SAF wiring and the preview dialog
- `JsonFileStore.writeRaw` / `readRaw` — atomic writes for already-serialised text

**290 unit tests, all green** (41 new).

### Validate → preview → confirm → commit, and why the split is real

`PrepareImportUseCase` **writes nothing**. It parses, assembles the complete candidate
state, repairs anything that would not resolve, and hands back an `ImportPreview`
carrying both the counts to show and the finished candidate. `CommitImportUseCase`
then writes that candidate unchanged.

That means a commit cannot discover a problem halfway through and leave the app
holding a mixture of two datasets — CLAUDE.md rule 8. There is a test asserting that
validation leaves the store untouched, because "the preview step is side-effect free"
is exactly the kind of property that quietly stops being true.

A Replace snapshots to `backup/` first and **aborts if the snapshot fails**. Replacing
everything with no way back is worse than not importing at all.

### Duplicate handling, in both directions

**A JSON backup re-imported is a no-op.** Ids match, so every record counts as a
duplicate and is skipped. There is a test that imports the same file twice and asserts
the second pass changes nothing — a silent doubling is the worst kind of import bug,
because it corrupts every total the user looks at afterwards.

**A merge never overwrites.** An id collision means the user already has that record,
so the incoming copy loses. "Add what is new" must not be quietly destructive.

**A hand-made CSV has no ids**, so one is derived from the row's own date, amount and
description. Importing that file twice is also a no-op, which is the behaviour someone
who exported, edited and re-imported actually expects.

### What the two formats are for

JSON round-trips exactly and is the real backup — there is a test asserting the
subcategory, notes, time and date all survive.

CSV is for spreadsheets and carries transactions only, with ids resolved to **names**
because a person is going to read it. That makes it the lossy direction, and the
importer compensates: names are matched case-insensitively, and a category or payment
method the file mentions but the app lacks is **created** rather than dropped.
Dropping it would move the money to "Uncategorised" and quietly change every report.

Amounts are written as minor units, as an integer. A spreadsheet that helpfully
reformats `350.50` is precisely the drift the `Long` model exists to prevent, and
there is a test asserting the string `350.50` never appears in an export.

### The CSV parser is a character scan, not a line split

Splitting on newlines before handling quotes is the classic way to corrupt any note
the user pressed return inside. So the parser walks characters, tracks whether it is
inside quotes, treats a doubled `""` as one literal quote, accepts LF / CRLF / CR, and
skips a UTF-8 BOM — because files round-tripped through Excel on Windows arrive with
one, and without that the first column name never matches.

Sixteen tests cover it, including a round trip of every awkward value: embedded
commas, quotes, newlines, CRLF, padding and unbalanced quotes.

### No storage permission, at all

Everything goes through the Storage Access Framework. The user picks the file and the
system hands back a URI scoped to that one document, so the manifest asks for nothing.
The export document is built **before** the picker opens and held in state, because
building it twice would be wasted work and could stamp two different export times onto
one file.

### Layering, corrected mid-phase

The first cut had the use cases importing `JsonFileStore`, `BackupFile` and
`DefaultData` directly — a domain-to-data dependency CLAUDE.md forbids. That is now
fixed: `BackupRepository` is a domain interface speaking `AppSnapshot` and `CsvRow`,
`FileBackupRepositoryImpl` owns everything DTO-, JSON- and RFC-4180-shaped, and the
placeholder factories moved into `domain/model/ImportPlaceholders.kt` because
"never drop a record whose category is absent — invent the category" is a domain
policy, not seed data. The repository also exposes `schemaVersion`, so the importer
can refuse a newer file without knowing where the number comes from.

`grep -r "import com.amteen.paisa.data" domain/` returns nothing again.

### Two smaller decisions

**A newer backup is refused, not partially read.** It reuses the existing
`AppError.SchemaTooNew`, whose message already names both versions. Guessing could
silently drop fields this build cannot represent, and during a Replace that is
unrecoverable.

**A CSV-driven Replace leaves budgets and settings alone.** A CSV carries neither, so
treating "replace everything" literally would wipe data the file never claimed to
cover. Tested.

---

## What was built in Phase 10

Polish — and the phase where a fair amount of code got **deleted**.

- `ui/screen/settings/` — the settings screen those preferences never had
- `ui/screen/about/` — the three promises the app is built on, in the user's terms
- `data/seed/SampleData.kt` + `domain/usecase/SampleDataUseCases.kt` — a seeded,
  reversible sample ledger
- Deleted: `ui/components/PlaceholderScreen.kt`, every `mixedCurrency` field, four
  unused strings

**298 unit tests, all green** (8 new).

### Every placeholder is now a real screen

`PlaceholderScreen` had done its job since Phase 1 and is gone, along with the
`coming_soon` string. Nothing in the navigation graph says "arrives in Phase N" any
more.

Two of those placeholders were hiding a genuine gap. `AppSettings` has carried
`themeMode`, `firstDayOfWeek`, `defaultSortOrder`, `budgetAlertsEnabled` and
`backupsToKeep` since **Phase 2**, and there was no way to change any of them. The
settings screen is the missing half rather than new state — the theme control works
the moment it is tapped, because `MainActivity` was already collecting `themeMode`.

`firstDayOfWeek` is the one worth calling out: the calendar grid and the "this week"
period have both honoured it since Phase 7, against a value the user could not reach.

### The dead-currency sweep, finished

Phase 7's commit noted `mixedCurrency` was still a constructor parameter on
`TransactionTotals`, `DashboardSummary` and `BudgetProgress`, always passed `false`.
It is now gone from all three, along with `CurrencyTable.sumConverted` and
`ConvertedTotal` — which turned out to have **no callers at all** — and the last live
converted note, on the history screen's totals card.

The tests that asserted conversion behaviour went with it. Four of them seeded a USD
currency at rate 280 to prove amounts were converted before summing, sorting or
filtering; none of that is reachable now, so they were rewritten in PKR where the
underlying rule still matters (the amount filter still excludes out-of-range records,
the amount sort still orders both ways) and dropped where it did not.

`CurrencyConverterTest` stays as it is. `CurrencyConverter` is still the only place
rounding happens, and testing it directly costs nothing.

### Sample data, and why it is seeded rather than random

`SampleData` uses a fixed seed, so the same call always produces the same ledger. A
random generator makes a screenshot unrepeatable and a bug report impossible to follow
up — "the daily average looks wrong" is only actionable if the data can be regenerated
exactly.

It is also **shaped** rather than uniform: salary on the 1st, rent on the 3rd, bills
mid-month, groceries clustered at weekends, roughly a fifth of days empty, and one
outlier a month. Uniform noise makes every chart in the app look identical and hides
the very bugs a chart has — a flat trend line, a donut of equal slices and a calendar
with no gaps would all look fine while being wrong.

Nothing is ever dated in the future, because a forward-dated record skews the
dashboard's rolling average and reads as a bug rather than as sample data.

**Adding it is not a one-way door.** Every generated id carries a `sample-` prefix,
which is what lets removal be surgical: seeding merges (a user who already recorded
something real keeps it) and clearing removes only prefixed records and prefixed
budgets. There is a test that a seed-then-clear round trip returns the ledger to
byte-identical state, and another that a user's own budget survives the clear.

Clearing does one whole-store write rather than a delete per record — several hundred
individual deletes would rewrite the same month shards over and over.

### Accessibility and touch targets, where they were actually wrong

The settings chips get `heightIn(min = 48.dp)`, because Material chips are shorter
than that by default and every one of these is a real control. Each `FilterChip`
already reports its own selected state, so a screen reader says "selected" instead of
leaving the user to infer it from a fill colour.

The switch row speaks as one node — `"Alert me at 75%, 90% and 100%, on"` — rather
than as a label and a separate control, which is two stops for one setting.

### One thing deliberately not done

The plan lists "dark-mode audit" and a "TalkBack pass". Both are **device work**, not
code work: every screen has light and dark previews and a spoken alternative, but
whether the result actually reads well is a judgement made looking at a phone. Phase 3
already recorded the lesson that no unit test catches a layout constraint. Flagging it
rather than claiming it.

---

## What was built in Phase 11

Test gaps closed, the docs brought back in line with the code, and the single lint
pass that had been deferred since Phase 4.

- `data/mapper/DtoMappersTest` — the wire-format boundary, 20 cases
- `core/time/DateFormattersTest`, `core/time/PeriodFilterTest` — the shared date logic
- `docs/SampleCsvTest` — the shipped sample file must actually import
- `docs/sample-data/` — a README and a hand-editable `transactions.csv`
- README: the acceptance table, the currency section, the testing section

**341 tests, all green** (43 new). **Lint: 0 errors, 19 warnings** — down from 1 error
and 42.

### The tests worth having added

**The mappers.** A mapper bug is the quiet kind: the file parses, the app starts, and
a field is silently wrong or a record has vanished. Twenty cases cover the two rules
that matter — a malformed record is *dropped rather than invented*, and a domain
object survives a round trip unchanged. The pointed ones: a transaction with no
readable date is dropped (an amount has to sit on some day to be summed at all), a
negative stored amount is corrected rather than trusted, blank optional strings become
null rather than empty, and a hand-edited settings file with `themeMode: NEON` falls
back instead of stopping the app from starting.

**Period resolution.** Shared by history, the dashboard, the calendar and reports, so
an off-by-one here is an off-by-one in every figure the app shows. Including the
31st-of-March-back-to-February trap, leap years, and that a week starting on today's
own weekday starts *today* rather than a week ago.

**The shipped sample CSV.** One test whose entire job is to fail when the documented
format and the real importer stop agreeing — far more likely than either changing on
its own. It caught a real bug immediately: the CSV I had just written contained bare
quotes inside an unquoted field, which is malformed per RFC 4180. The file was wrong,
not the parser.

### What the lint pass found

**One error, and it was real.** `Csv.kt` contained a literal byte-order mark, because
the BOM-skipping code was written with the character itself rather than the `﻿`
escape. An invisible BOM sitting in a source file is both unreadable and, as lint
correctly says, an error. Now a named constant.

**Six `ConstantLocale` warnings, and they were a genuine bug.** `DateFormatters` built
its six `DateTimeFormatter`s once, in `val`s, from `Locale.getDefault()`. If the user
changed their device language, the whole app carried on formatting dates in the old
one until the process was killed. They are now cached *per locale* rather than per
process — same allocation saving, without the staleness.

**Twelve unused resources, and five of them were the interesting kind.**
`title_add_expense`, `title_add_income`, `title_edit_transaction` and
`title_transaction_details` were unused because those screens had **hardcoded English
titles** instead. That is a localisation bug wearing an unused-resource costume, so
the fix was to use the strings, not delete them. The other seven were genuinely dead
and went.

**Four strings became plurals** — the single-quantity ones in the import preview.

### The nineteen warnings left, and why

**Fourteen are dependency upgrades** — AGP 8.13 → 9.4, Kotlin 2.2 → 2.4, the Compose
BOM, and so on. CLAUDE.md says to stop and ask before touching the dependency list,
and a major AGP bump is not something to slip into a docs phase. Left for you to
decide.

**Four are `PluralsCandidate` on strings that cannot cleanly be plurals.** Android
plurals select on exactly one quantity, and these have either two independent ones
("Recorded on 14 of 30 days"), four ("12 transactions, 3 categories, 2 payment
methods, 1 budget"), or a constant that can never be 1 (the sample data's six months).
Restructuring them to satisfy lint would make the strings worse to read for no user
benefit.

**One is `ObsoleteSdkInt` on `mipmap-anydpi-v26`.** I tried the suggested fix —
renaming the folder to `mipmap-anydpi` — and AAPT then could not resolve
`mipmap/ic_launcher` at all, so the build failed. Reverted. A cosmetic warning is not
worth a broken launcher icon.

### Still device work

The dark-mode audit and TalkBack pass remain a judgement made looking at a phone, as
noted in Phase 10. Every screen has light and dark previews and a spoken alternative;
whether they read *well* is not something `assembleDebug` can tell you.

---

## Fixing the app to PKR

Phase 7 removed the calendar's own currency surface. This finished the job across
every screen, because the app was **not actually PKR-only yet** — it only looked that
way on a fresh install.

### The bug that made this urgent

Three currency controls were still in the code, each guarded by
`if (state.currencies.size > 1)`: the code dropdown on the amount field, the currency
chips in the budget editor, and a currency filter in the history screen. On a fresh
install the seed writes one currency, the guard is false, and nothing renders — which
is why this looked finished.

But the pre-cut builds seeded **eight** currencies, and `FileBackedCollection` only
writes the seed when the file is **missing**. Every install made by one of those
builds — including the emulator this project has been tested on since Phase 3 — still
has all eight in `currencies.json`. On those installs the guard is true and all three
controls come back. The app was one upgrade away from offering a currency picker again.

### What changed

**`CurrencyRepository` is read-only.** `upsert`, `setBaseCurrency`, `archive`,
`hardDelete` and `replaceAll` are gone from the interface — not deprecated, removed.
None had a caller outside the implementation. "The app cannot end up with a second
currency" is now a property of the type rather than a promise about call sites.

**`FileCurrencyRepositoryImpl` normalises on read.** Whatever the file holds, the
repository exposes exactly one PKR entry, forced to `rateToBase = 1.0` and
`archived = false`. A legacy eight-currency file collapses; a file where PKR was
archived or rebased is repaired; a file with no PKR at all falls back to the seed.
The file itself is left alone — it is harmless, and this app does not rewrite user
data on read.

**Every currency control is deleted, not guarded.** The amount field shows a fixed
`Rs.` symbol, the budget editor a fixed prefix, and the history screen has no currency
filter. `TransactionQuery.currencyCodes`, `AddEditTransactionEvent.CurrencySelected`,
`BudgetEditEvent.CurrencySelected` and `TransactionHistoryEvent.CurrencyToggled` are
gone with them. A dropdown that can only ever hold one entry was also a tap on the
critical path of the one flow that has to stay under ten seconds.

**The converted badge is gone app-wide.** `home_converted_note` had three live render
sites — the dashboard's month card, its budget rows, and the budgets screen's totals
and per-budget cards — each behind a `mixedCurrency` flag that can no longer be true.
`BudgetListUiState.totalsMixedCurrency` went too.

**225 unit tests green** (5 new). The five that matter most are in
`FileCurrencyRepositoryImplTest`, and they exist specifically to fail if the
normalisation is ever removed: the legacy eight-currency file must collapse to PKR
alone, an archived-or-rebased PKR must be repaired, a file with no PKR must fall back
to the seed, and a corrupt file must still yield a usable currency.

### What deliberately stayed

`Currency`, `CurrencyTable`, `CurrencyConverter` and `currencies.json` all survive, as
CLAUDE.md requires: `MoneyFormatter` needs somewhere to read the symbol and
`decimalDigits` from, and the JSON keeps its shape so no schema bump was needed. Sums
still route through `CurrencyTable.toBase` — an identity conversion now — so a
hand-edited or imported record carrying a foreign code is normalised rather than
silently added up as though it were rupees.

`mixedCurrency` remains as a constructor parameter on `TransactionTotals`,
`DashboardSummary` and `BudgetProgress`, always passed `false`. Removing those fields
touches five use cases and their tests to delete a value nothing reads; it is worth
doing, but as its own change rather than folded in here.

One thing to know: if the emulator has non-PKR test transactions recorded during
earlier phases, their stored `currencyCode` is untouched (this app never rewrites
historical amounts) and they will now be read at rate 1.0 — so a $10 lunch reads as
Rs. 10. That is test data from a cut feature; clearing app data resets it cleanly.

---

## All phases complete

| | |
|---|---|
| Phases delivered | 0–11, with the former Phase 9 (currencies) cut |
| Unit tests | **341**, all green, pure JVM |
| Lint | **0 errors**, 19 warnings (14 of them dependency upgrades awaiting a decision) |
| Screens | No placeholders left — every route is a real screen |

**Open, and deliberately so:**

1. **Dependency upgrades.** Fourteen lint warnings covering AGP, Kotlin, the Compose
   BOM, coroutines and serialization. CLAUDE.md forbids touching the dependency list
   without asking, and a major AGP bump needs its own change with its own testing.
2. **The device pass.** Dark mode and TalkBack, on a real phone. Phase 3 already
   recorded the lesson that no unit test catches a layout constraint — the `+Rs.`
   truncation bug was invisible to the whole suite.

The architectural rules every phase is held to are in [../CLAUDE.md](../CLAUDE.md) — money is never
a `Double`, writes are always atomic, reads recover rather than crash, and a composable renders
state rather than computing it.
