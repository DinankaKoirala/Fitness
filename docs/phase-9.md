# Phase 9 — trends

Profile opens a Trends view with the same extracted Day/Week/Custom picker used
by Activity. Activity retains its from-to time slider; daily trends use whole
local calendar days, respecting DST. Compose Canvas supplies charts without a
charting dependency: calories versus current goal, macro grams, weight, steps,
stacked walked/vehicle distance, per-exercise volume and PR counts, plus all
eight weekly micronutrient averages. Tap charts to inspect a date; the latest
recorded values are also shown as text. Empty, one-point and 180-day datasets
render without invalid coordinates.

Missing records remain gaps, not invented zeroes. Known zeroes still count as
observations. Weekly micronutrient averages include only logged days with complete
values for the selected nutrient; the complete/logged day count is shown. Goal
comparison explicitly uses the current goal, not fabricated historical settings.
Route classifications retain their estimated/corrected semantics. PR comparisons
include prior history before the selected range.

Profile saves now atomically log initial/changed weight into the existing
WeightEntry table. Other profile edits do not create duplicate weights. Historical
weight entries can also be added from Trends without changing today's profile or
calorie goal. No schema change or new dependency.

Verification passed: build, lint with 0 errors, 31 JVM tests and 22 Android 35
emulator tests. New tests cover complete-day weekly averages, chart coordinate
edge cases, actual Canvas rendering for empty/single/multi-month data, and daily
range queries. Existing profile, food, gym, migration, map and service checks pass.
Long-term physical-device performance and real-provider acceptance remain in the
release validation list.
