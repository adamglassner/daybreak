# Larksong

<img src="desktop/icons/larksong.png" width="96" alt="Larksong icon: a lark singing in front of the rising sun">

An alarm clock for Android, Windows and Linux. (Larksong used to be called Daybreak. Moving over? Back up your alarms in the old app under Settings → Backup, then restore them in Larksong. On Windows and Linux, your settings come over by themselves.)

- **Clock**: analog and digital time with seconds, live weather (GPS and saved cities), and world clocks
- **Alarms**: repeat days, labels, custom sounds (any audio file on your phone), gradually increasing volume, vibration, snooze
- **Dismiss early**: up before your alarm? Dismiss its next ring from the alarm list, the "Upcoming alarm" notification (Android), or the tray icon (Windows/Linux)
- **Wake to the radio**: any station as an alarm sound (falls back to the alarm tone if the station can't play)
- **Sleep sounds** on the Clock page in every style: rain, ocean, fan, crickets, fireplace, white/pink/brown noise, with a timer that fades out
- **Stopwatch** with laps, and a **Timer** with its own sound
- **Bedtime reminder** with an optional matching wake-up alarm
- Four styles (Stock, Colors, Retro, Clock radio), each in dark and light

## Getting the APK

Every push to `main` builds the app on GitHub Actions. Open the **Actions** tab, pick the latest run, and download **Larksong-apk** under Artifacts.

## Windows

The `desktop/` folder holds Larksong for Windows. Every change there builds an installer; get **Larksong-Setup.exe** from the **Releases** page (the "Larksong for Windows" release). It runs in the system tray so alarms ring while the window is closed, and can start with Windows (Settings → Windows).

To build it yourself on Windows with JDK 17: `gradle -p desktop packageExe`.

## Linux

Every change to `desktop/` also builds Linux packages. Get them from the **Releases** page ("Larksong for Linux"):

- Ubuntu, Debian, Mint, Pop!_OS: `sudo apt install ./larksong_<version>_amd64.deb`
- Fedora, RHEL, openSUSE: `sudo dnf install ./larksong-<version>.x86_64.rpm`

Larksong appears in your app menu under Utilities.

## Building locally

Requires JDK 17 and the Android SDK (API 35):

```
gradle assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Weather data from [Open-Meteo](https://open-meteo.com). Fonts: Figtree, Fredoka, Bebas Neue, Space Mono and Archivo Narrow (SIL Open Font License).

## Credits

Created by Adam Glassner · The Glassnerds

Default alarm sound: "Alarm Clock" by freesound_community, from Pixabay (Pixabay Content License).
