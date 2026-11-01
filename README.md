# Rowan

Local-first gym tracker for Android, with an optional home-server for accounts and a leaderboard.

## Download the app

**[Download the latest APK](https://github.com/olliestrain/rowan/releases/latest/download/Rowan.apk)**
(or open the [Releases](https://github.com/olliestrain/rowan/releases/latest) page). On the phone: open the downloaded
file and allow "install from this source" when Android asks. Updates are announced inside the app.

```
app/      Android app (Kotlin, Jetpack Compose, Room)
server/   Home server for accounts, leaderboard and backup (Node, see server/README.md)
```

## Build the APK (Windows)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease     # app\build\outputs\apk\release\app-release.apk  (signed)
```

The release APK is signed with `rowan-release.jks` (password in `keystore.properties`, both git-ignored and NOT in
this repo). **Back both up.** Android only installs an update over an existing install when it is signed with the
same key.

## Publishing a release (what the in-app updater looks at)

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`, build the release APK.
2. `gh release create v0.2.0 app/build/outputs/apk/release/app-release.apk#Rowan.apk --title "Rowan 0.2.0" --notes "..."`
   (the asset must be named `Rowan.apk` so the download link above always works).
3. Installed apps show "A new update is available" on Home and in Settings.
   The repo must be public. The repo is set in `gradle.properties` (`rowan.githubRepo`).

## Home server

See `server/README.md`. Short version: copy `server/` to the Pi (hostname `mosaic`), run `bash deploy/install.sh`,
paste the Gmail app password into `.env` after `SMTP_PASS=`, restart the service. The app finds the server at
`http://mosaic.local:8787`, falls back to scanning the local /24 for it, and the address can be set by hand in Settings.

## Data safety

- Room database in WAL mode; the active workout is autosaved ~0.5 s after every edit.
- Rolling JSON snapshots in the app's private storage after every change burst, plus on launch.
- Export / Import (JSON, via the system file picker), restore from snapshots, optional copy on the home server.
- Backup reminder notification + Home banner when the last export is older than the chosen number of days.
