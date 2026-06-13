# Building an Android APK from Scratch — The Full Pipeline

This document walks through every tool involved in turning Java source code + XML resources into a signed `.apk` file that Android devices can install. You already know how to code — this is about what happens *between* your `javac` and the phone.

All references point to files in this repository.

---

## Table of Contents

1. [What's in an APK?](#1-whats-in-an-apk)
2. [Fresh Install of Build Tools](#2-fresh-install-of-build-tools)
3. [The Build Pipeline (step by step)](#3-the-build-pipeline-step-by-step)
   - [aapt2 compile](#31-aapt2-compile--resource-to-binary)
   - [aapt2 link](#32-aapt2-link--assemble-the-apk-shell)
   - [javac](#33-javac--java-bytecode)
   - [d8](#34-d8--dex-bytecode)
   - [zip](#35-zip--dex-into-apk)
   - [zipalign](#36-zipalign--memory-alignment)
   - [apksigner](#37-apksigner--digital-signature)
4. [aarch64 (ARM) Fixes](#4-aarch64-arm-fixes)
5. [Alternative: Gradle](#5-alternative-gradle)
6. [Installing on a Device](#6-installing-on-a-device)
7. [Reference: Project Layout](#7-reference-project-layout)

---

## 1. What's in an APK?

An `.apk` file is just a ZIP archive containing:

| Entry | What it is | Who makes it |
|---|---|---|
| `AndroidManifest.xml` | binary XML — permissions, activities, minSdk | `aapt2 link` |
| `classes.dex` | Dalvik Executable — your Java compiled for Android's VM | `d8` |
| `resources.arsc` | compiled resource table (strings, layouts, etc.) | `aapt2 link` |
| `res/` | compiled binary XML resources | `aapt2 compile` |
| `META-INF/` | signature files | `apksigner` |
| `lib/` | native .so libraries (not in this project) | NDK |

A device will **refuse** to install an unsigned or misaligned APK. Every tool in the pipeline exists to make sure these bits are correct.

---

## 2. Fresh Install of Build Tools

Script: [`setup.sh`](setup.sh)

### What it installs

| Tool | Why it exists |
|---|---|
| **OpenJDK 17** | Runs `javac`, `d8`, `apksigner`. Android SDK tools are mostly Java themselves. |
| **Android SDK cmdline-tools** | The `sdkmanager` binary — Google's package manager for SDK components. |
| **platforms;android-35** | `android.jar` — the Android framework API you compile against (like `rt.jar` for standard Java). |
| **build-tools;36.1.0** | `aapt2`, `zipalign`, `d8`, `apksigner` — the actual build executables. |
| **QEMU user** (aarch64 only) | Emulates x86_64 binaries on ARM systems (see §4). |

### Step-by-step what `setup.sh` does

```bash
# 1. Install JDK (tries 17, 21, 25, falls back to default-jdk)
apt-get install openjdk-17-jdk

# 2. Install system utilities
apt-get install unzip wget

# 3. If on aarch64, install QEMU for x86_64 emulation
apt-get install qemu-user

# 4. Download Android command-line tools from Google
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -o commandlinetools-linux-*.zip
mv cmdline-tools /root/android-sdk/cmdline-tools/latest/

# 5. Use sdkmanager to download platform + build-tools
sdkmanager "platforms;android-35" "build-tools;36.1.0"

# 6. On aarch64: create wrapper scripts for aapt2 and zipalign
#    because they are x86_64 native binaries
```

### Where things land

```
/root/android-sdk/
├── cmdline-tools/latest/bin/sdkmanager   # package manager
├── platforms/android-35/android.jar      # framework API library
├── build-tools/36.1.0/
│   ├── aapt2                             # resource compiler
│   ├── zipalign                          # alignment tool
│   ├── d8                                # Java→DEX compiler
│   └── apksigner                         # APK signer
└── tools/bin/                            # (aarch64) QEMU wrappers
    ├── aapt2
    └── zipalign
```

### Manual alternative: `sdkmanager`

If you ever need to install a different version:

```bash
export SDK_DIR=/root/android-sdk
$SDK_DIR/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SDK_DIR \
    "platforms;android-34" \
    "build-tools;34.0.0"
```

---

## 3. The Build Pipeline (step by step)

Script: [`build.sh`](build.sh)

The pipeline takes your source tree and produces `build/apk/app-debug.apk`. Here is every step, what it does, and why it exists.

### 3.1 aapt2 compile — resource → binary

```
aapt2 compile -o build/compiled/ app/src/main/res/layout/activity_main.xml
```

Android resources (XML layouts, strings, colors, drawables) are **not** shipped as plain text XML. They are compiled into a compact binary format that the device can parse without an XML parser.

**Input:** `app/src/main/res/*` (any XML or file under `res/`)
**Output:** `.flat` files (compiled binary resources) in `build/compiled/`

Each resource file becomes one `.flat` file. The directory structure mirrors the original.

```bash
# build.sh lines 49-54 — one aapt2 compile call per resource file
find "$APP_DIR/src/main/res" -type f | while read -r res_file; do
    "$AAPT2" compile -o "$out_dir/" "$res_file"
done
```

### 3.2 aapt2 link — assemble the APK shell

```
aapt2 link \
    -o build/apk/unaligned.apk \
    -I /root/android-sdk/platforms/android-35/android.jar \
    --manifest app/src/main/AndroidManifest.xml \
    --java build/gen \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    -R <all .flat files>
```

This is the "linker" for Android resources. It:

1. Reads your `AndroidManifest.xml` and validates it (activities declared? permissions valid?).
2. Merges all `.flat` compiled resources into a resource table (`resources.arsc`).
3. Assigns integer IDs to every resource (`R.id.my_button` → `0x7f010000`).
4. Generates `R.java` (into `build/gen/`) so your Java code can reference resources by integer ID.
5. Produces a **partial APK** — it has resources and manifest, but no DEX code yet.

**Key flags:**

| Flag | Meaning |
|---|---|
| `-I` | "include" — the framework jar (`android.jar`) so aapt knows what built-in resources exist (`@android:string/ok` etc.) |
| `--java` | where to write `R.java` |
| `-R` | a compiled `.flat` resource file to include (repeat for every file) |
| `--auto-add-overlay` | automatically add `android:isExtension` attributes from overlays |
| `--min-sdk-version` / `--target-sdk-version` | written into manifest binary |

**Output:** `build/apk/unaligned.apk` — an unsigned, unaligned, code-less APK shell.

### 3.3 javac — Java bytecode

```
javac -d build/classes/ \
    -classpath android.jar:build/gen/ \
    -source 17 -target 17 \
    app/src/main/java/com/example/appinsight/*.java
```

Standard Java compilation. Nothing Android-specific here except the classpath:

- `android.jar` — the Android framework API (like `javac -cp rt.jar` for desktop Java).
- `build/gen/` — contains `R.java` generated by `aapt2 link`, so `R.layout.activity_main` compiles.

**`-source 17 -target 17`**: Java 17 language features, Java 17 bytecode format. The `compileOptions` in `build.gradle.kts` match this.

**Output:** `.class` files in `build/classes/`.

### 3.4 d8 — DEX bytecode

```
d8 --lib /root/android-sdk/platforms/android-35/android.jar \
    --output build/dex/ \
    --min-api 21 \
    build/classes/com/example/appinsight/*.class
```

Android does **not** run JVM bytecode (`.class` files). It runs Dalvik bytecode (`.dex` files) on a register-based VM (ART on modern Android). `d8` is the compiler that translates one to the other.

**What `d8` does:**

- Parses all `.class` files (yours + dependencies).
- Runs shrinker/optimizer (the same engine as R8, but without ProGuard rules unless you pass `--pg-conf`).
- Emits `classes.dex` — a single file containing all your bytecode in DEX format.

**`--lib`**: tells d8 where to find the framework classes so it can resolve `Activity`, `View`, etc.
**`--min-api 21`**: which DEX format version to target (API 21 = Android 5.0 = DEX-042, which supports multi-dex and large methods).

**Output:** `build/dex/classes.dex`.

### 3.5 zip — DEX into APK

```
(cd build/dex && zip -q build/apk/unaligned.apk classes.dex)
```

Remember `unaligned.apk` from step 3.2? It's a ZIP with resources but no code. This step adds `classes.dex` into it. Now the APK has both resources and bytecode.

**Why not use aapt2 for this?** aapt2 only deals with resources. DEX injection is a separate step historically; AGP/Gradle wraps this in its own packaging.

### 3.6 zipalign — memory alignment

```
zipalign -v -p 4 build/apk/unaligned.apk build/apk/aligned.apk
```

ZIP entries in an APK must be **4-byte aligned** in the archive. Why? Because Android `mmap`s files directly from the APK ZIP. If a resource like `resources.arsc` starts at byte offset 4, 8, 12, etc., the kernel can map it directly. If it starts at byte 7, the CPU takes an unaligned access fault (slow) or the system must copy the data (slow, more memory).

`zipalign` rewrites the ZIP so every entry's data starts at a multiple of 4 bytes.

**`-p 4`**: page alignment = 4 bytes.
**`-v`**: verbose (prints "Verification successful" when done).

### 3.7 apksigner — digital signature

```
apksigner sign \
    --ks debug.keystore \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out build/apk/app-debug.apk \
    build/apk/aligned.apk
```

Android **requires** every APK to be cryptographically signed. The signature is verified at install time:

- The signer creates a digest of every entry in the APK.
- It signs the digest with a private key.
- The public key certificate is embedded in `META-INF/`.
- The device trusts the certificate (debug certs are trusted on debug builds; release certs need a known keystore).

**`debug.keystore`**: generated on first build by `keytool` (line 93-96 of build.sh). It uses:
```
keytool -genkey -v -keystore debug.keystore \
    -alias androiddebugkey \
    -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
```

This creates a self-signed RSA 2048-bit key valid for ~27 years. The password is literally `"android"` — this is the standard debug keystore used by Android Studio / Gradle.

---

## 4. aarch64 (ARM) Fixes

If you're on an ARM machine (Apple Silicon Mac, Raspberry Pi, AWS Graviton, etc.), you hit a problem: Google distributes `aapt2` and `zipalign` only as **x86_64 native binaries**. ARM Macs get them via Rosetta; on Linux ARM you need QEMU.

### The problem

```
$ /root/android-sdk/build-tools/36.1.0/aapt2
-bash: cannot execute: required file not found
```

Because it's an x86_64 ELF binary and the ARM kernel doesn't know how to run it.

### The fix

**Step 1:** Install QEMU user-mode emulator and the x86_64 C library cross-package.

```bash
apt-get install qemu-user-static libc6-amd64-cross
```

- `qemu-user-static` provides `qemu-x86_64-static` — a statically-linked ARM64 binary that can run x86_64 ELF binaries by interpreting their instructions.
- `libc6-amd64-cross` provides `/usr/x86_64-linux-gnu/lib/ld-linux-x86-64.so.2` and `libc.so.6` — the x86_64 dynamic linker and C library that the x86_64 binaries expect to load at runtime.

**Step 2:** Test that it works:

```bash
qemu-x86_64-static -L /usr/x86_64-linux-gnu/ \
    /root/android-sdk/build-tools/36.1.0/aapt2 version
# Output: Android Asset Packaging Tool (aapt) 2.20-14042983
```

**Step 3:** Update the wrapper scripts at `$SDK_DIR/tools/bin/{aapt2,zipalign}`.

The original `setup.sh` creates wrappers that use `qemu-x86_64` (the dynamically-linked QEMU) without `-L`:

```bash
# Original (broken on systems without binfmt_misc):
exec qemu-x86_64 "$SDK_DIR/build-tools/36.1.0/$bin" "$@"
```

The fix uses `qemu-x86_64-static` (statically linked, works without binfmt kernel support) and passes `-L` to point to the x86_64 sysroot:

```bash
# Fixed wrapper:
exec qemu-x86_64-static -L /usr/x86_64-linux-gnu/ \
    "/root/android-sdk/build-tools/36.1.0/$bin" "$@"
```

`-L /usr/x86_64-linux-gnu/` tells QEMU where to find the x86_64 dynamic linker and shared libraries (`ld-linux-x86-64.so.2`, `libc.so.6`, `libgcc_s.so.1`). Without it, QEMU looks at `/lib64/ld-linux-x86-64.so.2` on the host filesystem, which doesn't exist on ARM.

### Why `qemu-user-static` instead of `qemu-user`

`qemu-user` (dynamic) relies on kernel `binfmt_misc` support — a kernel module that automatically invokes QEMU when an x86_64 binary is executed. Many cloud containers (Docker, Kubernetes pods, Termux) don't have `binfmt_misc` or don't have the kernel module loaded.

`qemu-user-static` (static) provides the same emulation but the QEMU binary itself is statically linked (no runtime dependencies like `libglib`), and the registration method is file-based (via `/proc/sys/fs/binfmt_misc/register`) rather than relying on a running systemd service.

The `-L` flag is always needed regardless of binfmt — it tells QEMU where the guest's root filesystem is for loading shared libraries.

### Verification

```bash
# aapt2
bash /root/android-sdk/tools/bin/aapt2 version
# → Android Asset Packaging Tool (aapt) 2.20-14042983

# zipalign
bash /root/android-sdk/tools/bin/zipalign
# → Zip alignment utility

# These are Java-based, run natively on ARM:
/root/android-sdk/build-tools/36.1.0/d8 --version
/root/android-sdk/build-tools/36.1.0/apksigner version
```

---

## 5. Alternative: Gradle

The project also includes Gradle wrapper files (`gradlew`, `build.gradle.kts`, `settings.gradle.kts`) for compatibility with Android Studio and CI.

### How Gradle differs

Instead of running each tool manually, Gradle uses the **Android Gradle Plugin (AGP)** which internally orchestrates the same tools. The pipeline is identical; AGP just wraps it in tasks.

```bash
./gradlew assembleDebug
```

This downloads AGP (`com.android.application` version 8.5.2) and dependencies (AndroidX, appcompat) from Maven Central, then runs the same aapt2 → javac → d8 → zipalign → apksigner chain.

**Pros:**
- Handles dependencies automatically (Maven/Gradle)
- Multi-module builds
- ProGuard/R8 minification built-in
- Android Studio integration

**Cons:**
- Requires internet for the first build (downloads AGP + deps)
- Slower startup (Gradle daemon, dependency resolution)
- Heavy (~200 MB+ .gradle cache)

### `build.gradle.kts` equivalents

| build.sh step | Gradle equivalent | build.gradle.kts |
|---|---|---|
| aapt2 compile | `mergeDebugResources` | (implicit) |
| aapt2 link | `processDebugResources` | (implicit) |
| javac | `compileDebugJavaWithJavac` | `compileOptions { sourceCompatibility = VERSION_17 }` |
| d8 | `dexBuilderDebug` / `minify...WithR8` | (implicit) |
| zipalign | `zipAlignDebug` | (implicit) |
| apksigner | `signDebug` | (implicit) |

The `build.gradle.kts` file:

```kotlin
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

This tells AGP: compile against API 35, allow devices down to API 21, target API 35, use Java 17 bytecode. Same values passed to `--min-sdk-version`, `--target-sdk-version`, and `-source/-target` in `build.sh`.

---

## 6. Installing on a Device

Script: [`install.sh`](install.sh)

Two methods, tried in order:

### Method A: ADB (Android Debug Bridge)

```bash
adb install -r build/apk/app-debug.apk
```

`-r` means "replace existing app" (reinstall).

Prerequisites:
- USB debugging enabled on device (Developer Options → USB debugging)
- `adb` installed on your machine

### Method B: Copy to Downloads

If ADB isn't available, `install.sh` copies the APK directly to the device's shared storage:

```bash
cp build/apk/app-debug.apk /storage/emulated/0/Download/app-debug.apk
```

Then open the file manager on your Android device and tap the APK to install. You'll need to enable "Install from unknown sources" for your file manager app.

---

## 7. Reference: Project Layout

```
AppInsight/
├── setup.sh                     # One-command environment installer
├── build.sh                     # Standalone APK builder (no Gradle)
├── install.sh                   # Push APK to device
├── build.gradle.kts             # Root Gradle plugin declaration
├── settings.gradle.kts          # Project name + repository config
├── gradle.properties            # JVM args, AndroidX, Jetifier
├── gradlew / gradlew.bat        # Gradle wrapper scripts
├── gradle/                      # Gradle wrapper JAR
├── debug.keystore               # Generated on first build
├── build/                       # Output directory (gitignored)
│   ├── gen/R.java              # Generated by aapt2 link
│   ├── compiled/*.flat         # Compiled resources
│   ├── classes/*.class         # Javac output
│   ├── dex/classes.dex         # DEX bytecode
│   └── apk/app-debug.apk       # Final signed APK
└── app/
    ├── build.gradle.kts         # App module Gradle config
    ├── proguard-rules.pro       # ProGuard rules (empty for debug)
    └── src/main/
        ├── AndroidManifest.xml  # App declaration
        ├── java/.../            # Java sources (4 files)
        └── res/                 # Resources (layouts, drawables, values, menu)
```

---

## Cheat Sheet: What each tool is responsible for

| Tool | Genre | Input | Output | Ships with |
|---|---|---|---|---|
| `aapt2` | Resource compiler | XML resources, manifest | `.flat`, `R.java`, APK shell | Android build-tools |
| `javac` | Java compiler | `.java` files | `.class` files | JDK |
| `d8` | DEX compiler | `.class` files | `classes.dex` | Android build-tools |
| `zip` | Archiver | `classes.dex` + APK shell | Updated APK shell | system (Info-ZIP) |
| `zipalign` | Aligner | Unaligned APK | 4-byte aligned APK | Android build-tools |
| `apksigner` | Cryptographic signer | Aligned APK | Signed APK | Android build-tools |
| `keytool` | Keystore generator | — | `.keystore` file | JDK |
| `qemu-x86_64-static` | Emulator (aarch64 only) | x86_64 binary | Emulated execution | `qemu-user-static` |
