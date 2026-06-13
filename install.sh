#!/bin/bash
# Install the APK on a connected Android device
set -euo pipefail

APK="$(cd "$(dirname "$0")" && pwd)/build/apk/app-debug.apk"

if [ ! -f "$APK" ]; then
    echo "APK not found. Run build.sh first."
    exit 1
fi

# Try adb first
if command -v adb &>/dev/null && adb devices 2>/dev/null | grep -qP '^\w+\s+device$'; then
    echo "Installing via ADB..."
    adb install -r "$APK"
    exit $?
fi

# Fallback: copy to shared storage
DEST="/storage/emulated/0/Download/$(basename "$0" .sh).apk"
if [ -d "/storage/emulated/0/Download" ]; then
    cp "$APK" "$DEST"
    echo "APK copied to $DEST"
    echo "Open the file manager on your device to install."
    exit 0
fi

echo "No ADB device found and no shared storage accessible."
echo "APK at: $APK"
exit 1
