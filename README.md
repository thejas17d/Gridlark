# Gridlark

An offline daily logic-puzzle app for Android. Four new puzzles every day (Crowns, Sun & Moon, Trail, Sudoku), the same on every phone for a given date, each with exactly one solution that can be reached by logic alone. Free with considerate AdMob ads; a one-time "Remove ads" purchase (`remove_ads`). No account, no server.

The full guide (product, revenue model, architecture, engine, design system, build and launch steps, store listing, handoff notes) is **[Gridlark.html](Gridlark.html)**. Open it in a browser.

## Build

Requirements: JDK 21 and the Android SDK (compileSdk 37). No Android Studio needed.

```bash
./gradlew :engine:test                 # puzzle engine tests (valid, unique, deterministic)
./gradlew :app:testDebugUnitTest       # app unit tests
./gradlew :app:lintDebug               # lint (errors fail the build)
./gradlew :app:assembleDebug           # debug APK, package com.gridlark.app.debug, Google test ads
./gradlew :app:assembleRelease :app:bundleRelease   # release APK + AAB
```

Engine soak test: `./gradlew :engine:test -Psamples=200`. Crowns tier histogram: `./gradlew :engine:test -Pstats=1`.

Every push to `main` builds on GitHub Actions (`.github/workflows/android.yml`). Download the APK from the run's **Artifacts**:

| Artifact | Contents |
|---|---|
| `gridlark-debug-apk` | Debug APK (test ads) |
| `gridlark-release` | Release APK, AAB and R8 `mapping.txt` |
| `reports` | Test and lint reports (7 days) |

`versionCode` is the GitHub run number. `versionName` defaults to `1.0.0` and can be overridden with `VERSION_NAME`.

## Secrets

Set in GitHub: Settings → Secrets and variables → Actions. Locally, the same names work as `-P` properties or environment variables.

| Secret | Value | If missing |
|---|---|---|
| `KEYSTORE_BASE64` | Base64 of the upload keystore (`release.jks`) | Release is signed with a throwaway debug key |
| `KEYSTORE_PASSWORD` | Keystore password | |
| `KEY_ALIAS` | Key alias (e.g. `gridlark`) | |
| `KEY_PASSWORD` | Key password | |
| `ADMOB_APP_ID` | `ca-app-pub-…~…` | Release uses Google test ads (all four ad secrets are needed) |
| `ADMOB_BANNER_ID` | `ca-app-pub-…/…` | |
| `ADMOB_INTERSTITIAL_ID` | `ca-app-pub-…/…` | |
| `ADMOB_REWARDED_ID` | `ca-app-pub-…/…` | |
| `PRIVACY_POLICY_URL` | Public URL of `docs/privacy.md` | Stored in BuildConfig only |
| `SUPPORT_EMAIL` | Support address | In-app privacy policy shows a placeholder |

Base64 of the keystore: PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`, or Git Bash `base64 -w 0 release.jks`.

## Folder map

```
engine/                     Pure Kotlin/JVM puzzle engine (no Android)
  src/main/kotlin/com/gridlark/engine/
    Rng.kt                  SplitMix64: same seed, same puzzle on every device
    Difficulty.kt           EASY/MEDIUM/HARD/EXPERT (shown as Gentle/Steady/Tricky/Fiendish)
    crowns/ duo/ trail/ sudoku/   Generator, solver and rules per game
  src/test/                 GeneratorTest (validity, uniqueness, determinism), CrownsStats
app/                        Android app (Compose, Room, DataStore, WorkManager, AdMob, Billing)
  src/main/java/com/gridlark/app/
    GridlarkApplication.kt  AppContainer (manual DI)
    MainActivity.kt         Single activity, splash, theme, navigation host
    data/                   Room database, DataStore settings, games and daily seeds, streaks
    game/Boards.kt          Immutable board states, moves, hints, save/restore
    ads/ billing/ reminders/  AdsManager + banner, Remove ads purchase, daily reminder
    ui/                     AppNav, screens (home, puzzle, archive, stats, settings), theme, components
  src/main/assets/          privacy.txt, licenses.txt (shown in the app)
  src/main/res/font/        Fraunces, Atkinson Hyperlegible Next and Mono (OFL, bundled)
docs/                       GitHub Pages: index.md, privacy.md, app-ads.txt
gradle/libs.versions.toml   All dependency versions
.github/workflows/android.yml   CI build
Gridlark.html               The full project guide
```

## Do not change after release

`Daily.SEED_VERSION`, `Daily.EPOCH`, each `GameType.key` and `seedSalt`, the `Rng` algorithm, or the order of random calls in the generators. Any of these changes every daily puzzle and breaks saved progress.
