# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository

GitHub: https://github.com/alienex-bit/HoneyJar

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
- `ui/screens/` — one file per screen (Home, History, AI, Stats, Settings, Onboarding)
- `ui/viewmodels/` — `MainViewModel` (shared across all screens via `MainActivity`). Also defines `AppGuiltEntry` and `AIChatMessage`.
- `ui/theme/` — `Theme.kt` defines `HoneyJarTheme`, `HoneyJarColors`, `LocalHoneyJarColors`
- `ui/components/` — `GlassCard`, `ContextualActionsSheet`, `AppIcon`
- `utils/` — `BackupManager`, `AppIconCache`, `AppLabelCache`, `AppCategoryResolver`, `ColorUtils`, `NotificationCategories`, `NotificationConstants`, `TimeUtils`
- `workers/` — `AutoBackupWorker`, `SecondaryAlertWorker`

## Data Flow

`NotificationService` (background) → `NotificationRepository.addNotification()` → Room (`NotificationDao`) → `NotificationRepository.notifications: Flow` → screens via `collectAsState()`

`NotificationService` calls `NotificationRepository.initialize()` in its own `onCreate()`. `MainActivity` also calls `initialize()` on startup — both are safe because `HoneyJarDatabase` is a proper singleton keyed to `applicationContext`.

**Notification IDs** are formed as `"${sbn.key}_${sbn.postTime}"` — stable across service reconnects. The DAO uses `OnConflictStrategy.IGNORE`, so `onListenerConnected` re-processing active notifications is idempotent.

## Settings Persistence

`SettingsRepository` wraps Android DataStore (`preferences`). All settings are accessed as `Flow<T>` and written with `suspend fun set*()`. The DataStore extension `Context.dataStore` is defined at the top of `SettingsRepository.kt` — only one `preferencesDataStore` per name can exist per process.

`ThemePrefs` uses SharedPreferences (not DataStore) and is an in-memory `mutableStateOf` singleton initialized in `MainActivity.onCreate()`.

## Theming

Four themes: `DarkHoney`, `Midnight`, `LightCream`, `LightMinimal` (enum `HoneyJarThemeType`).

Screens access theme-specific tokens via `LocalHoneyJarColors.current` (`HoneyJarColors` — not `MaterialTheme.colorScheme`). Both must be used: `MaterialTheme.colorScheme.primary` for standard M3 slots, `LocalHoneyJarColors.current.accent` / `.textPrimary` / `.glassBorder` / `.heroGradient` / `.heatmapRamp` etc. for custom tokens.

`HoneyJarColors` tokens: `accent`, `accentGlow`, `heroGradient`, `glassBorder`, `textPrimary`, `textSecondary`, `itemBg`, `heatmapRamp`. The `heatmapRamp` is a `List<Color>` of 5 steps (empty → low → mid → high → max) defined per theme — do not compute heatmap colours dynamically from `MaterialTheme.colorScheme`.

Typography: `PlayfairDisplay` (italic branding/headers) and `Outfit` (all UI text). Both are defined as `FontFamily` vals in `Theme.kt`.

## Navigation

Single-activity (`MainActivity extends FragmentActivity`). Navigation uses `NavHost` with five bottom bar tabs: **Home, History, AI, Stats, Settings**. Onboarding is a separate nav destination that gates entry — the app redirects there if `hasCompletedOnboardingIntro` is false OR any required permission is missing (`areAllPermissionsGranted` in `OnboardingScreen.kt` checks notification access, battery optimization, storage, and DND access).

History accepts optional `?filter={filter}&status={status}` nav arguments for deep-linking to a filtered view (used by the urgent notifications shortcut on Home and by `SecondaryAlertWorker` deep links).

## Room Database

`HoneyJarDatabase` is at **version 12**, entities: `NotificationEntity`, `NotificationStatsEntity`, `PriorityGroupEntity`, `AppCategoryEntity`. Migrations 3→4 through 11→12 are all defined inline — no gaps.

**`fallbackToDestructiveMigration()` is still present** in the builder call. This is a known risk: any future migration gap will silently wipe user data rather than crash. It should be removed once the migration chain is considered stable.

Category string keys (stored in `PriorityGroupEntity.key` and `HoneyNotification.priority`) must be lowercase and match the constants in `NotificationCategories`. The DB is seeded with 16 default priority groups on first create via `DatabaseCallback.onCreate`.

`PriorityGroupEntity` fields of note:
- `soundUri: String` — `"off"` | `"default"` | `"chime"` | `"alert"` | custom URI (added migration 6→7)
- `vibrationPattern: String` — `"off"` | `"short"` | `"double"` | `"long"` | `"urgent"` (added migration 6→7)
- `secondaryAlertEnabled`, `initialAlertDelayMs`, `secondaryAlertDelayMs` (added migration 7→8)
- `ignoreUntil: Long` — mute-until epoch ms for temporary category silencing (added migration 11→12)

## Category System

16 active categories defined in `NotificationCategories`: `urgent`, `messages`, `social`, `email`, `calendar`, `calls`, `weather`, `travel`, `finance`, `shopping`, `media`, `security`, `connected`, `updates`, `photos`, `system`. Two legacy keys (`device`, `delivery`) are kept in the constants object so old DB rows don't become orphaned — both are disabled in the DB by default.

