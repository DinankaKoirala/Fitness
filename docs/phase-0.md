# Phase 0 — project skeleton

Scope: Phase 0 of `/home/loq/Downloads/fitness-app-plan.md` only.

- [x] Gradle Kotlin DSL, pinned version catalog, wrapper, one app module, six feature/core/data packages.
- [x] Dark Material 3 theme with all eight specified colors and a dark launch background.
- [x] Four empty screens with bottom navigation and saved tab selection.
- [x] All 11 Room entities, timestamps, indexes, foreign keys, schema v1 export, explicit migration policy.

Confirmed by the user: minSdk 26, temporary `com.example.fitness` application ID,
metric units, UTC timestamps with local calendar dates, ordinary Room foreign keys.

## Verification

JDK 17, SDK/target API 35, Android 15 / API 35 x86_64 emulator.

```sh
./gradlew assembleDebug lintDebug connectedDebugAndroidTest
```

- Debug APK builds; lint has zero errors. The 21 warnings only report newer
  dependency versions; versions remain deliberately pinned to a compatible set.
- Both instrumentation tests pass with no failures, errors or skipped tests.
- Navigation: every tab selects and displays its empty screen; Gym stays selected
  after activity recreation.
- Database: all 11 tables create; foreign-key enforcement is enabled; the location
  timestamp index exists; a test row persists after closing and reopening.
- Cold launch succeeds. The application's own `fitness.db`, WAL and shared-memory
  files are created without entering a feature workflow.
- Merged manifest contains no network, location, activity-recognition, sensor or
  notification permissions. AndroidX includes its internal Room invalidation
  service and profile-install receiver; there is no tracking service.
- `git diff --check` passes.

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Test reports are
in `app/build/reports/androidTests/connected/debug/` and lint reports in
`app/build/reports/`. Build artifacts and the local `.toolchain/` are ignored.
Physical-device testing and runtime API 26 testing have not been performed.

[Food screen screenshot](phase-0-food.png)

## Migration strategy

Versioned Room schema JSON is committed. Future schema changes increment the
version, register explicit migrations, and validate them against retained schema
history and representative existing data. No destructive migration fallback.
There is no previous schema to migrate for version 1.

## Open questions for later phases

No blocking Phase 0 questions remain.

- Final application ID before distribution?
- Phase 1: sex and activity-level options?
- Phase 2: should meal entries snapshot nutrition to preserve history when foods
  are edited?
- Phase 7: how should routines store exercise ordering and target sets/reps?

The linked design artifact was inaccessible; Phase 0 uses the written palette
and four-tab layout. There are no mock fitness numbers or sample records.

Reasoning preference recorded: medium for ordinary phases, high for Phases 4
and 5. Phase 1 and all later phases remain unstarted. Await the user's next
instruction before proceeding.
