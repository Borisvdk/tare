# Tare: guide for coding agents

Tare is a personal Android calorie tracker with exactly one user. Agents write the code and
make the design decisions; the owner steers now and then. This file is the source of truth
for how to work here. `AGENTS.md` points here.

**What Tare is for:** logging food should take seconds, and the screen should show only what
matters. Read [docs/PRODUCT.md](docs/PRODUCT.md) before adding features and
[docs/DESIGN.md](docs/DESIGN.md) before touching UI.

## Rules

1. **Logging speed and calm screens come first.** Before adding anything, ask whether it makes
   logging faster or the screen clearer. If it does neither, don't build it. Non-goals are
   listed in PRODUCT.md (auto-adjusting targets, micronutrient dashboards, social features,
   streaks).
2. **Never lose diary data.** Any Room schema change bumps the version in `TareDatabase` and
   adds a `Migration` plus a migration test. Never use destructive migration. Log entries
   snapshot their nutrients; keep it that way.
3. **Colour has meaning.** Coral is calories, periwinkle protein, mint carbs, amber fat. Use
   them only for those quantities. Everything else is warm neutrals (`Tare.colors`).
4. **Look at what you build.** UI changes aren't done until you've seen them: record
   screenshots (`scripts/dev.sh shots`) and open the PNGs, or run the flow on the emulator.
   Check light and dark.
5. **Keep it one module, packaged by feature**, with manual DI through `AppGraph`. Don't add
   Hilt, extra modules or a new architecture layer without writing an entry in
   docs/DECISIONS.md.
6. **No secrets in the repo.** The Claude API key lives on the device only (Keystore-encrypted
   DataStore). Signing material is in GitHub secrets and the gitignored `keystore.properties`.

## Commands

