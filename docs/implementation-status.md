# Implementation status

The user authorized completing the entire supplied plan without stopping for
phase approval or routine implementation questions. Necessary local installs,
builds, emulator tests and commits are authorized. Reusable command approvals
were requested for scripts/android-build, git add/commit, adb, curl -fL and the
local emulator. Do not push or publish externally without authorization.

Work in phase order; verify each phase before starting the next. Reasoning
preference: medium generally, high for Phase 4 background location and Phase 5
classification. No background component may access the network. That rule takes
precedence over the contradictory Phase 8 suggestion of background AI retries:
queue requests locally and retry only from an explicit foreground workflow.

- Phase 0: complete; docs/phase-0.md.
- Phase 1: complete; docs/phase-1.md.
- Phase 2: complete; docs/phase-2.md.
- Phase 3: complete (sensor/manual implementation; physical validation pending); docs/phase-3.md.
- Phase 4: software verified; full-day battery and Xiaomi acceptance pending; docs/phase-4.md.
- Phase 5: software verified; docs/phase-5.md.
- Phase 6: software verified; user-key imagery and physical terrain acceptance pending; docs/phase-6.md.
- Phase 7: software verified; docs/phase-7.md.
- Phase 8: software verified; keyed provider/physical camera acceptance pending; docs/phase-8.md.
- Phase 9: software verified; docs/phase-9.md.
- Phase 10: software verified; 33 JVM tests, 24 Android 35 emulator tests, lint zero errors; docs/phase-10.md.
- Phase 11: software verified; docs/phase-11.md. Final build, 33 JVM tests,
  26 Android 35 emulator tests and lint (0 errors) passed. An additional 3-test
  offline run passed food, Swiss lookup and gym flows with airplane mode on / GPS
  disabled. Physical battery/Xiaomi, provider-key and cross-device file-picker
  acceptance remain pending; no blanket hardware acceptance is claimed.

Hardware limitations must remain explicit: this workspace has an Android 35
emulator but no physical Xiaomi phone. Full-day battery measurements require
elapsed time and real device use; do not fabricate them or claim those checks
passed. Map terrain/provider capabilities and licenses must be checked against
primary sources before choosing integrations.

Current decisions: phase 2 meal entries snapshot food names, serving definitions,
source, estimated status and nutrients to protect historical totals from saved
food edits. Unknown micronutrients remain null; partial totals are labeled.
Only matching g/ml units or saved servings are accepted; no mass/volume guessing.
Macro bars show estimated 4/9/4 energy toward the calorie goal, avoiding invented
macro targets. Schema v2 has an explicit v1→v2 migration and backfill.

ML Kit was replaced by offline Tesseract/ZXing because Google documents SDK usage
analytics without a supported opt-out, conflicting with the explicit privacy
rules. CameraX remains. This was explained in commentary and docs/phase-8.md.
