# Roadmap

Working backlog for agents. Pick from the top of **Next** unless the owner says otherwise.
Tick items off and add follow-ups as you go. Bigger ideas go to **Later** with a one-line
reason.

## Next

- [ ] **Verify Claude end to end with a real key** on the phone: describe text, plate photo,
      label photo. Tune the prompts on real Dutch meals (portion sizes, AH/Jumbo products).
- [ ] **Search quality**: when a real search disappoints, add the query to
      `SearchQualityTest` and fix the ranking or aliases (60 queries pass today).

- [ ] **Copy a whole day**: long-press a day in the week strip → copy to today.

## Later

- Home-screen widget with calories left (the owner said it's not needed for now).
- Health Connect (not needed for now).
- NEVO (Dutch food composition database) as a second generic source; needs a licence
  request form, so ask the owner first.
- Barcode scanning without the network (cache the most-scanned OFF products).
- Instrumented UI tests on an emulator in CI.

## Shipped

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
- Undo when deleting meals, recipes and custom foods.
- Progress compares the 7-day average with the week before.
- Describe sends your 40 most-logged foods to Claude so matches reuse your exact product.
- "Log & next" in the scanner for meals built from several packaged products.
- Meal-like searches ("2 eggs and toast", "boterham met kaas") put Describe at the top.
- 15 fixes from an independent code review (see PR #2).

- v0.1: Today, log hub, barcode scan, Claude describe and label reading, saved meals,
  recipes, custom foods, weight trend, intake chart, targets, backup, signed releases through
  Obtainium.
