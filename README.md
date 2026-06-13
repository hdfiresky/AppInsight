# AppInsight

Android app manager & APK extractor. Lists all installed apps, inspects permissions, and extracts APK files to your Downloads folder.

Built with **pure Android SDK** — zero external dependencies, no AndroidX, no Kotlin, no Gradle plugins needed for the standalone build.

## Features

- **App list** — all installed apps with search/filter, system apps marked
- **App details** — package name, version, target SDK, install/update dates, APK size
- **Permission scanner** — every declared permission with granted status, dangerous permissions highlighted in red
- **APK extractor** — save any app's APK to `Downloads/AppInsight/` (uses `MediaStore` on Android 10+, direct file on older)
- **Quick actions** — tap for details, long-press for Open / App Info (system settings)

## Quick start (fresh Ubuntu)

```bash
chmod +x setup.sh && sudo ./setup.sh
```

This installs: OpenJDK 17, Android SDK cmdline-tools, platform android-35, build-tools 36.1.0, QEMU user (for aarch64 hosts).

Then build:

```bash
./build.sh
```

Output: `build/apk/app-debug.apk`

Install on device:

```bash
cp build/apk/app-debug.apk /path/to/device/Download/appinsight.apk
```

## Build methods

### Method 1: Standalone `build.sh` (recommended, offline)

Uses `aapt2` + `d8` + `zipalign` + `apksigner` directly. No Gradle, no network needed after SDK install.

```bash
./build.sh
```

| Step | Tool | Notes |
|------|------|-------|
| Resource compile | `aapt2` (x86_64) | via QEMU on aarch64 |
| APK link | `aapt2` link | same |
| Java compile | `javac` (JDK 17) | native |
| DEX | `d8` | Java-based, native |
| Align | `zipalign` (x86_64) | via QEMU on aarch64 |
| Sign | `apksigner` | Java-based, native |

### Method 2: Gradle (requires network)

The project includes Gradle wrapper files for compatibility. Requires Internet for first build (downloads AGP + dependencies):

```bash
./gradlew assembleDebug
```

## Requirements

| Tool | Version | Why |
|------|---------|-----|
| OpenJDK | 17+ | javac, d8, apksigner |
| Android SDK | platform 35, build-tools 36.1.0 | aapt2, zipalign |
| QEMU user | (only on aarch64) | run x86_64 aapt2/zipalign |

### SDK directory

The build script expects SDK at `/root/android-sdk/`. Override with:
```bash
SDK_DIR=/path/to/sdk ./build.sh
```

## Project structure

```
├── build.sh              # Standalone APK builder
├── setup.sh              # One-command tool install
├── install.sh            # ADB or copy-to-Download install
├── app/
│   ├── build.gradle.kts  # Gradle build (optional)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/appinsight/
│       │   ├── MainActivity.java
│       │   ├── DetailActivity.java
│       │   ├── AppListAdapter.java
│       │   └── AppInfo.java
│       └── res/
│           ├── layout/         # activity_main, activity_detail, item_app, item_info, item_permission, search_view
│           ├── menu/           # main.xml
│           ├── values/         # strings, colors, themes
│           ├── drawable/       # adaptive icon layers
│           └── mipmap-anydpi-v26/  # adaptive icon pointers
└── gradle/                    # Gradle wrapper (optional)
```

## Compatibility

| Android | API | minSdk | targetSdk | Works |
|---------|-----|--------|-----------|-------|
| 5.0+ | 21+ | 21 | 35 | ✅ |
| 13 | 33 | 21 | 35 | ✅ |
| 14 | 34 | 21 | 35 | ✅ |
| 15 | 35 | 21 | 35 | ✅ native |
| 16 | 36 | 21 | 35 | ✅ compat |
