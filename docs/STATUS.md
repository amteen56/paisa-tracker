# Project Status

**Last updated:** 2026-09-02, end of session
**Repo:** `paisa-tracker` · **Package:** `com.amteen.paisa` · **Branch:** `main` (no commits yet)

---

## Where we are

| Phase | Scope | Status |
|---|---|---|
| **0** | README, CLAUDE.md, .gitignore | ✅ **Done** |
| **1** | Gradle setup, theme, navigation skeleton | ✅ **Done — `BUILD SUCCESSFUL`** |
| 2 | Data layer (models, Money, JsonFileStore, repositories) | ⬜ **Next** |
| 3 | Transactions (add/edit/delete/list/search) | ⬜ |
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

## ⚠️ Uncommitted work

**Nothing has been committed yet.** The entire project is working-tree only. Making the initial
commit is the first thing to do after the restart.

```
 M .gitignore
 M CLAUDE.md
 M README.md
?? app/
?? build.gradle.kts
?? gradle.properties
?? gradle/
?? gradlew
?? gradlew.bat
?? settings.gradle.kts
```

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

Suggested first commit message:

```
Phase 0-1: project docs, Gradle setup, Material 3 theme, navigation skeleton
```

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

## Next session: start here

1. **Commit Phase 0–1** (see above).
2. **Verify the emulator boots** — see [EMULATOR_TROUBLESHOOTING.md](EMULATOR_TROUBLESHOOTING.md).
   It was left mid-recovery; a `-wipe-data` cold boot was in progress when the PC was restarted.
3. **Begin Phase 2 — data layer.** Deliverables:
   - `core/money/` — `Money` (`Long` minor units), `MoneyFormatter`, `CurrencyConverter`
   - `domain/model/` — Transaction, Category, Budget, Currency, PaymentMethod, AppSettings
   - `domain/repository/` — the 6 interfaces
   - `data/file/JsonFileStore.kt` — atomic `tmp → fsync → rename` writes + the recovery ladder
   - `data/dto/` + `data/mapper/` — wire format decoupled from the domain
   - `data/seed/DefaultData.kt` — predefined categories, currencies, payment methods
   - `data/repository/` — the 6 file-backed implementations
   - `di/AppContainer.kt` — wired into `PaisaApp.onCreate()`
   - Unit tests for `Money` arithmetic and `JsonFileStore` atomicity/corruption recovery

The architectural rules that Phase 2 must follow are in [../CLAUDE.md](../CLAUDE.md) — money is
never a `Double`, writes are always atomic, reads recover rather than crash.
