# Cadence

A private, local-only gym tracker for Android. Everything lives on your phone: no account, no server, no cloud.

## Download

**[Download Cadence.apk](https://github.com/olliestrain/cadence/releases/latest/download/Cadence.apk)**
(or see the [Releases](https://github.com/olliestrain/cadence/releases/latest) page).
Open the file on your phone and allow "install from this source" when Android asks.

## What it does

- Calendar with independent days (workout / rest / note), move a workout to another day, month summary.
- Workout plans with exercises, sets, rep ranges and weights.
- Logging with last-time reference, per-set values, notes for next time, swap-for-today, rest timer.
- Progress charts per exercise (max load, volume, estimated 1RM, average reps), history, consistency streaks, milestones.
- English and Italian.
- Backups: autosave of the active workout, rolling local snapshots, JSON export/import, reminder when overdue.

The only network use is an optional, passive check of this repo's latest release to announce new versions.

## Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease     # app\build\outputs\apk\release\app-release.apk
```

Signing uses `cadence-release.jks` + `keystore.properties` (git-ignored, not in this repo). Keep them safe: updates
only install over an existing install when signed with the same key.

## Publishing a release

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts` and build.
2. `gh release create vX.Y.Z "app/build/outputs/apk/release/app-release.apk#Cadence.apk" --title "Cadence X.Y.Z"`
   (the asset must be named `Cadence.apk` so the download link above keeps working).
