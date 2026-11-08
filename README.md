# HiFitli

Android-only, offline-first fitness tracker built with Kotlin and Jetpack Compose.
The software implementation covers Phases 0–11 of the supplied plan. Physical
battery/OEM acceptance and account-keyed integrations remain explicitly unverified;
see [verification and release checks](docs/phase-11.md).

## Use the app

Install [the debug APK](app/build/outputs/apk/debug/app-debug.apk) on Android 8+
and complete profile onboarding, or restore an existing backup from onboarding’s
Settings button. Measurements are metric. The four tabs provide:

- **Food:** daily calorie/macro/micronutrient totals, editable meal logs, saved
  foods, offline Swiss search, barcode/label scanning, optional remote food
  databases and Gemini text/photo estimates. Logged meals preserve their original
  nutrition when saved foods change. Unknown micros remain unknown.
- **Activity:** opt-in steps and GPS recording, manual step correction, editable
  motion classifications, Day/Week/Custom statistics and street maps. Satellite
  and elevation-based terrain require a MapTiler key. Sensor availability varies.
- **Gym:** 876 bundled exercises, custom exercises, ordered routines, sessions,
  rest timers, sets, previous sessions, PRs and workout history.
- **Profile:** editable measurements and calorie goals, inspectable trend charts,
  weight history and **Settings, backup and export**.

Settings holds encrypted provider keys, lookup mode, GPS battery saver, licenses,
full JSON backup/restore and date-range CSV/GPX exports. File operations use the
Android file picker without storage permissions. JSON backups are plaintext
personal data; choose the destination accordingly. Restore validates before
replacement, excludes keys/permissions and leaves tracking off.

Enable tracking from Activity and follow the permission/battery guidance. It is
optional; manual logging works without tracking permissions. Recording uses
persistent notifications with Stop actions. Android and OEM settings can stop
services; real-device validation is still required.

## Privacy and estimates

No account, backend, analytics or cloud sync. Automatic Android backup is disabled.
All app HTTP passes through a foreground gate and is cancelled when the app stops
being visible. Map tiles and explicit provider searches use the internet. AI
requests queue locally and retry only through an explicit foreground action on
Wi-Fi/charging. Free-tier Gemini content may be used by Google; the UI discloses
this before use. Keys use Android Keystore encryption and are excluded from
exports, saved UI state and authenticated response caches.

Calories, automatic motion labels, OCR and AI nutrition are estimates and editable.
Offline OCR uses bundled Tesseract and barcode decoding uses ZXing. The planned
ML Kit integration was replaced because its documented analytics conflict with
the plan's privacy requirement. Optional Health Connect import is deferred.

## Build and verify

Use JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Set `ANDROID_HOME` or
`local.properties`, then run:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

In this workspace `./scripts/android-build` selects the installed ignored local
toolchain. The second command requires a running emulator or connected device.
Dependencies download at build time. Debug APK output is
`app/build/outputs/apk/debug/app-debug.apk`; this is a development build, not a
signed store release. Temporary app ID: `com.example.fitness`; min API 26,
compile/target API 35.

Room schema 7 has explicit migrations from schema 1; no destructive fallback.
Events use UTC epoch milliseconds, calendar records ISO local dates. SQLite
transactions preserve fitness data; a durable restore journal coordinates database,
preferences and queued images after interruption.

## Implementation and dependencies

[Implementation status](docs/implementation-status.md) links phase evidence.
[Phase 10](docs/phase-10.md) describes backup format and recovery.
[Phase 11](docs/phase-11.md) records final checks and physical test procedures.
[Schema policy](app/schemas/README.md) covers migrations.

Runtime libraries: AndroidX Compose/Material, Room, DataStore, Lifecycle, coroutines,
Play services Activity Recognition, MapLibre Native and bundled MapLibre GL JS for
terrain, OkHttp, CameraX, Tesseract4Android and ZXing. Charts use Compose Canvas;
no chart, analytics or background network scheduler library. Libraries and bundled
data have notices in [licenses](licenses), app assets and the Settings viewer.
Swiss data: FSVO Swiss Food Composition Database v7.1; exercises:
yuhonas/free-exercise-db (Unlicense).

Remaining decisions before distribution: final application ID/signing, user API
keys/provider billing choices, physical Xiaomi acceptance and measured full-day
battery usage. No remote repository push or publication is performed.

## GitHub APK distribution

Every push to `main` (or manual **Actions → Build and publish HiFitli APK → Run
workflow**) builds, unit-tests and lints the app, then publishes:

- **GitHub Packages:** Maven artifact
  `io.github.dinankakoirala:hifitli:0.1.0-build.<run>.<attempt>@apk`, linked to this
  repository. GitHub Packages downloads require GitHub authentication.
- **Releases:** a development prerelease with the directly downloadable
  `HiFitli.apk` and its SHA-256 checksum.
- **Actions artifacts:** a retained copy of the APK and checksum.

Find downloads at [Releases](https://github.com/DinankaKoirala/Fitness/releases)
and packages on the repository’s GitHub page. The workflow uses GitHub’s own
short-lived `GITHUB_TOKEN` with package/content write permissions; no personal
access token is committed or required as a repository secret. Repository/organization
policy must allow GitHub Actions publishing.

Builds are debug signed. GitHub runners can generate different debug keys, so
Android may require uninstalling an earlier build. Export a JSON backup first;
restore after reinstalling. A stable production signing key remains a separate
release decision. The display name is HiFitli; the existing application ID is
preserved for local installation/data compatibility.

To verify APK publication locally without contacting GitHub:

```sh
./gradlew :app:publishHiFitliApkPublicationToDistributionCheckRepository
```

The APK and POM are written under `app/build/distribution-check/`. Publishing
configuration follows [GitHub’s Gradle registry documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry).
