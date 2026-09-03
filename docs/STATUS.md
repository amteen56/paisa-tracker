# Project Status

**Last updated:** 2026-09-03
**Repo:** `paisa-tracker` · **Package:** `com.amteen.paisa` · **Branch:** `main`

---

## Where we are

| Phase | Scope | Status |
|---|---|---|
| **0** | README, CLAUDE.md, .gitignore | ✅ **Done** |
| **1** | Gradle setup, theme, navigation skeleton | ✅ **Done — `BUILD SUCCESSFUL`** |
| **2** | Data layer (models, Money, JsonFileStore, repositories) | ✅ **Done — 55 tests green** |
| 3 | Transactions (add/edit/delete/list/search) | 🔨 **In progress** |
| 4 | Categories & subcategories | ⬜ |
| 5 | Dashboard | ⬜ |
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

One commit per phase. Nothing is pushed yet — the remote has no `main` branch.

| Commit | Phase |
|---|---|
| `cf67f8a` | Phase 0–1: docs, Gradle setup, Material 3 theme, navigation skeleton |

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

## Next: Phase 3 — transactions

The data layer is done, so this is the first phase the user can actually *see*. Deliverables:

- `ui/components/` — `AmountText`, `TransactionRow`, `ConfirmDialog`, `ErrorState`
- `ui/icons/CategoryIcons.kt` — stable `iconKey` → `ImageVector`, with a fallback
- `ui/screen/transaction/` — the shared add/edit form (type-switched), plus the detail screen
- `ui/screen/history/` — search, filters, 4 sort modes, sticky date headers
- `di/ViewModelFactories.kt`, and the real screens registered in `PaisaNavHost`

The bar for the add flow is **5–10 seconds** for "Rs. 800, Food / Fast Food, Burger" — autofocused
amount field, numeric keyboard, category chips rather than a dropdown. Any change that adds a tap
to that flow has to justify itself.

**Also still open:** the emulator was left mid-recovery before the restart — a `-wipe-data` cold
boot was in progress and never confirmed. See
[EMULATOR_TROUBLESHOOTING.md](EMULATOR_TROUBLESHOOTING.md) step 5 before running on device.

The architectural rules every phase is held to are in [../CLAUDE.md](../CLAUDE.md) — money is never
a `Double`, writes are always atomic, reads recover rather than crash, and a composable renders
state rather than computing it.
