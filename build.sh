#!/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$PROJECT_DIR/app"
BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
COMPILED_DIR="$BUILD_DIR/compiled"
DEX_DIR="$BUILD_DIR/dex"
APK_DIR="$BUILD_DIR/apk"
CLASSES_DIR="$BUILD_DIR/classes"

SDK_DIR="/root/android-sdk"
PLATFORM="$SDK_DIR/platforms/android-35/android.jar"
BUILD_TOOLS="$SDK_DIR/build-tools/36.1.0"

AAPT2="/root/android-sdk/tools/bin/aapt2"
D8="$BUILD_TOOLS/d8"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="/root/android-sdk/tools/bin/zipalign"

echo "=== Clean ==="
rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$COMPILED_DIR" "$DEX_DIR" "$APK_DIR" "$CLASSES_DIR"

echo "=== aapt2 compile ==="
find "$APP_DIR/src/main/res" -type f | while read -r res_file; do
    rel_path="${res_file#$APP_DIR/src/main/res/}"
    out_dir="$COMPILED_DIR/$(dirname "$rel_path")"
    mkdir -p "$out_dir"
    "$AAPT2" compile -o "$out_dir/" "$res_file"
done

echo "=== aapt2 link ==="
FLAT_FILES=()
while IFS= read -r -d '' f; do
    FLAT_FILES+=(-R "$f")
done < <(find "$COMPILED_DIR" -name "*.flat" -print0)
"$AAPT2" link \
    -o "$APK_DIR/unaligned.apk" \
    -I "$PLATFORM" \
    --manifest "$APP_DIR/src/main/AndroidManifest.xml" \
    --java "$GEN_DIR" \
    --auto-add-overlay \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    --version-code 1 \
    --version-name "1.0" \
    "${FLAT_FILES[@]}"

echo "=== javac ==="
SOURCES=()
while IFS= read -r -d '' f; do
    SOURCES+=("$f")
done < <(find "$APP_DIR/src/main/java" -name "*.java" -print0)
javac -d "$CLASSES_DIR" -classpath "$PLATFORM:$GEN_DIR" -source 17 -target 17 "${SOURCES[@]}"

echo "=== d8 ==="
"$D8" --lib "$PLATFORM" --output "$DEX_DIR/" --min-api 21 \
    $(find "$CLASSES_DIR" -name "*.class")

echo "=== package DEX ==="
(cd "$DEX_DIR" && zip -q "$APK_DIR/unaligned.apk" classes.dex)

echo "=== zipalign ==="
"$ZIPALIGN" -v -p 4 "$APK_DIR/unaligned.apk" "$APK_DIR/aligned.apk" 2>&1 | tail -1

echo "=== sign ==="
KEYSTORE="$PROJECT_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v -keystore "$KEYSTORE" \
        -alias androiddebugkey -storepass android -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi
"$APKSIGNER" sign \
    --ks "$KEYSTORE" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out "$APK_DIR/app-debug.apk" \
    "$APK_DIR/aligned.apk"

echo ""
APK="$APK_DIR/app-debug.apk"
ls -lh "$APK"
echo "Verified: $(apksigner verify "$APK" 2>&1 && echo OK || echo FAIL)"
