Room schema history
===================

KSP exports versioned JSON snapshots here; commit them with every schema change.
Version 1 is the initial schema, so there is no migration from an earlier version.

For a future schema change:
1. Increment FitnessDatabase.version.
2. Supply an explicit Migration(from, to) to the builder using addMigrations.
3. Keep every prior JSON snapshot and commit the newly generated snapshot.
4. Validate the migration using prior exported snapshots and populated records,
   then open with Room to validate the resulting schema. Use MigrationTestHelper
   when adding separate migration fixtures.

Never use fallbackToDestructiveMigration. Missing migration paths must fail rather
than silently erase data. Updates must explicitly set updatedAt; constructor
values apply only when a record is first constructed.

Dates are device-local ISO dates; event timestamps are UTC epoch milliseconds.
Queries over event times should use half-open ranges [from, to).
Food micros are nullable so unknown values aren't treated as measured zero.
Schema history: v2 adds immutable meal nutrition snapshots; v3 sensor baseline and
estimated day splits; v4 step intervals and editable segment labels; v5 ordered
routine/workout exercise memberships; v6 queued AI drafts; v7 restore checkpoint.
The Android regression suite builds a populated v1 database from its exported
snapshot and opens it through the complete explicit migration chain, verifying
preserved records and the current Room schema. Keep all snapshots 1–7.
