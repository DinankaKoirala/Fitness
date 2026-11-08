# Phase 7 — gym log

Bundled 876 exercise names, primary muscle groups and equipment from
https://github.com/yuhonas/free-exercise-db after checking its Unlicense. A source
SHA-256 and count are retained in assets/exercises/NOTICE.txt; the license is in
licenses/free-exercise-db-LICENSE.txt. No exercise images/instructions or runtime
network requests are needed. Library rows use stable negative IDs; custom rows
use normal generated IDs in the same Exercise table and have custom=true.

Routines support named, ordered exercise lists. Starting a workout snapshots its
name and exercise membership so later routine edits do not rewrite the session.
Free workouts, adding library/custom exercises, logging reps×kg, previous-session
values, weight/reps/volume PRs, set corrections/deletion, finishing and history
review are available. No invented prior-session values or exercise targets are
seeded. Unweighted sets use 0 kg and can have repetition PRs.

Elapsed and rest timers use persisted workout timestamps/deadlines, with UI ticks
only while visible. Rest duration is editable per new set (90 seconds initially,
0 disables rest); deadlines survive recreation/process restart. Timers do not
hold a background wakelock or initiate network traffic. Workout data remains
editable after finishing. Schema v5 adds ordered routine/workout memberships and
session name/rest fields, with an explicit v4→v5 migration and legacy membership
backfill. No new runtime library was added.

Verification passed: debug build, lint (0 errors), 26 JVM tests and 16 Android
35 device tests. The gym checks cover idempotent library seeding, custom rows,
routine snapshots, set editing, rest persistence, database reopening, PR rules,
and a complete UI routine→workout→finish→review flow with activity recreation.
Migration from schema v1 through v5 and all earlier workflows pass. Physical workout use remains part of release
acceptance. This phase does not implement trends or food lookups.
