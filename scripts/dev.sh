#!/usr/bin/env bash
# Tare developer helper — one entry point for building, testing and driving the app.
# Works in Git Bash on Windows and on Linux/macOS. Run `scripts/dev.sh help` for commands.
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1 # stop Git Bash rewriting /sdcard paths

# --- JDK: Gradle needs 17+. Fall back to Android Studio's bundled JBR on Windows. -----------
java_major() { "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'; }
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(java_major "$JAVA_HOME/bin/java")" -ge 17 ]; then
  :
elif [ -x "/c/Program Files/Android/Android Studio/jbr/bin/java" ]; then
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
fi

# --- Android SDK -------------------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f local.properties ]; then
  SDK=$(sed -n 's/^sdk.dir=//p' local.properties | sed 's#\\:#:#; s#\\\\#/#g')
fi
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
[ -x "$ADB" ] || [ -x "$ADB.exe" ] || ADB=adb
AVD=tare_api36
PKG=app.tare.debug
SCRATCH=.scratch
mkdir -p "$SCRATCH"

gradle() { ./gradlew --console=plain "$@"; }

ui_dump() {
  "$ADB" shell uiautomator dump /sdcard/tare-ui.xml >/dev/null
  "$ADB" exec-out cat /sdcard/tare-ui.xml
}

# Prints "label<TAB>x<TAB>y" for every node with text or a content description.
ui_nodes() {
  ui_dump | tr '>' '\n' | grep -E '(text|content-desc)="[^"]+"' | while IFS= read -r node; do
    label=$(printf '%s' "$node" | grep -oE 'text="[^"]*"' | head -1 | cut -d'"' -f2)
    [ -z "$label" ] && label=$(printf '%s' "$node" | grep -oE 'content-desc="[^"]*"' | head -1 | cut -d'"' -f2)
    b=$(printf '%s' "$node" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    # shellcheck disable=SC2086
    set -- $b
    label=$(printf '%s' "$label" | sed 's/&amp;/\&/g; s/&quot;/"/g; s/&#39;/'"'"'/g; s/&lt;/</g; s/&gt;/>/g')
    [ -n "$label" ] && [ $# -eq 4 ] && printf '%s\t%d\t%d\n' "$label" $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  done || true
}

case "${1:-help}" in
  build) gradle :app:assembleDebug ;;
  test) gradle :app:testDebugUnitTest ;;
  shots)
    shift
    gradle :app:recordRoborazziDebug --tests '*ScreenshotTest*' "$@"
    echo "Screenshots: app/src/test/screenshots/"
    ;;
  release) gradle :app:assembleRelease && ls -la app/build/outputs/apk/release/ ;;
  emulator)
    if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD"; then
      echo no | "$AVDMANAGER" create avd -n "$AVD" -k "system-images;android-36;google_apis;x86_64" -d pixel_8 --force
    fi
    if ! "$ADB" devices | grep -q "emulator-"; then
      nohup "$EMULATOR" -avd "$AVD" -no-snapshot-save -no-boot-anim -no-audio >"$SCRATCH/emulator.log" 2>&1 &
    fi
    "$ADB" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
    echo "Emulator ready"
    ;;
  install | run)
    gradle :app:assembleDebug
    "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
    "$ADB" shell am start -n "$PKG/app.tare.MainActivity"
    ;;
  launch) "$ADB" shell am start -n "$PKG/app.tare.MainActivity" ;;
  reset) "$ADB" shell pm clear "$PKG" ;;
  screen)
    out="${2:-$SCRATCH/screen.png}"
    "$ADB" exec-out screencap -p >"$out"
    echo "$out"
    ;;
  ui) ui_nodes ;;
  tap)
    [ $# -ge 2 ] || { echo "usage: dev.sh tap <text or content-desc>"; exit 1; }
    nodes=$(ui_nodes)
    # awk, not `grep -iF`: Git Bash's grep aborts on that flag combination.
    hit=$(printf '%s\n' "$nodes" | awk -F'\t' -v q="$2" 'index(tolower($1), tolower(q)) { print; exit }')
    [ -n "$hit" ] || { echo "No element matching '$2'"; exit 1; }
    x=$(printf '%s' "$hit" | cut -f2)
    y=$(printf '%s' "$hit" | cut -f3)
    "$ADB" shell input tap "$x" "$y"
    echo "tapped '$(printf '%s' "$hit" | cut -f1)' at $x,$y"
    ;;
  type)
    shift
    "$ADB" shell input text "$(printf '%s' "$*" | sed 's/ /%s/g')"
    ;;
  back) "$ADB" shell input keyevent KEYCODE_BACK ;;
  dark) "$ADB" shell cmd uimode night yes ;;
  light) "$ADB" shell cmd uimode night no ;;
  logcat) "$ADB" logcat -d -t 400 '*:E' | grep -iE "tare|AndroidRuntime|FATAL" || echo "No app errors" ;;
  help | *)
    cat <<'EOF'
scripts/dev.sh <command>

  build        Assemble the debug APK
  test         Unit, Robolectric and screenshot-render tests
  shots        Record Roborazzi screenshots into app/src/test/screenshots (then look at them)
  release      Assemble the signed, minified release APK

  emulator     Boot the tare_api36 emulator (creates it if missing) and wait for boot
  install      Build, install and launch the debug app (alias: run)
  launch       Launch the installed debug app
  reset        Clear the debug app's data
  screen [f]   Save a device screenshot (default .scratch/screen.png)
  ui           List visible elements: label, x, y
  tap <text>   Tap the first element whose text/description contains <text>
  type <text>  Type text into the focused field
  back         Press back
  dark|light   Switch the device theme
  logcat       Recent app errors
EOF
    ;;
esac
