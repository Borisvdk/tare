---
name: verify-on-device
description: Run Tare on the Android 16 emulator and drive it to check a change in the real app: logging flows, sheets, navigation, keyboard, camera permission. Use after UI or flow changes that screenshot tests can't cover.
---

# Verify on the emulator

1. `scripts/dev.sh emulator` boots `tare_api36` (it's created if missing) and waits.
2. `scripts/dev.sh install` builds, installs and launches `app.tare.debug`.
3. Drive the app:
   - `scripts/dev.sh ui` lists visible elements as `label<TAB>x<TAB>y`.
   - `scripts/dev.sh tap "Log food"` taps the first element whose text or description
     contains the string (case-insensitive).
   - `scripts/dev.sh type "havermout"` types into the focused field.
   - `scripts/dev.sh back`, `scripts/dev.sh dark` / `light`.
4. `scripts/dev.sh screen` saves `.scratch/screen.png`. Open it with the Read tool and look
   at it properly: spacing, alignment, truncation, colour meaning, dark and light.
5. `scripts/dev.sh logcat` shows crashes. Run it after every flow.

Tips
- Wait about 1.5 s after navigation before `ui`/`tap`; uiautomator returns an empty tree
  while things are animating.
- Bottom sheets and dialogs show up in `ui` like everything else.
- The FAB's description is "Log food (hold to scan)".
- `scripts/dev.sh reset` clears debug data for a first-run experience. The bundled food
  import takes about 2 s after the first launch.
- Barcodes can't be scanned on the emulator. Test lookups through
  `FoodRepository.lookupBarcode` in a unit test, or with a known code such as
  `5449000000996` (Coca-Cola).
- Don't enter the owner's real API key on the emulator. A fake `sk-ant-…` key exercises the
  error path.
