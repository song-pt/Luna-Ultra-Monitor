#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export DEVELOPER_DIR=${DEVELOPER_DIR:-/Volumes/LKX/临时Xcode/Xcode.app/Contents/Developer}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
xcrun swiftc -module-cache-path "$TMP/modules" "$ROOT/LunaMonitor/ProtocolCore.swift" "$ROOT/Tests/main.swift" -o "$TMP/tests"
"$TMP/tests" "$ROOT/LunaMonitor/Monitor.metal"
