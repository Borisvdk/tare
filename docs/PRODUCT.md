# Product

## The idea

Tare is a calorie tracker for one person who already knows what they're doing: they weigh
food, know their targets, and want logging done in seconds. MacroFactor is the benchmark for
polish. Tare drops what doesn't serve that person (auto-adjusting targets, subscriptions,
coaching) and puts everything into **ease of logging** and **a calm, clear screen**.

The name: *tare* is the button on a kitchen scale that zeroes out everything that doesn't
count.

## Principles

1. **Seconds per log.** The common case is re-logging something you've had before; that's
   one tap (recents' `+`, saved meals). Everything else is at most: search, tap, amount, log.
2. **Show less.** Today shows calories left, three macro bars, and what you ate. Every extra
   element must earn its place.
3. **The number pad beats the keyboard.** Amounts use a built-in pad that never moves the
   layout. The first key press replaces the old value.
4. **Remember everything.** Last amount, last portion, favourites and recents rank searches,
   so the right food is usually the first row.
5. **Trust the user's numbers.** Targets are set by hand and never changed by the app.
6. **Local first.** Instant, works offline, nothing leaves the phone except Open Food Facts
   lookups and (opt-in) Claude requests.

## Who

One user in the Netherlands on a Poco F8 Ultra (Android 16). English UI, metric units,
groceries from Albert Heijn and Jumbo, a mix of Dutch and English food names.

## Features (v0.1)

- **Today:** calorie gauge (left / over), protein, carbs and fat against targets, week strip
  with daily rings, entries grouped by time of day (morning, afternoon, evening, night),
  swipe to delete with undo, tap to edit, long-press to select (save as meal, copy to today,
  change time, delete).
- **Log hub:** search across bundled basic foods (USDA, with Dutch aliases), your foods,
  recipes and saved meals, plus Open Food Facts products online. Shortcuts for scan,
  describe and quick add. One-tap re-log from recents.
- **Barcode scanning:** on-device ML Kit and Open Food Facts lookup. For an unknown product:
  read the label with Claude, or type the values once.
- **Describe with Claude:** type, dictate or photograph a meal and get editable items with
  grams and macros, then log them all as one group. Bring your own API key; model is
  selectable.
- **Saved meals:** groups of foods logged together, scalable (½×, 1×, 1½×, 2×).
- **Recipes:** ingredients plus cooked weight give per-100 g values, so you can log any
  portion by weight.
- **Custom foods:** per 100 g or per serving, with an optional serving size and barcode.
  Editing a scanned product forks it into your own version.
- **Progress:** smoothed weight trend (exponential moving average) with raw weigh-ins, and
  14-day intake bars with a 7-day average.
- **Settings:** targets, theme, Claude key and model, JSON export/import. Android auto
  backup covers the database.

## Non-goals

- Adaptive targets, TDEE estimation or coaching.
- Micronutrient tracking beyond fibre.
- Social features, streaks, badges, gamification.
- Multiple users, accounts, a cloud backend.
- iOS, tablets, wear (phone only).

Ask the owner before building anything in this list.
