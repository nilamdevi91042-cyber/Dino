# Workspace

## Overview

This repo hosts a **native Android (Kotlin) Chrome Dino-style game** in `android-dino-game/`,
plus a GitHub Actions workflow that automatically builds the debug APK.

The pre-existing pnpm artifact scaffolding (api-server, mockup-sandbox) is unused for this
project — Android apps are not a Replit artifact type, so the project is built and
distributed via the GitHub Actions APK pipeline rather than a Replit preview.

## DinoRun (Android app)

- Location: `android-dino-game/`
- Language: Kotlin · Min SDK 23 · Target/Compile SDK 34 · JDK 17
- Build system: Gradle 8.7, AGP 8.5.2, KSP 1.9.24-1.0.20
- Storage: Room 2.6.1 (SQLite) at `dinorun.db`
- UI: Material 3 + viewBinding + custom `SurfaceView` for the game canvas
- Audio: `SoundManager` auto-detects `res/raw/dog_bark.*` if present; otherwise plays
  a synthesized two-syllable woof via `AudioTrack`. Crash/pickup/level-up effects use
  `ToneGenerator`. Vibration via `VibratorManager` on Android 12+.
- Multiplayer: single `SurfaceView` hosting two independent `GameEngine` instances.
  Touch routing is done in `MultiplayerGameView` by mapping each pointer to its half
  on `ACTION_DOWN`/`ACTION_POINTER_DOWN`, so simultaneous taps on each side never
  interfere. Locked to landscape via the manifest.

### Building locally

```bash
cd android-dino-game
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

APK lands at `android-dino-game/app/build/outputs/apk/debug/app-debug.apk`.

### CI

`.github/workflows/android-build.yml` runs on push / PR / manual dispatch:
sets up JDK 17 + Gradle 8.7, generates the wrapper, runs `assembleDebug`, and uploads
the APK as the **DinoRun-debug-apk** workflow artifact (30-day retention).

## Notes

- The Gradle wrapper jar is **intentionally not committed** — CI generates it via
  `gradle wrapper`. Locally, run that same command once before `./gradlew`.
- A real bark MP3 can be dropped into `app/src/main/res/raw/dog_bark.mp3` and will be
  picked up automatically; otherwise the synthesized fallback plays.
