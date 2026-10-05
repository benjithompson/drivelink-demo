#!/usr/bin/env bash
# Installs the debug APK and captures the Phase 1 screens to docs/ui/screens/.
# Since Phase 5 stage 1 the screens need a signed-in session and a running mock (see docs/ui-spec.md,
# "How to open a screen directly"). Sign in once on the device first. The p5-* screenshots were
# captured by hand against `python3 scripts/mock-server.py --port 8081`.
# Needs one running emulator or device. Usage: scripts/screenshots.sh
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=com.drivelink.demo
OUT="$(cd "$(dirname "$0")/.." && pwd)/docs/ui/screens"
mkdir -p "$OUT"

./gradlew -q :app:installDebug

shot() { # name, then extra "--es key value" pairs
  local name=$1; shift
  "$ADB" shell am force-stop "$PKG"
  "$ADB" shell am start -W -n "$PKG/.MainActivity" "$@" >/dev/null
  sleep 2
  "$ADB" exec-out screencap -p > "$OUT/$name.png"
  echo "captured $name"
}

shot 01-home
shot 02-home-dark --es dark true
shot 04-home-offline --es scenario vehicle-offline
shot 05-home-low-battery --es scenario low-battery
shot 06-home-ice --es vehicle solace
shot 07-controls --es screen controls
shot 08-climate --es screen climate
shot 09-pin --es screen pin
shot 10-charging --es screen charging
shot 11-status-door-ajar --es screen status --es scenario door-ajar
shot 12-carcare --es screen carcare
shot 13-maps --es screen maps
shot 14-menu --es screen menu
shot 15-gallery --es screen gallery
