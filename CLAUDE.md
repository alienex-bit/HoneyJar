# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

All commands run from `android/` using PowerShell on Windows:

```powershell
# Debug build
powershell -Command "Set-Location 'C:\Users\Steve\Documents\HoneyJar\android'; & .\gradlew.bat assembleDebug"

# Release build
powershell -Command "Set-Location 'C:\Users\Steve\Documents\HoneyJar\android'; & .\gradlew.bat assembleRelease"

# Clean build
powershell -Command "Set-Location 'C:\Users\Steve\Documents\HoneyJar\android'; & .\gradlew.bat clean assembleDebug"

# Run unit tests
powershell -Command "Set-Location 'C:\Users\Steve\Documents\HoneyJar\android'; & .\gradlew.bat test"

# Run a single test class
powershell -Command "Set-Location 'C:\Users\Steve\Documents\HoneyJar\android'; & .\gradlew.bat testDebugUnitTest --tests 'com.honeyjar.app.ExampleUnitTest'"
```

There is no linter configured. Kotlin compilation errors surface via `compileDebugKotlin`.

The Room schema uses KSP (not kapt) — regenerate after any `@Entity` or `@Dao` changes by rebuilding.

## Repository Structure

```
android/          Android app (Kotlin + Jetpack Compose)
docs/             Design docs, architecture notes, and bug/feature audit files
```

The `docs/` folder contains important context:
- `HoneyJar_Notification_Findings.md` — bug audit of the notification capture pipeline
- `HoneyJar_Unimplemented_Features.md` — catalogue of stub/no-op features
- `project_architecture.md` — original design system reference (web prototype, pre-Android)

## Android Architecture

**Package layout:** `com.honeyjar.app`
- `services/` — `NotificationListenerService` (capture entry point)
- `repositories/` — `NotificationRepository` (singleton object), `SettingsRepository`, `PriorityRepository`
- `data/` — Room database, DAOs, entities, `HoneyEncryptor`, `ThemePrefs`
- `models/` — `HoneyNotification` domain model
- `ui/screens/` — one file per screen (Home, History, Stats, Settings, Onboarding)
- `ui/viewmodels/` — `MainViewModel` (shared across all screens via `MainActivity`)
- `ui/theme/` — `Theme.kt` defines `HoneyJarTheme`, `HoneyJarColors`, `LocalHoneyJarColors`
- `ui/components/` — `GlassCard`, `ContextualActionsSheet`
- `utils/` — `NotificationCategories` (category string constants), `TimeUtils`

## Data Flow

`NotificationService` (background) → `NotificationRepository.addNotification()` → Room (`NotificationDao`) → `NotificationRepository.notifications: Flow` → screens via `collectAsState()`

`NotificationService` calls `NotificationRepository.initialize()` in its own `onCreate()`. `MainActivity` also calls `initialize()` on startup — both are safe because `HoneyJarDatabase` is a proper singleton keyed to `applicationContext`.

**Notification IDs** are formed as `"${sbn.key}_${sbn.postTime}"` — stable across service reconnects. The DAO uses `OnConflictStrategy.IGNORE`, so `onListenerConnected` re-processing active notifications is idempotent.

## Settings Persistence

`SettingsRepository` wraps Android DataStore (`preferences`). All settings are accessed as `Flow<T>` and written with `suspend fun set*()`. The DataStore extension `Context.dataStore` is defined at the top of `SettingsRepository.kt` — only one `preferencesDataStore` per name can exist per process.

`ThemePrefs` uses SharedPreferences (not DataStore) and is an in-memory `mutableStateOf` singleton initialized in `MainActivity.onCreate()`.

## Theming

Four themes: `DarkHoney`, `Midnight`, `LightCream`, `LightMinimal` (enum `HoneyJarThemeType`).

Screens access theme-specific tokens via `LocalHoneyJarColors.current` (`HoneyJarColors` — not `MaterialTheme.colorScheme`). Both must be used: `MaterialTheme.colorScheme.primary` for standard M3 slots, `LocalHoneyJarColors.current.accent` / `.textPrimary` / `.glassBorder` / `.heroGradient` etc. for custom tokens.

Typography: `PlayfairDisplay` (italic branding/headers) and `Outfit` (all UI text). Both are defined as `FontFamily` vals in `Theme.kt`.

## Navigation

Single-activity (`MainActivity extends FragmentActivity`). Navigation uses `NavHost` with bottom bar tabs: Home, History, Stats, Settings. Onboarding is a separate nav destination that gates entry — the app redirects there if `hasCompletedOnboardingIntro` is false OR any required permission is missing (`areAllPermissionsGranted` in `OnboardingScreen.kt` checks notification access, battery optimization, storage, and DND access).

History accepts an optional `?filter={filter}` nav argument for deep-linking to a filtered view (used by the urgent notifications shortcut on Home).

## Room Database

`HoneyJarDatabase` version 5, entities: `NotificationEntity`, `NotificationStatsEntity`, `PriorityGroupEntity`. Migrations 3→4 and 4→5 are defined inline. `fallbackToDestructiveMigration()` is enabled — a missing migration drops the DB rather than crashing.

Category string keys (stored in `PriorityGroupEntity.key` and `HoneyNotification.priority`) must be lowercase and match the constants in `NotificationCategories`. The DB is seeded with defaults on first create via `DatabaseCallback.onCreate`.

## Known Unimplemented Areas

The following features exist in the UI but are stubs (see `docs/HoneyJar_Unimplemented_Features.md` for details):
- **StatsScreen** — all metrics are hardcoded strings; chart heights are random on every recomposition. Needs wiring to `MainViewModel.totalCount`, `actionedCount`, `dismissedCount`, and `allStats`.
- **Encryption** — `HoneyEncryptor` (AES-GCM via Android Keystore) is instantiated in `NotificationRepository` but the `encrypt()`/`decrypt()` path in `toEntity()`/`toModel()` requires the `iv` and `encryptedData` fields to be non-null. Currently, most stored notifications have null IV and are stored as plaintext.
- **Contextual actions** — `ContextualActionsSheet` presents mute, archive, snooze, unsubscribe, etc. Only `resolve` and `reply` are handled; all others are silent no-ops.
- **Focus Mode** — persists to DataStore but is never read by any filter.
- **Search** — `onClick = { /* Search */ }` on the search icon in HomeScreen.
- **Pro purchase** — no `BillingClient` integration; the buy button calls `onFinished()`.
- **Timestamps in HistoryScreen** — `postTime` is captured but every card shows hardcoded "Now".