`AppCategoryResolver` maps incoming notifications to a category using three strategies in order:
1. **Package lookup table** (`PACKAGE_CATEGORY_MAP`) — exact match on package name
2. **Substring match** — checks package name and notification text for keywords
3. **Play Store scrape** — fetches the app's Play Store page if network is available; result is cached in the `app_category_cache` Room table (`AppCategoryEntity`)

**Important:** the Play Store scrape makes an HTTP call with no timeout configured. This runs on `Dispatchers.IO` during notification handling and can block a thread for the OS default TCP timeout (~75 s) under poor network conditions.

`recategorizeAll()` in `NotificationRepository` re-runs strategy 1 (static only — no network) over all historical notifications in a single transaction. Called from `MainActivity` on startup and exposed via `MainViewModel.recategorizeAll()` for the Settings screen trigger.

## Encryption

`HoneyEncryptor` uses Android Keystore (AES/GCM/NoPadding) with key alias `HoneyJarMainKey`. **All alerts are encrypted at write time.** `NotificationRepository.toEntity()` wraps notification content into a JSON blob, encrypts it, and populates the `iv` and `encryptedData` columns. The plaintext `title` and `text` columns are set to `"[Encrypted]"` as placeholders. `toModel()` automatically decrypts content on-the-fly with full fallback support for legacy plaintext data.

## Backup

`BackupManager` (in `utils/`) exports a versioned JSON file containing all notifications, priority groups, stats, settings, and theme. Restore uses `IGNORE` for notifications (never overwrites existing) and `REPLACE` for groups and stats.

`AutoBackupWorker` (in `workers/`) runs via WorkManager on a user-selected schedule (off / daily / weekly / monthly). A one-off backup also fires immediately when the user selects a frequency pill. No battery or network constraints are applied — backup always runs when scheduled.

Android system backup (`backup_rules.xml`, `data_extraction_rules.xml`) also covers all SharedPreferences and the Room database via Google cloud backup and device-to-device transfer.

## Sound Profiles

Per-priority-group sound and vibration alerts. When a notification is captured, `NotificationService` checks the group's `soundUri` and `vibrationPattern`. If either is non-`"off"`, it posts a HoneyJar-owned secondary alert notification on a dedicated per-category channel (`honeyjar_alert_$categoryKey`).

- **Sound options:** `off` / `default` / `chime` (bundled `res/raw/sound_chime.wav`) / `alert` (bundled `res/raw/sound_alert.wav`) / custom (device ringtone URI)
- **Vibration presets:** `off` / `short` `[0,100]` / `double` `[0,100,100,100]` / `long` `[0,500]` / `urgent` `[0,100,50,100,50,300]`
- Channels are created/recreated dynamically in `NotificationService` when settings change; cached in `channelSettingsCache` to avoid redundant recreation.
- `soundUri` and `vibrationPattern` are included in `BackupManager` export/restore.

**Note:** `SecondaryAlertWorker` posts to a category's alert channel (`honeyjar_alert_$categoryKey`) without verifying the channel exists first. If the category has never had a notification processed by `NotificationService`, the channel won't have been created yet and the alert will silently drop.

## App Guilt Score

A section in `StatsScreen` ranking the top 10 noisiest apps over the last 7 days. Computed entirely in-memory from the existing notifications flow via `MainViewModel.appBreakdown: StateFlow<List<AppGuiltEntry>>` — no separate DB table. Shows app icon (`AppIconCache`), app label (`AppLabelCache`), 7-day count chip, and a streak badge ("🔥 X days in a row") when an app has interrupted the user 3+ consecutive days.

**Performance note:** the streak calculation does an O(N×365) scan of the full notification list for each app on every update. This will become slow with several months of accumulated data.

## AI Screen

`AIScreen` is a chat-style interface backed by `MainViewModel.sendAIPrompt()`. Responses are currently generated by **keyword matching** (`when` block on prompt text) with a fake 1-second delay — there is no real LLM API call. The response logic reads live notification data from the DB, so the summaries reflect real counts, but the "AI" label is misleading for anything other than the three recognized intents ("missed/summary", "urgent", "distract/interrupted"). Any other input gets a generic fallback response.

## Stats Screen

All metrics in `StatsScreen` are now wired to real `MainViewModel` state flows: `totalCount`, `actionedCount`, `barChartData`, `categoryBreakdown`, `heatmapData`, `avgResponseMinutes`, `appBreakdown`, `weeklyCount`, `prevWeekCount`. The heatmap uses `heatmapRamp` from `LocalHoneyJarColors`. No hardcoded or random values remain.

## Known Unimplemented / Stub Areas

- **Search** — the search icon tap handler in `HomeScreen` is an empty `onClick = {}`. No search UI exists.
- **Pro purchase** — no `BillingClient` integration. The buy button calls `onFinished()` immediately.
- **Notification encryption** — `HoneyEncryptor` exists but is never called; all notification content is stored plaintext (see Encryption section above).
- **Autopurge** — `NotificationRepository.purgeOldNotifications()` is implemented correctly but has no call site (no WorkManager job, no startup trigger). The setting may appear in the UI but does nothing.