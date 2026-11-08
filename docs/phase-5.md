# Phase 5 — segments and range statistics

Persisted step intervals provide time-local cadence evidence. Classification uses
speed and cadence; fast foot motion with cadence is retained as walking, while
speed without cadence suggests a vehicle. Slow traffic and missing sensors can
remain ambiguous, so every automatic label is estimated and can be corrected.
Corrections persist as time-interval labels, survive recomputation, and split
edges at their boundaries rather than changing unrelated history.

Range queries include boundary neighbours, clip/interpolate each observed edge,
and recompute distance, walked/vehicle split, moving average and maximum speed
for only the selected interval. Gaps over 3 minutes, duplicate timestamps and
invalid jumps are excluded; unobserved time is not invented as still/travel.
Average speed uses recorded moving time. Midnight boundaries are split and only
whole-calendar-day results are cached as Segment rows; partial slider queries do
not replace whole-day history. Cadence joins use an ordered sweep rather than
quadratic scans. Calendar ranges respect local timezone/DST; query bounds are
half-open UTC timestamps.

Activity provides Day / Week / Custom dates, a from-to time slider, totals and
editable segment labels. Schema v4 adds step intervals, label corrections and
segment estimate/max-speed fields through explicit migration. No new dependency.

Verification passed: debug build, lint with 0 errors, 22 JVM tests and 11 Android
35 emulator tests. New coverage includes clipped range conservation, cadence,
fast foot motion, gap handling, manual interval corrections, DST day lengths and
proof that partial queries preserve whole-day cache rows. v1→v4 migration and all
prior workflows still pass. Physical classification accuracy and the Phase 4
battery/OEM checks remain release validation work, not claimed measurements.