Everything goes through `scripts/dev.sh`. It finds a JDK 21+ (Android Studio's bundled JBR on
Windows; the Temurin 17 in `JAVA_HOME` is too old for Robolectric's Android 16) and the SDK for
you. Check exit codes, and never chain a commit after tests with `;`.

| Command | What it does |
|---|---|
| `scripts/dev.sh test` | Unit + Robolectric + screenshot-render tests (~1 min). Run before every PR. |
| `scripts/dev.sh shots` | Records Roborazzi screenshots to `app/src/test/screenshots/`. Open them to review UI. |
| `scripts/dev.sh build` | Debug APK |
| `scripts/dev.sh release` | Minified, signed release APK (catches R8 problems) |
| `scripts/dev.sh emulator` | Boots the `tare_api36` emulator (Android 16) and waits |
| `scripts/dev.sh install` | Builds, installs and launches the debug app (`app.tare.debug`) |
| `scripts/dev.sh ui` / `tap <text>` / `type <text>` / `back` / `screen` | Drive the app on the emulator and screenshot it |
| `scripts/dev.sh dark` / `light` / `logcat` / `reset` | Theme, crash log, clear data |

The debug app (`app.tare.debug`) installs next to the release app (`app.tare`), so testing on
the owner's phone never touches the real diary.

## Code map

```
app/src/main/java/app/tare/
  AppGraph.kt            manual DI: database, HTTP, repositories, FoodAi
  domain/                pure Kotlin: Nutrients, search (normalize, typos, ranking), parts of
                         the day + usual foods, weight trend
  data/db/               Room entities (Food, LogEntry, Meal, MealItem, RecipeIngredient, WeightEntry) + DAOs
  data/food/             FoodRepository, Open Food Facts client, bundled USDA importer
  data/log|meal|weight/  repositories
  data/ai/FoodAi.kt      Claude (official Anthropic Java SDK): meal + label estimation, JSON-schema output
  data/settings/         DataStore settings, Keystore-encrypted API key
  data/backup/           JSON export/import
  ui/theme/              colour + type tokens (Tare.colors, Tare.type, Radii)
  ui/components/         gauge, macro bars, number pad, rows, sheets, controls
  ui/today|log|scan|ai|library|meal|recipe|food|progress|settings/   screens + ViewModels
  ui/navigation/Routes.kt  Navigation 3 routes, Navigator, tareViewModel { }
tools/generic-foods/     reproducible build of assets/generic_foods.tsv from USDA SR Legacy
```

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## How to work

The owner has approved autonomous merging. A change goes like this:

1. Branch from `main`: `feat/<slug>`, `fix/<slug>` or `chore/<slug>`.
2. Implement it. Add or adjust tests for logic changes; re-record screenshots for UI changes
   and **look at them**. Changing a logging flow? Extend `app/src/test/.../flows/FlowTest.kt`.
   It drives the real UI on the JVM (tap, type, number pad via `pad-<digit>` test tags,
   sheets) against an in-memory database, with no emulator needed.
3. Run `scripts/dev.sh test`. For anything touching R8, serialization, the Claude SDK or
   networking, also run `scripts/dev.sh release`.
4. Commit in Conventional Commits style (`feat: …`, `fix: …`). Push and open a PR with
   `gh pr create` that says what changed and how you verified it, with screenshots for UI.
5. When CI is green, `gh pr merge --squash --delete-branch`. Merging to `main` publishes a
   signed release that the owner's phone installs from inside Tare. **Main must always be
   shippable.**
6. Update docs/ROADMAP.md: tick off what shipped and add follow-ups you found.

Ask the owner before anything that changes the product's direction, adds a paid service,
touches their real data, or deletes features.

## Conventions

- Kotlin official style, 4-space indent, trailing commas. Comments explain *why*; don't
  narrate the code.
- Composables take state and callbacks. ViewModels come from `tareViewModel { graph -> … }`
  and expose `StateFlow`s. Repositories are the only code that touches DAOs or the network.
- Numbers go through `Fmt` (`ui/Format.kt`). Never hand-format kcal or grams.
- Feedback messages go through `LocalNotifier.current.show(message, "Undo") { … }`. It's
  root-scoped (it survives the screen popping), short, and the newest message replaces the
  current one. Don't call `SnackbarHostState` from screens.
- Anything that saves or logs must ignore a second tap while it runs (see `EditorScaffold`,
  `ScanScreen`). Double entries are the most likely real-world bug.
- Long-lived ViewModels that depend on "today" must refresh it on resume
  (`LifecycleResumeEffect` → `onResume()`), because the app stays open across midnight.
- Copy: short, plain, friendly, no exclamation marks. Sentence case. Use "kcal" and "g".
- New strings can be inline literals (English-only app); keep them consistent with DESIGN.md.
- Writing files with backslashes from Bash heredocs on this Windows host collapses `\\` to
  `\`. Use the Edit/Write tools for Kotlin regexes.

## Gotchas

- Robolectric needs the `--add-opens` JVM args in `app/build.gradle.kts`; tests use
  `@Config(application = Application::class, sdk = [36])` so `TareApp` doesn't start the
  dataset import.
- Version code = commit count on HEAD (CI checks out with full history). Don't hard-code it.
- Open Food Facts asks for an identifying User-Agent (set in `AppGraph`). Search uses
  search-a-licious, filtered to the Netherlands, with a worldwide fallback.
- The Anthropic SDK ships its own R8 rules; don't add blanket `-keep` rules (they tripled the
  APK size).
- **Release-only bugs:** R8 (full mode) only runs on release builds, and it broke ML Kit once
  (registrars created by reflection lost their constructors; both scanners crashed on the phone
  while every debug build worked). CI builds a release on every PR and runs
  `scripts/check-release.sh`. For anything reflection-heavy (ML Kit, Play services, Health
  Connect, serialization), also try the minified build on the emulator: `scripts/dev.sh release`
  then `adb install -r -d app/build/outputs/apk/release/app-release.apk`.
- Crash reports from the phone are R8-obfuscated. Each release has `tare-<version>-mapping.txt`
  attached; look the frames up in it (`retrace`, or grep the class blocks).
- `Food` ids are namespaced (`usda:`, `off:`, `custom:`, `recipe:`, `ai:`) so re-imports upsert
  instead of duplicating.
