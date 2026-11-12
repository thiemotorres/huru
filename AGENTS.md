# AGENTS.md

Guidance for AI agents and contributors working on **Huru**, an ad-free IPTV player (Xtream Codes) for Amazon Fire TV.

## Language

**Everything in English:** code, comments, UI strings, docs, commit messages, PR descriptions and issues. (Conversation with the maintainer may be in German; the repository is not.)

## Build & test

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home   # JDK 17 required
./gradlew testDebugUnitTest      # JVM unit tests (parsing, text helpers)
./gradlew assembleRelease        # minified APK, signed with the debug key
adb install -r app/build/outputs/apk/release/app-release.apk
```

- Always build and test the **release** variant before reporting performance – debug Compose is much slower.
- Pure logic (parsing, text cleanup) gets a small JUnit test in `app/src/test`. `org.json` is available in tests as a real dependency.

## Testing on the device

- The target is a Fire TV Stick 4K Max (Fire OS 8 / Android 11) reachable via `adb`.
- **Ask before sending key events or installing** – the TV is in daily use. Taking a screenshot (`adb exec-out screencap -p`) is fine.
- Navigate step by step and check focus with `uiautomator dump` instead of firing long blind key sequences.
- Video renders into a `TextureView`, so it appears in screenshots.

## Architecture

| File | Responsibility |
|---|---|
| `Xtream.kt` | Xtream Codes API client, JSON parsing, list cache (`loadLists`) |
| `MainActivity.kt` | App shell, login, mode switch (TV/Movies/Series), live channel browser |
| `Guide.kt` | Channel rows with the 2-hour programme guide |
| `Player.kt` | Live player (zapping, info overlay), shared player factory and video surface |
| `Vod.kt` | Movies/series grid, search, continue watching, series detail, VOD player |
| `Splash.kt` | Animated splash and brand colours |
| `Text.kt` | Search key and episode title cleanup |

## Conventions

- **Keep it small:** no new dependencies without a strong reason; prefer platform/stdlib (`HttpURLConnection`, `org.json`, `SharedPreferences`).
- Deliberate shortcuts are marked with a `// ponytail:` comment naming the limit and the upgrade path.
- D-pad focus is the main source of bugs: avoid experimental focus APIs (`focusRestorer` broke grid navigation); restore focus explicitly with a `FocusRequester`.
- Overlays (player, detail) that sit on top of focusable content must block focus underneath (`blockFocus`) or consume D-pad keys.
- Never put credentials, provider URLs or IPs into the repository.

## Brand

Brand assets and rules live in `branding/` (see `branding/BRAND.md`). Colours in code come from `Splash.kt`.

## Git

- Commit author email: the GitHub noreply address (set in the local git config) – never a work address.
- Commit messages: short English summary line, optional body.
