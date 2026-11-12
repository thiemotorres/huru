# Contributing to Huru

Thanks for helping! Huru is a small, focused app – these rules keep it that way.

## Before you start

- **Open an issue first** for anything bigger than a small fix, so we can agree on the approach before you invest time.
- Huru is a **player only**. Contributions that add content sources, provider lists, playlists, scraping or anything that points to specific streams will not be accepted.
- Never include real provider names, URLs, credentials, channel logos or copyrighted artwork – not in code, tests, issues or screenshots. Use the fictional mock data in `app/src/test`.

## Rules

1. **English everywhere:** code, comments, commit messages, issues and PRs.
2. **Localized UI:** no hard-coded user-visible text. Add new strings to `res/values/strings.xml` (English) and to all translations in `res/values-<lang>/`. **New translations are very welcome** – copy `values/strings.xml` to `values-<lang>/strings.xml` and translate it.
3. **Small, focused PRs:** one change per PR, with a short description of what and why.
4. **No new dependencies** without discussing it in an issue first. Prefer the platform and the standard library.
5. **Built for the remote:** every feature must be fully usable with a D-pad. Test focus handling – it is the most common source of bugs.
6. **Tests:** pure logic (parsing, text helpers) gets a small JUnit test in `app/src/test`.
7. **UI changes:** update the mockups (`./gradlew recordPaparazziDebug`, then copy the changed images from `app/src/test/snapshots/images/` to `docs/screenshots/`).
8. Deliberate shortcuts are marked with a `// ponytail:` comment that names the limit and the upgrade path.

## Checks

```sh
./gradlew testDebugUnitTest assembleRelease
```

CI runs the same on every pull request.

## License of contributions

Huru is source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). The maintainer also offers commercial licenses.

**By submitting a contribution you agree that it is licensed under the PolyForm Noncommercial License 1.0.0, and you grant the maintainer (Thiemo Ferreira Torres) a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license to use, modify and relicense your contribution under any terms, including commercial ones.** You confirm that you have the right to grant this.

The name and logo "Huru" are not part of the license (see `branding/BRAND.md`).
