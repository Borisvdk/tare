# Architecture

## Stack

| Concern | Choice |
|---|---|
| Language/UI | Kotlin 2.4, Jetpack Compose (BOM 2026.09), Material 3 as the base, heavily themed |
| Build | AGP 9.4 with built-in Kotlin, Gradle 9.8, version catalog `gradle/libs.versions.toml` |
| Navigation | Navigation 3 (`NavDisplay`, typed `Route` keys), ViewModels scoped per entry |
| Persistence | Room 2.8 (KSP), DataStore Preferences |
| Network | Ktor client (OkHttp engine) + kotlinx.serialization for Open Food Facts |
| AI | Official Anthropic Java SDK, structured JSON output |
| Camera | CameraX + `camera-compose` viewfinder + ML Kit barcode scanning (bundled model) |
| Images | Coil 3 (product thumbnails; not yet shown in the UI) |
| Tests | JUnit4, Truth, Robolectric 4.17, Roborazzi, MockWebServer |

minSdk 31, targetSdk 36 (Android 16), compileSdk 37.

## Layers

```
ui (Compose screens + ViewModels)
  └─ data (repositories) ─ data/db (Room DAOs) · OpenFoodFactsClient · FoodAi · DataStore
       └─ domain (pure Kotlin: Nutrients, ranking, trend)
```

- `AppGraph` builds everything lazily; `TareApp` owns one instance. Screens get ViewModels
  through `tareViewModel { graph -> … }`, and composables can reach the graph through
  `LocalAppGraph` for simple one-off reads (settings sheets).
- Room entities double as domain models (`Food`, `LogEntry`, …). Domain logic that needs no
  Android sits in `domain/` and is unit tested.

## Data model

- `food`: every food from every source, nutrients **per 100 g/ml** (`per100_*` columns),
  portions stored as JSON (`[{label, grams}]`, grams per one unit). Usage stats (`useCount`,
  `lastUsedAt`, `lastGrams`, `lastPortionLabel`) drive ranking and default amounts. `hidden`
  is a soft delete. Recipes are foods with `source = RECIPE` and rows in `recipe_ingredient`.
- `log_entry`: one row per thing eaten; snapshots name and **absolute** nutrients. `day` is
  the epoch day; `loggedAt` is the time (used for the time-of-day sections). Entries logged
  together share a `groupId`/`groupName`.
- `meal` + `meal_item`: saved meals (food id + grams + portion label).
- `weight`: one row per day.

Schema JSON is exported to `app/schemas/`. **Every schema change needs a migration.**

## Food sources

1. **Bundled basics:** `assets/generic_foods.tsv`, about 7,400 USDA SR Legacy foods, 883 of
   them curated with clean names, Dutch aliases and popularity. It's imported into `food` on
   first launch and on each app update (usage stats are kept). Rebuild with
   `python tools/generic-foods/build.py`.
2. **Open Food Facts:** barcode lookups (`/api/v2/product/{code}`) and text search through
   search-a-licious (`search.openfoodfacts.org`), filtered to products sold in the
   Netherlands with a worldwide fallback, then re-ranked locally. Products are saved into
   `food` the first time they're logged.
3. **Custom foods, recipes, AI estimates:** created in the app.

Local search: normalize the query (lowercase, no accents), fetch candidates by the longest
token with `LIKE`, then rank in Kotlin (`domain/Ranking.kt`). Exact names and aliases win,
then usage and recency, then curated popularity. About 10k rows makes this a few
milliseconds; no FTS needed.

## Claude

`data/ai/FoodAi.kt` calls the Messages API with the user's key (`SecretStore`). The model is
chosen in settings and defaults to Claude Opus 5.5. Requests use `output_config.format`
(JSON schema), so replies are parsed directly. Opus and Sonnet requests opt into server-side
refusal fallbacks. Photos are downscaled to 1568 px JPEG. Two prompts:

- `estimateMeal(text, photo?)` → title + items with grams and per-100 g macros + a note.
- `readLabel(photo)` → per-100 g values (+ serving size) for the food editor.

AI items become `ai:` foods keyed by name, so they show up in recents and search.

## Backups

- **Daily automatic backup** (`data/backup/AutoBackup.kt`): the user picks a folder once
  (SAF tree, persisted permission). A WorkManager job writes `tare-backup.json` there every
  day, ideally into a folder that syncs to Google Drive. Settings shows the last run.
- **Manual** export/import of the same JSON format (`BackupManager`). Import replaces the
  diary, meals and weights; foods are merged by id.
- Android auto backup includes the database and settings, but not the encrypted API key.

## Release pipeline

`.github/workflows/ci.yml`: every push and PR runs unit tests and builds the APK. Pushes to
`main` also build a signed release APK and publish GitHub release `v0.1.<commit count>`.
The owner's phone tracks releases with Obtainium. The signing key is in GitHub secrets
(`TARE_KEYSTORE_BASE64`, …); locally, `keystore.properties` points to
`C:/Users/Boris/.tare/tare-release.jks`.
