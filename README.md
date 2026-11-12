<p align="center"><img src="branding/lockup.svg" alt="Huru" width="360"></p>

**Huru** is a clean, ad-free IPTV player for Amazon Fire TV (Android TV). It plays **your own** Xtream Codes subscription – no ads, no tracking, no clutter. *Huru* means "free" in Swahili.

> **Note:** Huru does not provide any channels, movies or series and does not point to any provider. You need your own, legitimate subscription.

## Features

- **Live TV** with a programme guide (EPG) built into the channel list, favourites and hideable categories
- **Fast zapping:** Up/Down in the player, starts straight into the last watched channel
- **Movies & series** as a poster grid with search, "Continue watching" and automatic next episode
- **Built for the remote:** everything works with the D-pad; long-press OK for favourites or hiding categories

## Build & install

Requirements: JDK 17, Android SDK (platform 35).

```sh
./gradlew assembleRelease
adb connect <fire-tv-ip>:5555               # enable ADB debugging in the developer options first
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release APK is signed with the debug key for sideloading.

## Tech

Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Coil. No other dependencies, no backend.

## Brand

Logo, colours and usage rules: [`branding/BRAND.md`](branding/BRAND.md). Typeface in the logo: Figtree (SIL OFL 1.1).
