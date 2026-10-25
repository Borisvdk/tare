# Decisions

Short records of choices that shape the codebase. Add an entry when you make a choice a
future agent might want to undo.

## 2026-10-25: Native Kotlin + Compose, single module

Android-only with one user means no cross-platform framework. Native gives the best camera
and barcode performance, Material components and Compose tooling. One Gradle module keeps
builds fast and agent navigation simple. Split it only when build times actually hurt.

## 2026-10-25: Manual DI (`AppGraph`) instead of Hilt

There are about ten singletons. A plain class is easier to read, needs no annotation
processing, and works in Robolectric screenshot tests by passing an in-memory database.

## 2026-10-25: Room entities are the domain models

Mapping layers would double the code for no gain at this size. Pure logic still lives in
`domain/`.

## 2026-10-25: Log entries snapshot nutrients

Editing or deleting a food must never rewrite history. Entries keep name and absolute
nutrients; amount edits recompute from the food when it still exists, otherwise they scale
the snapshot.

## 2026-10-25: Bundled USDA basics + Open Food Facts, no own backend

USDA SR Legacy is public domain, complete and offline. Curated rows add Dutch aliases.
Open Food Facts covers branded and Dutch products. NEVO would be better for Dutch generic
foods but needs a licence form, so it's deferred.

## 2026-10-25: Claude via the official Java SDK with structured outputs

The JSON schema output makes parsing reliable. The SDK costs about 15 MB of dex but ships
correct R8 rules. Users bring their own key; it's stored encrypted with a Keystore key and
excluded from backups.

## 2026-10-25: Releases on every merge, version code = commit count

Obtainium installs from GitHub Releases. Every merge to `main` ships, so `main` must stay
shippable. Commit count gives a strictly increasing version code with no manual bumps.

## 2026-10-25: Name: Tare

The button on a kitchen scale that zeroes out everything that doesn't count. It matches the
design philosophy (show only what matters) and the audience (people who weigh food).
