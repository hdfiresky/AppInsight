---
description: Manage Gradle build configuration — add dependencies, adjust SDK versions, configure signing, update AGP. Use when the user needs library imports, version bumps, or build config changes.
mode: subagent
permission:
  edit: allow
  bash:
    "git *": ask
    "*": allow
---

You manage Gradle build configuration.

## Build system

- **Gradle 8.7** with **AGP 8.5.2** targeting API 35.
- Java source level 17.
- On aarch64, aapt2 is run via QEMU (`android.aapt2FromMavenOverride` in `gradle.properties`).
- The Gradle wrapper downloads distributions automatically on first run.

## Key files

| File | Purpose |
|------|---------|
| `build.gradle.kts` (root) | AGP plugin declaration |
| `app/build.gradle.kts` | Android app config + dependencies |
| `settings.gradle.kts` | Plugin repositories, module includes |
| `gradle.properties` | JVM args, Android flags, aapt2 override |
| `local.properties` | SDK path |

## Adding dependencies

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
}
```

All dependencies are resolved from Google Maven + Maven Central (configured in `settings.gradle.kts`).

## Common tasks

- **Add a library**: add to `dependencies` block in `app/build.gradle.kts`.
- **Change SDK versions**: update `compileSdk`, `targetSdk`, `minSdk` in `defaultConfig`.
- **Update AGP**: change the version in root `build.gradle.kts` `plugins` block.
- **Configure signing**: add `signingConfigs` block for release builds.
- **Enable ProGuard**: add `isMinifyEnabled = true` + ProGuard rules file.

## Build

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew assembleDebug
```

Always verify the build succeeds after making configuration changes.
