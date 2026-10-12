# Daybreak

An alarm clock app for Android.

- **Clock**: analog and digital time with seconds, live weather (GPS and saved cities), and world clocks
- **Alarms**: repeat days, labels, custom sounds (any audio file on your phone), gradually increasing volume, vibration, snooze
- **Stopwatch** with laps, and a **Timer** with its own sound
- **Bedtime reminder** with an optional matching wake-up alarm
- Four styles (Stock, Colors, Retro, Clock radio), each in dark and light

## Getting the APK

Every push to `main` builds the app on GitHub Actions. Open the **Actions** tab, pick the latest run, and download **Daybreak-apk** under Artifacts.

## Windows

The `desktop/` folder holds Daybreak for Windows. Every change there builds an installer; get **Daybreak-Setup.exe** from the **Releases** page (the "Daybreak for Windows" release). It runs in the system tray so alarms ring while the window is closed, and can start with Windows (Settings → Windows).

To build it yourself on Windows with JDK 17: `gradle -p desktop packageExe`.

## Building locally

Requires JDK 17 and the Android SDK (API 35):

```
gradle assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Weather data from [Open-Meteo](https://open-meteo.com). Fonts: Figtree, Fredoka, Bebas Neue, Space Mono and Archivo Narrow (SIL Open Font License).
