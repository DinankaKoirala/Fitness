# Phase 1 — profile and goals

Scope: Phase 1 of the supplied fitness-app plan. Phase 2 and later are unstarted.

- [x] First-run onboarding for height (cm), weight (kg), age, equation sex input,
  and activity level; these remain editable in Profile.
- [x] Estimated maintenance goal from Mifflin–St Jeor × activity factor, rounded
  once to whole kcal. No automatic weight-loss or weight-gain adjustment.
- [x] Manual goal override in Preferences DataStore; editable or removable to
  restore the estimate. Changing measurements preserves an active override.
- [x] The active daily goal appears in the Food header, labeled estimated/manual.

## Choices and storage

The user authorized routine decisions without further confirmation. Equation
inputs are Male (+5) / Female (−161), explicitly presented as formula inputs.
Activity factors: sedentary 1.2, light 1.375, moderate 1.55, very active 1.725,
extra active 1.9. These are selectable estimation assumptions, not measured
activity or factors derived from the original resting-energy paper.

Room's existing singleton UserProfile row stores measurements and the calculated
calorie goal. Its presence completes onboarding. DataStore stores only the
independent manual override; the repository resolves the effective goal for both
screens. Separate actions save the profile and override, avoiding a two-store
transaction. Existing creation timestamps and step goals are preserved; a new
profile's step goal is zero (unset), with no step workflow added.

No Room schema change or migration is required: only a DAO was added. No keys,
network permissions, background services, onboarding defaults or production
sample profiles were added. Input drafts survive activity recreation. The form
validates finite positive measurements, positive integer age, usable calculated
results, and positive integer manual kcal. Decimal commas are accepted for
height and weight. Read errors show a retry action; write errors retain entries
and never claim a successful save.

New libraries are limited to DataStore Preferences, Lifecycle Runtime Compose,
an explicit Coroutines Android dependency, and test-only JUnit. Their purpose
and pinned versions are documented in the data-task commit.

## Verification

JDK 17, SDK 35, Android 15 / API 35 x86_64 emulator:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
```

- Debug build succeeds; lint has 0 errors and 27 dependency-version warnings.
- Five JVM tests pass: both equation inputs, all factors, final rounding,
  decimal-comma parsing, invalid/nonfinite/overflow inputs and manual validation.
- Four device tests pass: full onboarding/override/edit/restore/navigation flow,
  profile and settings repository reload, DataStore disk close/reopen, and the
  retained schema/persistence check from Phase 0.
- Form drafts and selected navigation tab survive activity recreation.
- A manual override survives activity relaunch and profile edits. Clearing it
  restores the newly calculated goal.
- Room schema v1 export is unchanged; no destructive migration fallback.
- `git diff --check` passes.

Reports: `app/build/reports/tests/testDebugUnitTest/`,
`app/build/reports/androidTests/connected/debug/`, and
`app/build/reports/lint-results-debug.html`. APK:
`app/build/outputs/apk/debug/app-debug.apk`.

Physical-device and API 26 runtime verification remain unperformed.

[First-run onboarding](phase-1-onboarding.png) · [Food goal](phase-1-food.png)

Screenshots use emulator test measurements, not user profile data. Production
onboarding starts blank. The Food screenshot was also checked after an actual
force-stop/process relaunch; demo data was then cleared from the task emulator.

## Sources

- [Original Mifflin–St Jeor paper](https://pubmed.ncbi.nlm.nih.gov/2305711/):
  10 × kg + 6.25 × cm − 5 × age + sex adjustment, then activity factor.
- [Android DataStore guide](https://developer.android.com/topic/libraries/architecture/datastore)
  and [release history](https://developer.android.com/jetpack/androidx/releases/datastore).

## Open questions

No blocking Phase 1 questions remain. Deferred decisions: final application ID
before distribution; food-log nutrition snapshots in Phase 2; routine ordering
and targets in Phase 7. Routine choices may be resolved within the authorized
phase without asking, per the user's updated instruction.

Reasoning preference remains medium generally and high for Phases 4 and 5.
Stop here and wait for the user's next message before Phase 2.
