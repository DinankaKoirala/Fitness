# Phase 11 — settings and hardening

## Delivered software

Profile → Settings, backup and export consolidates encrypted USDA/Gemini/MapTiler
keys, Gemini model, lookup mode, tracking battery saver and JSON/CSV/GPX operations.
Activity retains tracking controls and permission/OEM guidance. Settings includes
privacy/estimate explanations, source acknowledgements and bundled license texts.
No new runtime dependency was added.

Hardening covers safe stop on service/storage errors, a Stop action on the step
notification, atomic sensor-baseline reset consumption, independent boot attempts
for steps/routes, stale-date step display protection, and stopping a stale tracking
preference even when no sensor exists. Restore waits for profile/preferences to
refresh before showing the normal UI. A staged restore rejected before its journal
is created now releases its temporary files.

Automated checks include the existing BMR, baseline/reboot, classification, range,
chart, gym, food and provider/privacy suites; fresh-database JSON round trips;
queued-image restoration; interruption recovery after commit; rollback after an
injected SQLiteFullException; malformed backup rejection; CSV safety and GPX range
checks; and settings persistence/export accessibility. The disk-full test injects
the failure into the transaction: it does not claim to fill a physical disk.

Final test totals and emulator edge-case results are recorded in
implementation-status.md. The additional offline run passed manual food logging,
bundled Swiss search/confirmation and gym workflows with emulator airplane mode
on and device location disabled. A cold-start synchronization race exposed by
running these tests alone was fixed; startup no longer waits for a profile value
that onboarding can change. Restore-specific profile refresh remains guarded.
Connectivity and location were restored after these checks. ARM64 native ELF
LOAD segments all report 16 KiB alignment; physical 16 KiB device validation is
not claimed. Lint dependency-update/KTX suggestions
are warnings; no lint error is suppressed with a new baseline.

## Physical acceptance — pending, not measured

There is an Android 35 emulator, no physical Xiaomi device, user provider keys or
elapsed full-day field run. These plan acceptance criteria cannot be claimed as
passed. No battery percentage is invented. Do the following before distribution:

1. Install on a Xiaomi phone, record model/Android/MIUI or HyperOS versions and
   battery health. Complete Activity's foreground/background location, notification
   and recognition permissions. Enable OEM autostart and remove battery restriction
   using the guidance. Confirm both persistent tracking notifications.
2. Fully charge, reset/record Android battery stats and note the starting time and
   charge. Use normally for 24 hours, including still, walk, vehicle, indoor GPS
   loss, screen-off periods and a reboot. Keep map/AI use separate from tracking
   measurement. Do not leave USB charging attached for the battery measurement.
3. Record the app's Android battery share, absolute charge loss, tracking duration,
   missing intervals, distance/steps versus a known route and whether the service
   resumes after reboot. Report the actual number against the plan's target of
   a few percent daily. Repeat battery-saver on/off on comparable days.
4. Revoke location/activity permissions during recording, disable GPS, use airplane
   mode, deny notifications and exercise low storage. Reopen Activity and verify
   clear error state, retained committed data and explicit recovery. Grant access
   and restart; confirm gaps remain gaps rather than invented route distance.
5. Remove the app from recents, lock overnight and test Xiaomi battery management.
   Verify recording persistence and document any required OEM settings. A force
   stop intentionally prevents background restart until the user opens the app.
6. With user keys, verify USDA, Gemini text/photo, satellite and terrain on the
   actual account. Inspect provider quota/billing. Confirm estimates before logging.
   Test barcode/labels under varied physical lighting and cameras. Confirm GPS
   altitude is not used for terrain.
7. Export a real full backup, install on a separate fresh device and restore it.
   Compare logs, charts, routines, queued drafts and counts; keys should remain
   unset on the fresh device and recording off. Test picking files through the
   device's document provider. Automated fresh-database equivalence already passes;
   a cross-device file-picker run remains a manual check.

## Scope limits and decisions

Optional Health Connect import is deferred. Current JSON format 1 accepts matching
schema 7 only; unsupported backup versions are rejected without replacing data.
Map terrain uses bundled GL JS because native MapLibre terrain is unsupported.
ML Kit is replaced with offline Tesseract/ZXing to avoid documented SDK analytics.
AI retries are foreground only to honor the plan's no-background-network rule.
The app uses metric units and a temporary application ID, with a debug signing
certificate. Publishing, final identity/signing and store policy review are outside
this implementation. No external push or deployment is performed.
