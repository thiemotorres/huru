<p align="center"><img src="branding/lockup.svg" alt="Huru" width="360"></p>

**Huru** ist eine schlichte, werbefreie IPTV-App für Amazon Fire TV (Android TV). Sie ist ein reiner Player für **eigene** Xtream-Codes-Zugänge – ohne Werbung, ohne Tracking, ohne Ballast. *Huru* heißt auf Swahili „frei“.

> **Hinweis:** Huru liefert keine Sender, Filme oder Serien und verweist auf keine Anbieter. Du brauchst einen eigenen, rechtmäßigen Zugang.

## Funktionen

- **Live-TV** mit Programmführer (EPG) direkt in der Senderliste, Favoriten und ausblendbaren Kategorien
- **Schnelles Zappen:** Hoch/Runter im Player, Start direkt mit dem zuletzt gesehenen Sender
- **Filme & Serien** als Poster-Raster mit Suche, „Weiterschauen“ und automatisch nächster Folge
- **Für die Fernbedienung gebaut:** alles per D-Pad, langes OK für Favoriten bzw. Ausblenden

## Bauen & installieren

Voraussetzungen: JDK 17, Android SDK (Plattform 35).

```sh
./gradlew assembleRelease
adb connect <IP-des-Fire-TV>:5555          # ADB-Debugging in den Entwickleroptionen einschalten
adb install -r app/build/outputs/apk/release/app-release.apk
```

Das Release-APK ist zum Sideloaden mit dem Debug-Schlüssel signiert.

## Technik

Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Coil. Keine weiteren Abhängigkeiten, kein Backend.

## Marke

Logo, Farben und Regeln: [`branding/BRAND.md`](branding/BRAND.md). Schrift im Logo: Figtree (SIL OFL 1.1).
