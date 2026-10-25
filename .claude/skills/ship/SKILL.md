---
name: ship
description: Take a finished change in Tare from branch to the owner's phone: tests, PR, green CI, squash-merge, and checking that the release was published. Use when a feature or fix is ready.
---

# Ship a change

Merging to `main` publishes a signed APK that the owner's phone installs automatically, so
only ship what you've verified.

1. **Check**
   - `scripts/dev.sh test` passes.
   - UI changed? `scripts/dev.sh shots`, then open the changed PNGs in
     `app/src/test/screenshots/` and look at them. Commit the updated PNGs.
   - Touched R8, serialization, networking or the Claude SDK? `scripts/dev.sh release` builds,
     and the flow works in the release APK on the emulator.
   - Schema changed? Version bump, `Migration`, migration test, new schema JSON committed.
2. **PR**
   - `git push -u origin HEAD`
   - `gh pr create --fill` or write a body: what changed, why, how it was verified, and
     screenshot paths for UI changes.
3. **CI**: `gh pr checks --watch`. If it's red, fix it; never merge red.
4. **Merge**: `gh pr merge --squash --delete-branch`, then `git switch main && git pull`.
5. **Release**: `gh run watch` on the main-branch run, then `gh release list --limit 1`
   should show the new `v0.1.<n>` with `tare-0.1.<n>.apk` attached.
6. **Roadmap**: tick the item in docs/ROADMAP.md (in the PR itself where possible).

If a release breaks something, revert with a new PR (`git revert`). Never force-push `main`.
