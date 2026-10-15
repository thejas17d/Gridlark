# Tashlark

Offline Indian card games against computer players: **Call Break, 29, Court Piece, Mendikot**.
Kotlin + Jetpack Compose. The full guide (decision, architecture, rules, build and launch steps) is in
[`Tashlark.html`](Tashlark.html), open it in a browser.

## Build

GitHub Actions builds every push (`.github/workflows/android.yml`): tests, lint, debug APK
(`tashlark-debug-apk` artifact) and, on `main`, release APK + Play bundle (`tashlark-release`).

Locally on the owner's PC (Java there cannot reach services.gradle.org):

```
~/.android-toolchain/g.sh :engine:test :app:testDebugUnitTest :app:assembleDebug
```

## Secrets (GitHub → Settings → Secrets and variables → Actions)

| Secret | What |
|---|---|
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Release signing key |
| `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` | Real ads (otherwise Google test ads) |
| `PRIVACY_POLICY_URL`, `SUPPORT_EMAIL` | Shown in the app and policy |

Tags `v*` refuse to build without real ad IDs, the keystore and the support email.

## Folders

| Path | What |
|---|---|
| `engine/` | Pure Kotlin rules + AI for all four games, with tests |
| `app/src/main/java/com/tashlark/app/data` | Settings, house rules, saved matches (replay), match history, game catalog |
| `app/.../ui/table` | The card table, its controller and parts |
| `app/.../ui` | Home, settings, stats, welcome, theme, card art |
| `app/.../ads`, `billing` | AdMob + consent, remove-ads purchase |
| `docs/` | Privacy policy and app-ads.txt for GitHub Pages |

Never change the game ids (`callbreak`, `twentynine`, `courtpiece`, `mendikot`) or the deal seeding after release: saved matches replay from them.
