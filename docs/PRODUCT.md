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
   lookups and (opt-in) Claude requests. Health Connect (opt-in) is read on the phone itself.

## Who

One user in the Netherlands on a Poco F8 Ultra (Android 16). English UI, metric units,
groceries from Albert Heijn and Jumbo, a mix of Dutch and English food names.

## Features (v0.1)

- **Today:** calorie gauge (left / over), protein, carbs and fat against targets, week strip
  with daily rings, entries grouped by time of day (morning, afternoon, evening, night),
  each with a `+` to log into it; swipe to delete with undo, tap to edit, long-press to
  select (save as meal, copy to today, change time, delete). Past days can be copied to
  today in one tap.
- **Log hub:** a Morning/Afternoon/Evening/Night selector, then search across bundled basic
  foods (USDA, with Dutch aliases), your foods, recipes and saved meals, plus Open Food
  Facts products online, forgiving typos. Shortcuts for scan, describe and quick add.
  "Same as yesterday" cards; "Usually in the morning" (what you have in that part of the
  day, at your usual amount there, minus what's already logged); favourites and recents,
  all with one-tap re-log. Meal-like searches ("2 eggs and toast") lead with Describe.
- **Amount sheet:** number pad, food portions plus your own ("my bowl"), live macros and
  what's left after logging.
- **Barcode scanning:** on-device ML Kit and Open Food Facts lookup. For an unknown product:
  read the label with Claude, or type the values once.
- **Describe with Claude:** type, dictate, photograph or share a meal and get editable items
  with grams and macros, then log them all as one group. Your 40 most-logged foods are
  offered to Claude so matches reuse your exact products. Bring your own API key; model is
  selectable.
- **Health Connect (opt-in, read only):** weigh-ins from a scale app (Renpho) flow into the
  weight trend; calories burned from a watch (Garmin) show on Progress as "eaten vs burned"
  and as marks on the 14-day chart. A comparison only: targets never change by themselves.
- **Ways in:** launcher shortcuts (scan, describe, log) and the share sheet ("Log with
  Tare" for photos and text).
- **Saved meals:** groups of foods logged together, scalable (½×, 1×, 1½×, 2×).
- **Recipes:** ingredients plus cooked weight give per-100 g values, so you can log any
  portion by weight.
- **Custom foods:** per 100 g or per serving, with an optional serving size and barcode.
  Editing a scanned product forks it into your own version.
- **Progress:** smoothed weight trend (exponential moving average) with raw weigh-ins, and
  14-day intake bars with a 7-day average.
- **Settings:** targets, theme, Claude key and model, a daily automatic backup to a folder
  of your choice, and JSON export/import.

## Non-goals

- Adaptive targets, TDEE estimation or coaching.
- Micronutrient tracking beyond fibre.
- Social features, streaks, badges, gamification.
- Multiple users, accounts, a cloud backend.
- iOS, tablets, wear (phone only).

Ask the owner before building anything in this list.
