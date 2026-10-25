#!/usr/bin/env bash
# Checks a built release APK for what R8 can break without any test noticing:
# ML Kit creates the component registrars named in the manifest by reflection, so each needs
# its no-arg constructor in the dex. R8 full mode drops it unless a keep rule says otherwise,
# and then both barcode scanners crash on the phone while every debug build works.
# Usage (after :app:assembleRelease): scripts/check-release.sh
set -euo pipefail
cd "$(dirname "$0")/.."

apk=app/build/outputs/apk/release/app-release.apk
manifest=$(ls app/build/intermediates/merged_manifest/release/*/AndroidManifest.xml | head -1)
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
analyzer=$(ls "$sdk"/cmdline-tools/*/bin/apkanalyzer 2>/dev/null | head -1)
[[ -x "$analyzer" ]] || { echo "apkanalyzer not found under $sdk/cmdline-tools" >&2; exit 2; }

dex=$("$analyzer" dex packages --defined-only "$apk")
registrars=$(grep -oE 'com\.google\.firebase\.components:[A-Za-z0-9_.$]+' "$manifest" | cut -d: -f2 | sort -u)
[[ -n "$registrars" ]] || { echo "No component registrars in the merged manifest; did the build change?" >&2; exit 2; }

missing=0
for registrar in $registrars; do
    if grep -qF "$registrar <init>()" <<<"$dex"; then
        echo "ok: $registrar"
    else
        echo "::error::$registrar lost its constructor in the release build (see proguard-rules.pro)"
        missing=1
    fi
done
exit $missing
