# Phase 2 — manual food log

Implemented the 7-day date strip, month/date picker, daily calorie and macro
summary, all eight micronutrients, four meal sections, entry add/edit/delete,
manual food creation/editing, saved/recent food selection and local persistence.

Meal entries snapshot their food and serving values. Editing saved food affects
future logs; editing logged nutrition affects only that entry. All entries and
estimated values remain editable. Over-goal bars clamp at full. Macro bars show
estimated energy toward the daily calorie goal (4/9/4 kcal/g), not arbitrary
macro targets. Missing micros remain unknown; totals with missing contributions
are marked partial. g/ml must match the saved unit, or users can log saved
servings. No density conversion is guessed.

Schema v2 adds nullable snapshot columns and backfills existing meals from their
referenced food during an explicit, non-destructive v1→v2 migration. Schema v1
is retained for migration tests. No new runtime dependencies.

Verification passed: debug build, lint (0 errors, 27 dependency update warnings),
10 JVM tests and 7 Android 35 emulator tests. Coverage includes all-day logging,
amount edits, deletion, recent foods, date selection/recreation, profile workflows,
snapshot preservation, unknown/partial micros, scaling, over-goal clamping and
non-destructive v1→v2 migration. Reports are in app/build/reports; APK:
app/build/outputs/apk/debug/app-debug.apk. Physical-device tests remain pending.

No blocking questions remain. The user authorized routine implementation choices
and continuation through the remaining phases after each phase is verified.
