# Phase 4 — background location

Software implementation uses an explicit opt-in location foreground service,
persistent notification with Stop action, and a separate boot/package-replacement
resume path. Permission order is foreground precise location, an explanation and
separate background-location settings flow, notifications, then activity
recognition. Declining background access preserves all other features and allows
recording started from the visible app; boot recovery then requires reopening
Activity. Battery settings and manufacturer guidance are available on first use.

GPS uses Android LocationManager's GPS provider only. Activity recognition and
low-power significant-motion signals stop/resume GPS. A 1 Hz batched acceleration
fallback is used only when recognition and significant-motion support are absent.
Still means no GPS request; uncertain starts have a bounded warm-up, and GPS
pauses after two minutes without movement. Walking uses 20 s/8 m, vehicles 5 s/15 m,
and turns 2 s/5 m. Battery saver slows intervals. No app-initiated background
network request, per-point wakelock or polling alarm is used.

Points are filtered for invalid coordinates, accuracy over 50 m, out-of-order
timestamps, implausible reported speeds and impossible jumps accounting for
accuracy. Writes are grouped by 20 points or 60 seconds, serialized and committed
off the main thread. Stop flushes the remaining batch. Permission revocation,
GPS-off state, unavailable motion recognition and storage errors are surfaced.
Missing Google Play services use the local fallback instead of preventing use.

New dependency: Play services Location supplies on-device Activity Recognition;
GPS itself deliberately does not use fused/network positioning. No schema change.

Software verification passed: build, lint with 0 errors, 17 JVM tests and 10
Android 35 emulator tests, including an actual location foreground-service
start/stop, batched persistence, half-open ranges, noise rejection, sampling
policy and prior workflows. Lint has 33 warnings (dependency upgrades, KTX
suggestions and a false-positive Intent identity warning).

Hardware acceptance is **not verified**:
full-day battery percentage, 24/7 field reliability, and Xiaomi-specific boot /
battery behavior require a real device and elapsed time. They remain required
release checks in Phase 11; no measured battery figure is claimed.

Sources: Android foreground-service types and background-start restrictions,
background-location permission documentation, and Google's ActivityRecognitionClient
API documentation. Thresholds and sampling intervals are implementation choices
subject to the documented physical battery and accuracy tests.
