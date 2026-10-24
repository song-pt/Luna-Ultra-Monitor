#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export DEVELOPER_DIR=${DEVELOPER_DIR:-/Volumes/LKX/临时Xcode/Xcode.app/Contents/Developer}
DERIVED=${DERIVED_DATA_PATH:-$ROOT/build/DerivedData}
xcodebuild -project "$ROOT/LunaMonitor.xcodeproj" -scheme LunaMonitor -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath "$DERIVED" CODE_SIGNING_ALLOWED=NO build
STAGE="$ROOT/build/unsigned"
mkdir -p "$STAGE/Payload"
# Only remove this script's previous staging app, never a user's source or installed app.
rm -rf "$STAGE/Payload/LunaMonitor.app"
cp -R "$DERIVED/Build/Products/Release-iphoneos/LunaMonitor.app" "$STAGE/Payload/LunaMonitor.app"
rm -f "$ROOT/build/LunaMonitor-1.1.0-unsigned.ipa"
(cd "$STAGE" && /usr/bin/zip -qry "$ROOT/build/LunaMonitor-1.1.0-unsigned.ipa" Payload)
echo "$ROOT/build/LunaMonitor-1.1.0-unsigned.ipa"
