#!/usr/bin/env bash
# Rebuild the greyscale MG4CPlay from a base APK.
#
#   ./build.sh [base-apk]
#
# Default base is base/MG4CPlay_0.2.9-mg4.96-swi68.14.apk (the known-good
# original). For a newer MG4CPlay, drop its APK in and pass it as the argument.
# Output: dist/MG4CPlay-grey.apk, signed with keys/platform.p12 (same key every
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

echo "==> signing (keys/platform.p12)"
mkdir -p dist
if command -v apksigner >/dev/null 2>&1; then
  apksigner sign --ks keys/platform.p12 --ks-pass pass:android --ks-key-alias platform \
    --out dist/MG4CPlay-grey.apk "$WORK/unsigned.apk"
else
  # MG4CPlay is sharedUserId=android.uid.system and the car's system UID uses the
  # APK Signature Scheme v3 - a v1/v2-only signature is REJECTED with
  # INSTALL_FAILED_SHARED_USER_INCOMPATIBLE. Sign with v3 via apksig.
  : "${APKSIG_JARS:?set APKSIG_JARS to d8.jar:apksig.jar (from the launcher tools) to sign without apksigner}"
  javac -cp "$APKSIG_JARS" -d "$WORK" tools/SignV3.java
  java -cp "$WORK:$APKSIG_JARS" SignV3 keys/platform.p12 android platform \
    "$WORK/unsigned.apk" dist/MG4CPlay-grey.apk
fi

echo "==> done: dist/MG4CPlay-grey.apk"
ls -l dist/MG4CPlay-grey.apk | awk '{print "   "$5" bytes"}'
