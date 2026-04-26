# DinoRun — Native Android Dino-style Game

A modern, fully offline Chrome Dino-inspired endless runner written in **Kotlin** with
Material 3 UI, Room (SQLite) persistence, multi-level progression, per-player score
tracking, a leaderboard, full game history, and an optional **landscape split-screen
two-player mode** with independent multi-touch input.

## Features

- Endless-runner gameplay with jump (tap) and duck (long-press)
- **Dog bark sound on every jump** (drop your own `dog_bark.mp3` into `app/src/main/res/raw/`,
  otherwise a synthesized bark plays automatically)
- 5 progressive difficulty levels (faster speed, more obstacle types, day↔night cycle,
  pterodactyl birds at higher levels)
- Shield power-ups that absorb one collision
- Player name entry — every score is attached to a player
- **Leaderboard** with top scores across all players (Material 3 cards, animations)
- **Full game history** with date, time, duration, level reached, obstacles avoided,
  and player name
- **Two-player split-screen** mode locked to landscape — each half is an independent
  game with its own touch zone; the two halves cannot interfere with each other
- Pause / resume, haptic feedback on crash, dark mode toggle
- 100% offline — no network permission requested

## Build locally

```bash
cd android-dino-game
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Open the project folder (`android-dino-game/`) in **Android Studio Hedgehog or newer** and
press Run for an interactive build.

## CI — automatic debug APK build

The repo ships a GitHub Actions workflow at `.github/workflows/android-build.yml`. On every
push to `main` (or `master`), it:

1. Sets up JDK 17 + Gradle 8.7
2. Generates the Gradle wrapper
3. Runs `./gradlew assembleDebug`
4. Uploads `app-debug.apk` as a downloadable workflow artifact named **DinoRun-debug-apk**

You can also trigger it manually from the **Actions** tab → *Build Debug APK* →
*Run workflow*.

## Project layout

```
android-dino-game/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/dinorun/game/
│       │   ├── DinoApp.kt
│       │   ├── MainActivity.kt
│       │   ├── PlayerEntryActivity.kt
│       │   ├── GameActivity.kt
│       │   ├── MultiplayerSetupActivity.kt
│       │   ├── MultiplayerActivity.kt
│       │   ├── LeaderboardActivity.kt
│       │   ├── HistoryActivity.kt
│       │   ├── SettingsActivity.kt
│       │   ├── game/        # game engine, sprites, view
│       │   ├── data/        # Room database, DAO, entities
│       │   ├── adapter/     # RecyclerView adapters
│       │   └── util/        # SoundManager, Prefs
│       └── res/             # layouts, themes, drawables, sounds
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Adding a real dog bark sound

1. Place an `mp3`/`ogg`/`wav` file at
   `android-dino-game/app/src/main/res/raw/dog_bark.mp3`.
2. Rebuild — `SoundManager` automatically picks it up.
