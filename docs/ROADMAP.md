# Roadmap

Working backlog for agents. Pick from the top of **Next** unless the owner says otherwise.
Tick items off and add follow-ups as you go. Bigger ideas go to **Later** with a one-line
reason.

## Next

- [ ] **Verify Claude end to end with a real key** on the phone: describe text, plate photo,
      label photo. Tune the prompts on real Dutch meals (portion sizes, AH/Jumbo products).
- [ ] **Copy from another day**: in the log hub, "Yesterday's morning" style shortcuts that
      copy a whole section in one tap.
- [ ] **Edit time on log**: remember the last time picked for past-day logging.
- [ ] **Product images**: show OFF thumbnails in search results and the amount sheet (Coil
      is already a dependency).
- [ ] **Favourites row** in the log hub above recents.
- [ ] **Pinned quick items**: long-press a recent food to pin it with a fixed amount for
      true one-tap logging.
- [ ] **Search quality pass**: collect queries where the right food isn't first and adjust
      ranking or aliases. Add tests for each.

## Later

- Home-screen widget with calories left (the owner said it's not needed for now).
- Health Connect (not needed for now).
- NEVO (Dutch food composition database) as a second generic source; needs a licence
  request form, so ask the owner first.
- Barcode scanning without the network (cache the most-scanned OFF products).
- Instrumented UI tests on an emulator in CI.

## Shipped

- v0.1: Today, log hub, barcode scan, Claude describe and label reading, saved meals,
  recipes, custom foods, weight trend, intake chart, targets, backup, signed releases through
  Obtainium.
