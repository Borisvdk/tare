# Design system

Tare should feel calm, warm and precise: closer to a well-made kitchen scale than a fitness
dashboard. Inspirations are MacroFactor (clarity of numbers), Claude (warm neutrals, serif
accents) and Discord (rounded, friendly surfaces). Dark mode comes first; light mode is just
as polished.

Screenshots of every main screen live in `app/src/test/screenshots/` (`scripts/dev.sh shots`).

## Colour

Tokens live in `ui/theme/Theme.kt` and are read as `Tare.colors.*`.

| Token | Dark | Light | Use |
|---|---|---|---|
| background | `#0E0D0C` | `#F6F4F0` | Screen background |
| surface | `#181715` | `#FFFFFF` | Cards, sheets, bottom bar |
| surfaceRaised | `#211F1D` | `#EFECE6` | Fields, chips, secondary buttons |
| surfacePressed | `#2B2926` | `#E6E2DB` | Pressed or nested surfaces |
| outline | `#2F2C29` | `#E2DDD5` | Hairlines (light-mode card borders) |
| text / textMuted / textFaint | warm off-white → grey | ink → grey | Three levels, no more |
| **kcal** | `#FF7A50` | `#EC5B2C` | Calories and the primary action |
| **protein** | `#8E9BFF` | `#5B6CF0` | Protein only |
| **carbs** | `#4FD6B4` | `#14A483` | Carbs only |
| **fat** | `#FFC24F` | `#DB9A0E` | Fat only |
| over | `#FF5370` | `#E5304F` | Over target, destructive actions |

Rules:
- The four quantity colours mean exactly one thing each. Never use them for decoration.
- Coral doubles as the brand and primary-action colour: one primary button per screen.
- No gradients except subtle fades behind floating bars.

## Type

Geist (variable, tabular figures for numbers) for everything; Instrument Serif for editorial
headlines only (screen titles such as "Today", empty states). Tokens are in `ui/theme/Type.kt`.

| Token | Use |
|---|---|
| `hero` 60sp/600 | The one big number on a screen (calories left) |
| `number` 40sp/600 | Secondary big numbers (weight, averages) |
| `headline` serif 38sp | Screen titles |
| `title` 17sp/600 | Sheet titles, card titles, buttons |
| `body` / `bodyStrong` 15sp | Text, row titles |
| `label` 13sp/500 | Chips, tabs |
| `caption` 12sp | Secondary info |
| `figure` 15sp/500 tabular | Numbers in rows |
| `overline` 11sp/600, tracked, uppercase | Section headers |

## Shape and space

- Radii: cards 24, rows and fields 16, chips 12, buttons and pills fully round, sheets 28 on
  top (`Radii`).
- 16dp screen gutters, 12dp between cards, 8dp inside groups.
- Touch targets at least 44dp; primary buttons 54dp tall.

## Components (`ui/components`)

- `CalorieGauge`: 270° dial open at the bottom, coral fill, overflow drawn in `over`.
- `MacroBars` / `ProgressBar`: label, "value / target g", 6dp bar. Bars can be tappable.
- `EnergySplitBar`: one bar split into protein, carbs and fat by their share of calories
  (4/4/9), with whole percentages that add up to 100 underneath.
- `MiniRing`: day ring in the week strip.
- `NumberPad` + `AmountInputState`: the first key replaces the value, long-press backspace
  clears.
- `ListRow` / `FoodRow`: title, subtitle, kcal figure (optionally with a P/C/F line under it,
  as on Today), optional trailing action.
- `TareSheet`: the modal bottom sheet every flow uses.
- `PrimaryButton`, `SecondaryButton`, `TextAction`, `RoundIconButton`, `SelectChip`,
  `Segmented`, `Card`, `SectionHeader`, `TareTextField`, `EmptyState`.
- Press feedback is a slight scale-down (`pressScale`), not ripples.

## Patterns

- **One primary action per screen**, bottom-anchored and coral.
- **Sheets for quick decisions** (amount, quick add, meal log, targets); full screens for
  editing (food, meal, recipe).
- **Breakdowns** (`ui/today/BreakdownSheet`): totals first, then a kcal/Protein/Carbs/Fat
  switch that ranks what's inside, biggest first, with a share bar in that quantity's colour.
- **Undo instead of confirm** for anything reversible (delete entry, quick log), via
  `LocalNotifier`. Confirm only for irreversible actions (import replaces data).
- **Numbers:** `Fmt.kcal` ("1,240"), `Fmt.grams` (one decimal under 10 g), `Fmt.amount`
  (no trailing zeros).
- **Motion:** spring animations on the gauge and bars, slide and fade between screens, and
  haptics on log (`Confirm`) and long-press.

## Voice

Short and plain, like a friend who knows nutrition. Sentence case. No exclamation marks, no
hype. Say what happens: "Logged Banana · 105 kcal", "Removed 2 items · Undo".

## Logo

The mark is ▸0◂: the sign a scale's display shows once it has settled at zero, set around
the Instrument Serif zero. Tagline: "Zero in on what counts."

- `tools/logo/build.py` generates the launcher layers, the animated splash icon and the SVGs
  in `docs/brand/`. Change the script, not the generated XML.
- Launcher icon: the mark in `#1A0A04` on coral (`tare_launcher_background`). The
  monochrome layer has the same shapes for themed icons.
- Splash: no icon background. The zero in text colour and coral triangles on the screen
  background; the triangles slide onto the zero (700 ms).
- The mark only ever uses coral and neutrals, never the protein, carbs or fat colours.
