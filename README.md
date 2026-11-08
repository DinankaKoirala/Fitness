# Fitness

Android-only, offline-first fitness tracker. **Phase 0 only**: a dark Material 3
shell with Food, Activity, Gym and Profile tabs, plus the initial Room database.
Screens are deliberately empty except for their titles. No later-phase features
or libraries are included. No network permission, tracking services, analytics, backend,
or cloud backup. Device backup is disabled in the manifest.

## Build and run

Use JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0. Open this directory
in Android Studio, set your SDK location in `local.properties`, and run `app`.
Alternatively, set `ANDROID_HOME` and run:

```sh
./gradlew assembleDebug lintDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

The second command requires a connected device or running emulator (API 26+).
The APK is `app/build/outputs/apk/debug/app-debug.apk`. Gradle downloads the
pinned build dependencies on the first build; the app itself uses no network.

## Confirmed choices

- Minimum API 26; compile/target API 35; temporary ID `com.example.fitness`.
- One `app` module; packages: `food`, `activity`, `gym`, `profile`, `data`, `core`.
- Measurements use kg, cm, metres, m/s. Dates are local ISO calendar dates;
  event timestamps are UTC epoch milliseconds. Records have creation timestamps;
  mutable records also have update timestamps.
- Room schema version 1 contains all 11 entities from the supplied plan. Meal and
  workout relationships use foreign keys and indexes. Unknown micros are null.
- Database creation runs off the main thread on application launch. Tab selection
  survives activity recreation. No sample fitness values are displayed or seeded.

## Dependencies

Compose UI / Material 3 provide the UI and theme; Activity Compose hosts it.
Room runtime stores local records; KSP plus Room compiler generate and validate
its schema. Compose BOM aligns UI artifacts. AndroidX Test and Compose UI Test
are test-only dependencies for navigation, recreation and persistent DB checks.
No navigation, DI, icon, networking or chart library is needed for four tabs.

Schema exports and the migration policy live in [app/schemas](app/schemas/README.md).
Future schema versions must use tested, explicit migrations; destructive fallback
is prohibited. Feature DAOs will be added with the workflows that need them.

## Scope and open questions

Phase 1 and beyond are not started. Before their respective phases:

- Choose final application ID before distribution.
- Define routine exercise ordering / targets in Phase 7.
- Decide whether food logs snapshot nutrition to preserve historical totals after
  editing a saved food in Phase 2.
- Confirm sex / activity-level options during Phase 1; these are plain strings in
  the initial schema rather than preemptively fixing onboarding choices.

Reasoning preference: medium for ordinary phases; high for Phase 4 (background
location) and Phase 5 (classification). Each phase requires a separate instruction
to begin.

Navigation icons are bundled Material Design vector paths (Google, Apache 2.0);
see [their license](licenses/material-icons-LICENSE.txt). The launcher icon is a
simple project-local vector.

The local verification toolchain remains in `.toolchain/` (not committed). To
reuse it in this workspace:

```sh
JAVA_HOME="$PWD/.toolchain/jdk" ANDROID_HOME="$PWD/.toolchain/android" \
GRADLE_USER_HOME="$PWD/.toolchain/gradle" ./gradlew assembleDebug
```

See [Phase 0 results and open questions](docs/phase-0.md).
