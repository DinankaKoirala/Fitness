# Phase 3 — steps

Step-counter support uses an opt-in health foreground service and a persistent
low-importance notification. It registers the hardware step-counter sensor with
30-second batching, writes baseline and daily totals in one Room transaction,
and unregisters when stopped. No GPS, wakelock or network is used. Missing sensors
fall back to manual daily totals. Notification and activity-recognition requests
are initiated by the user. The boot/package-replacement receiver resumes only
previously enabled tracking with the required permission.

Schema v3 adds the persistent cumulative baseline and a daily estimated flag,
with explicit v2→v3 migration. A first reading establishes the baseline; explicit
stop/start skips steps from the disabled period. Process restarts retain the
baseline; reboot/driver resets never subtract from an existing day. Sensor event
timestamps, rather than batch delivery times, determine the local date. A gap
across midnight cannot be split exactly from cumulative counters, so the affected
day is labeled estimated and can be corrected. Corrections retain future deltas.

Optional Health Connect import is deferred; sensor/manual support has no external
fitness dependency. Real sensor accuracy, OEM restart behavior and full-day
battery use require physical-device verification in Phase 11. Emulator tests
simulate the counter readings and boot IDs; they do not prove physical tracking.

Verification passed: build, lint with zero errors, 14 JVM tests and 8 Android
35 device tests. Counter tests cover restart, reboot/reset, midnight estimation,
manual corrections and real database reopening. Existing profile, food and
v1→v3 migration checks pass. No new runtime library was added.
Sources: Android's Sensor Manager step-count guide and foreground-service health
type documentation (developer.android.com).
