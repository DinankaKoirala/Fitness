# Phase 10 — export and import

Full JSON backup streams all business tables, including meal nutrient snapshots,
exercise/routine memberships, correction labels, timestamps, AI drafts and queued
images. User preferences are explicitly whitelisted. Encrypted provider keys,
permissions, runtime tracking flags and device caches are excluded. Restore leaves
recording off. JSON format 1 currently requires schema 7; older/future backup
schemas are rejected with existing data retained.

Restore first imports into a separate Room database, checks types, required
columns, relationships, enums, dates, nutrition bounds and image references, and
shows record counts before the user confirms replacement. Live database replacement
is transactional. A durable journal and in-transaction checkpoint recover settings
and queued media after interruption. Startup waits for recovery; failed recovery
retains the journal for retry. Stop tracking before restoring.

Food, steps, weight and gym CSV exports use the selected date range, quote fields,
neutralize spreadsheet formula text and preserve unknown nutrients as empty fields.
Food quantities are scaled from immutable logged nutrition. GPX 1.1 export uses
selected recorded points, UTC timestamps, speed/accuracy extensions and separate
track segments across gaps; it does not invent altitude. All file operations use
Android's Storage Access Framework without storage permissions.

Validation includes a populated source database → backup → fresh target database
comparison across every exported table, an injected interruption after database
commit followed by successful settings recovery, corrupt-reference/format rejection,
CSV scaling/escaping and namespace-aware GPX content, gap and range checks. JVM
checks cover CSV formula protection and bounded streaming input. Final verification
counts are recorded in implementation-status.md.

Backups are plaintext personal data. The user chooses where to save them. No cloud
sync, automatic network retries, or additional runtime dependency was introduced.
Schema 6 → 7 adds only the restore checkpoint table; migration from the original
schema remains covered by the regression suite.
