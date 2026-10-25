---
name: design-review
description: Critically review Tare's UI against docs/DESIGN.md using rendered screenshots, then fix what falls short. Use before shipping UI work or when asked to polish the design.
---

# Design review

1. `scripts/dev.sh shots` renders every screen in `ScreenshotTest` (light and dark). Add a
   test there for any new screen or sheet first.
2. Open each PNG in `app/src/test/screenshots/` with the Read tool. For every screen check:
   - **One focal point.** Is it obvious what matters most? Is anything competing with it?
   - **Less.** Can a label, divider, number or card go without losing meaning?
   - **Colour meaning.** Coral, periwinkle, mint and amber appear only for kcal, protein,
     carbs and fat. Destructive actions use `over`.
   - **Type.** Tokens from `Tare.type` only, tabular figures for numbers, the serif only for
     titles and empty states.
   - **Spacing and alignment.** 16dp gutters, consistent row heights, nothing cramped or
     clipped, long names ellipsized.
   - **Both themes.** Contrast holds in light and dark; light-mode cards have hairline
     borders.
   - **Copy.** Short, sentence case, no exclamation marks, numbers formatted through `Fmt`.
3. Fix issues in the components first (`ui/components`) so every screen benefits.
4. Re-record the screenshots and compare before and after. Include both in the PR description.
