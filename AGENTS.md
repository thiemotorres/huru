# AGENTS.md

Guidance for AI agents and contributors working on **Huru**, an ad-free IPTV player (Xtream Codes) for Amazon Fire TV.

## Language

**Everything in English:** code, comments, UI strings, docs, commit messages, PR descriptions and issues. (Conversation with the maintainer may be in German; the repository is not.)

**UI text is localized:** never hard-code user-visible text. Add it to `app/src/main/res/values/strings.xml` (English, default) **and** to every translation (`values-de/strings.xml`, …), then use `stringResource(R.string.…)`. Brand names and URLs stay literal.

## Build & test

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home   # JDK 17 required
./gradlew testDebugUnitTest      # JVM unit tests (parsing, text helpers)
./gradlew assembleRelease        # minified APK, signed with the debug key
adb install -r app/build/outputs/apk/release/app-release.apk
```

- Always build and test the **release** variant before reporting performance – debug Compose is much slower.
- Pure logic (parsing, text cleanup) gets a small JUnit test in `app/src/test`. `org.json` is available in tests as a real dependency.

## Releases & signing

- CI (`.github/workflows/ci.yml`) runs tests and a release build on every push/PR.
- Tag `vX.Y.Z` and push it → `release.yml` publishes a signed `huru-X.Y.Z.apk` as a GitHub release and keeps the Amazon build (`-Pstore=amazon`, no Ko-fi QR) as an artifact. `versionCode` = workflow run number.
- Release key: `~/.android/huru-release.jks` on the maintainer's Mac, referenced by the git-ignored `keystore.properties`; CI gets it from the `HURU_*` repository secrets. **Never commit keystores or passwords.** Losing the key means store updates are impossible – it must be backed up.
- `-Pstore=amazon` sets `BuildConfig.DONATE = false` (Amazon's rules on external payments are unclear).

## Store & screenshots

- Mockup screenshots: `Mockups.kt` (Paparazzi, fictional data) → `./gradlew recordPaparazziDebug` → copy to `docs/screenshots/`.
- Fictional artwork for mocks: `python3 tools/mock_assets.py`. Ko-fi QR drawable: `python3 tools/kofi_qr.py` (needs `segno`).
- Amazon graphics: `python3 tools/store_assets.py` → `store/amazon/`; texts and checklist in `store/amazon/listing.md`.

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
| `About.kt` | About page (version, license, Ko-fi QR outside the Amazon build) |
| `Text.kt` | Search key and episode title cleanup |

## Conventions

- **Keep it small:** no new dependencies without a strong reason; prefer platform/stdlib (`HttpURLConnection`, `org.json`, `SharedPreferences`).
- Deliberate shortcuts are marked with a `// ponytail:` comment naming the limit and the upgrade path.
- D-pad focus is the main source of bugs: avoid experimental focus APIs (`focusRestorer` broke grid navigation); restore focus explicitly with a `FocusRequester`.
- Overlays (player, detail) that sit on top of focusable content must block focus underneath (`blockFocus`) or consume D-pad keys.
- Never put credentials, provider URLs or IPs into the repository.

## Brand

Brand assets and rules live in `branding/` (see `branding/BRAND.md`). Colours in code come from `Splash.kt`.

## License

- Code: **PolyForm Noncommercial 1.0.0** (`LICENSE.md`) – *source-available*, not OSI open source. Never describe the project as "open source".
- Name and logo are excluded from the license (see `branding/BRAND.md`).
- Keep the `Required Notice:` line at the top of `LICENSE.md`.
- Outside contributions need the contributor's agreement that the maintainer may also license them commercially – this is the contribution license in `CONTRIBUTING.md`; PRs confirm it via the PR template checkbox.

## Contributing

The full rules are in `CONTRIBUTING.md` and apply to agents too. In short:

- Huru is a **player only** – never add content sources, provider lists, playlists or links to specific streams.
- No real provider names, URLs, credentials, channel logos or copyrighted artwork anywhere (code, tests, screenshots). Use the fictional mock data in `app/src/test`.
- One focused change per PR; open an issue before bigger changes; no new dependencies without discussion.
- UI changes: re-record mockups (`./gradlew recordPaparazziDebug`) and copy changed images to `docs/screenshots/`.

## Git

- Commit author email: the GitHub noreply address (set in the local git config) – never a work address.
- Commit messages: short English summary line, optional body.
