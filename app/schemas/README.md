Room schema history
===================

KSP exports versioned JSON snapshots here; commit them with every schema change.
Version 1 is the initial schema, so there is no migration from an earlier version.

For a future schema change:
1. Increment FitnessDatabase.version.
2. Supply an explicit Migration(from, to) to the builder using addMigrations.
3. Keep every prior JSON snapshot and commit the newly generated snapshot.
4. Use Room MigrationTestHelper to validate the migration with existing user data.

Never use fallbackToDestructiveMigration. Missing migration paths must fail rather
than silently erase data. Updates must explicitly set updatedAt; constructor
values apply only when a record is first constructed.

Dates are device-local ISO dates; event timestamps are UTC epoch milliseconds.
Queries over event times should use half-open ranges [from, to).
Food micros are nullable so unknown values aren't treated as measured zero.
Routine-to-exercise ordering and food-log nutrition snapshots are deferred schema
questions for their respective phases; no later-phase workflows are implemented.
