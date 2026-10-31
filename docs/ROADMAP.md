# Roadmap

Working backlog for agents. Pick from the top of **Next** unless the owner says otherwise.
Tick items off and add follow-ups as you go. Bigger ideas go to **Later** with a one-line
reason.

## Next

- [ ] **Verify Claude end to end with a real key** on the phone: describe text, plate photo,
      label photo. Tune the prompts on real Dutch meals (portion sizes, AH/Jumbo products).
- [ ] **Search quality**: when a real search disappoints, add the query to
      `SearchQualityTest` and fix the ranking or aliases (60 queries pass today).

## Later

- Home-screen widget with calories left (the owner said it's not needed for now).
- NEVO (Dutch food composition database) as a second generic source; needs a licence
  request form, so ask the owner first.
- Barcode scanning without the network (cache the most-scanned OFF products).
- Instrumented UI tests on an emulator in CI.

## Shipped

- Every row on Today shows P/C/F under its kcal, not just meals (the owner wanted the numbers
  for each item).
- Macro breakdowns: meals on Today show P/C/F; tapping a meal, the calories or a macro bar
  opens a breakdown with totals against targets, the calorie split, and foods ranked by kcal,
  protein, carbs or fat (asked for after a many-ingredient Describe meal).

- The repository is public, so in-app updates need no token and Obtainium isn't needed (PR #28).
- The scanner crash, found: R8 full mode stripped ML Kit's registrar constructors, so both
  scanners crashed in release builds only. Keep rule, a CI check on a release build per PR, and
  R8 mappings attached to releases.
- In-app updates: Tare checks GitHub Releases, shows what's new on Today, and installs the
  update after checking it's signed with the release key (PR #26).
- Scanner: any bad exit while scanning switches to Google's scanner; Settings switch; reports
  name why Android ended the app (PR #25).
- New logo, ▸0◂ (a scale's settled-at-zero sign around a serif zero): coral launcher icon,
  themed-icon layer, and a splash where the triangles slide onto the zero. Built by
  `tools/logo/build.py`; brand SVGs in `docs/brand/` (PR #23).
- Health Connect, read only: Renpho weigh-ins into the trend, Garmin calories burned as
  "eaten vs burned" on Progress (PR #24).
- Guided Claude key setup: claim Max plan API credits, create a key, paste; checked before
  saving (PR #22).
- Targets follow calories: protein in g/kg of trend weight, fat as % of calories, carbs the
  rest, with a guide to good ranges (PR #21).
- Easier favourites: long-press any food to star it; star logged entries from Today's
  selection bar, which now stays pinned while scrolling (PR #20).
- Inline dictation in Describe: mic inside the field, live words, Dutch, keeps listening
  through pauses (PR #19).
- Scanner crash on the Poco F8 Ultra made survivable: caught camera errors, Google's scanner
  as fallback, on-device crash reports to copy (PR #18).
- "Usually in the morning/afternoon/…" in the log hub: foods you had in that part of the day
  on 2+ of the last 28 days, at your usual amount there, until you've logged them today.
- Search survives typos ("bnaan", "brocolli", "chiken breast"); guesses are labelled and
  dropped when Open Food Facts shows the word was a real product.
- Large system font sizes (130%+) keep the amount sheet and Today headers readable; portion
  data from newer app versions no longer crashes an older one.
- "Same as yesterday" cards in the log hub: copy a whole morning/afternoon/evening in one tap.
- Product thumbnails in search results and the amount sheet (Open Food Facts images;
  `images.openfoodfacts.org` was unreachable from the dev machine on 2026-10-25, so check
  on the phone).
- Today follows the date after midnight when the app resumes.
- Favourites section in the log hub, above Recent. (Covers the "pinned items" idea: the
  `+` re-logs the last amount.)
- Log into a chosen part of the day: Morning/Afternoon/Evening/Night selector in the log
  hub and a `+` per section on Today. (Covers "edit time on log".)
- Launcher shortcuts (scan, describe, log) and share-to-Tare for photos and text.
- End-to-end UI flow tests on the JVM (`FlowTest`) and a 60-query search-quality suite.
- Daily automatic backup to a folder you pick (e.g. one that syncs to Google Drive).
- Your own portions ("my bowl" = 350 g): type grams, tap "+ Save", name it; long-press to
  remove. They survive dataset re-imports, product refreshes and edits.
- Undo when deleting meals, recipes and custom foods.
- Progress compares the 7-day average with the week before.
- Describe sends your 40 most-logged foods to Claude so matches reuse your exact product.
- "Log & next" in the scanner for meals built from several packaged products.
- Meal-like searches ("2 eggs and toast", "boterham met kaas") put Describe at the top.
- Copy a whole past day to today from its header (with undo).
- 15 fixes from an independent code review (see PR #2).

- v0.1: Today, log hub, barcode scan, Claude describe and label reading, saved meals,
  recipes, custom foods, weight trend, intake chart, targets, backup, signed releases through
  Obtainium.
