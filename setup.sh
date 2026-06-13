#!/bin/bash
# AppInsight - One-command development environment setup
# Usage: sudo bash setup.sh
set -euo pipefail

SDK_DIR="${SDK_DIR:-/root/android-sdk}"
ARCH=$(uname -m)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== AppInsight Build Environment Setup ==="
echo "SDK_DIR:    $SDK_DIR"
echo "Arch:       $ARCH"
echo "Project:    $SCRIPT_DIR"
echo ""

# ---- 1. System dependencies ----
echo "[1/6] Installing system packages..."
apt-get update -qq

JDK_INSTALLED=
for jdk in openjdk-17-jdk openjdk-21-jdk openjdk-25-jdk; do
    if apt-get install -y -qq "$jdk" 2>/dev/null; then
        echo "  Installed $jdk"
        JDK_INSTALLED=1
        break
    fi
done
if [ -z "$JDK_INSTALLED" ]; then
    echo "  WARNING: Could not install JDK. Trying default-jdk..."
    apt-get install -y -qq default-jdk 2>/dev/null || echo "  WARNING: No JDK installed. Build will fail."
fi

apt-get install -y -qq unzip wget 2>&1 | tail -1

# ---- 2. QEMU (aarch64 only) ----
if [ "$ARCH" = "aarch64" ]; then
    echo "[2/6] Installing QEMU user (for x86_64 binary emulation)..."
    apt-get install -y -qq qemu-user 2>&1 | tail -1

    # binfmt registration (optional, wrapper scripts work without it)
    if [ -f /proc/sys/fs/binfmt_misc/register ]; then
        echo ":qemu-x86_64:M::\x7fELF\x02\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x02\x00\x3e\x00:\xff\xff\xff\xff\xff\xfe\xfe\x00\xff\xff\xff\xff\xff\xff\xff\xff\xfe\xff\xff\xff:/usr/bin/qemu-x86_64:F" > /proc/sys/fs/binfmt_misc/register 2>/dev/null || true
    fi
else
    echo "[2/6] Skipping QEMU (native $ARCH)."
fi

# ---- 3. Android SDK cmdline-tools ----
echo "[3/6] Installing Android SDK cmdline-tools..."
mkdir -p "$SDK_DIR"
if [ ! -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    echo "  Downloading cmdline-tools (approx 150 MB)..."
    wget -q --show-progress "$CMDLINE_URL" -O /tmp/cmdline-tools.zip
    echo "  Extracting..."
    mkdir -p /tmp/cmdline-extract
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-extract
    mkdir -p "$SDK_DIR/cmdline-tools"
    mv /tmp/cmdline-extract/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-extract
    echo "  cmdline-tools installed."
else
    echo "  cmdline-tools already present."
fi

# ---- 4. SDK platforms & build-tools ----
echo "[4/6] Installing Android platform 35 + build-tools 36.1.0..."
echo "  This downloads approx 80 MB. Please wait..."
# yes may get SIGPIPE (broken pipe) once sdkmanager closes stdin;
# wrapping with || true prevents pipefail from masking sdkmanager's exit code
(yes 2>/dev/null || true) | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" \
    "platforms;android-35" "build-tools;36.1.0" > /tmp/sdkmanager.log 2>&1 || {
    echo "  ERROR: SDK install failed. See /tmp/sdkmanager.log"
    tail -20 /tmp/sdkmanager.log
    exit 1
}
echo "  SDK install complete."

# ---- 5. QEMU wrapper scripts (aarch64 only) ----
if [ "$ARCH" = "aarch64" ]; then
    echo "[5/6] Creating QEMU wrapper scripts..."
    mkdir -p "$SDK_DIR/tools/bin"
    for bin in aapt2 zipalign; do
        cat > "$SDK_DIR/tools/bin/$bin" << WRAPPER
#!/bin/bash
exec qemu-x86_64 "$SDK_DIR/build-tools/36.1.0/$bin" "\$@"
WRAPPER
        chmod +x "$SDK_DIR/tools/bin/$bin"
    done
    echo "  Wrappers created at $SDK_DIR/tools/bin/{aapt2,zipalign}"
else
    echo "[5/6] Skipping QEMU wrappers (native $ARCH)."
fi

# ---- 6. Verify ----
echo "[6/6] Verification..."
java -version 2>&1 | head -1 || echo "  WARNING: java not found"

if [ "$ARCH" = "aarch64" ]; then
    echo -n "  aapt2: "
    "$SDK_DIR/tools/bin/aapt2" version 2>&1 || {
        echo "FAILED - QEMU wrapper issue"
        echo "  Check: qemu-x86_64 exists: $(which qemu-x86_64 || echo 'MISSING')"
        exit 1
    }
else
    echo -n "  aapt2: "
    "$SDK_DIR/build-tools/36.1.0/aapt2" version 2>&1 || {
        echo "FAILED"
        echo "  Check: $SDK_DIR/build-tools/36.1.0/aapt2 exists: $(ls -la $SDK_DIR/build-tools/36.1.0/aapt2 2>&1)"
        exit 1
    }
fi

echo -n "  d8: "
"$SDK_DIR/build-tools/36.1.0/d8" --version 2>&1 | head -1 || {
    echo "FAILED"
    exit 1
}
echo -n "  apksigner: "
"$SDK_DIR/build-tools/36.1.0/apksigner" version 2>&1 | head -1 || {
    echo "FAILED"
    exit 1
}

echo ""
echo "=== Setup complete! ==="
echo ""
echo "To build the app:"
echo "  cd $SCRIPT_DIR && ./build.sh"
echo ""
echo "Output APK:"
echo "  $SCRIPT_DIR/build/apk/app-debug.apk"