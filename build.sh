#!/usr/bin/env bash
# Rebuild the greyscale MG4CPlay from a base APK.
#
#   ./build.sh [base-apk]
#
# Default base is base/MG4CPlay_0.2.9-mg4.96-swi68.14.apk (the known-good
# original). For a newer MG4CPlay, drop its APK in and pass it as the argument.
# Output: dist/MG4CPlay-grey.apk, signed with keys/grey.p12 (same key every
# time, so grey builds update over each other without an uninstall).
set -euo pipefail
cd "$(dirname "$0")"

BASE="${1:-base/MG4CPlay_0.2.9-mg4.96-swi68.14.apk}"
APKTOOL_VER="2.9.3"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

[ -f "$BASE" ] || { echo "base APK not found: $BASE"; exit 1; }

# apktool (from GitHub releases)
if [ ! -f tools/apktool.jar ]; then
  echo "==> fetching apktool $APKTOOL_VER"
  curl -sL -o tools/apktool.jar \
    "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar"
fi

echo "==> decompiling $BASE"
java -jar tools/apktool.jar d -f -o "$WORK/decoded" "$BASE" >/dev/null

echo "==> applying greyscale patch"
python3 patch/apply_patch.py "$WORK/decoded" patch

echo "==> rebuilding"
java -jar tools/apktool.jar b "$WORK/decoded" -o "$WORK/unsigned.apk" >/dev/null

echo "==> signing (keys/grey.p12)"
mkdir -p dist
if command -v apksigner >/dev/null 2>&1; then
  apksigner sign --ks keys/grey.p12 --ks-pass pass:greypass --ks-key-alias grey \
    --out dist/MG4CPlay-grey.apk "$WORK/unsigned.apk"
else
  # JDK-only fallback: v1 signature via jarsigner (fine for Android 9 / API 28)
  cp "$WORK/unsigned.apk" dist/MG4CPlay-grey.apk
  jarsigner -keystore keys/grey.p12 -storepass greypass -storetype PKCS12 \
    -sigalg SHA256withRSA -digestalg SHA-256 dist/MG4CPlay-grey.apk grey >/dev/null
fi

echo "==> done: dist/MG4CPlay-grey.apk"
ls -l dist/MG4CPlay-grey.apk | awk '{print "   "$5" bytes"}'
