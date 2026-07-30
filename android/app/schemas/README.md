# Room exported schemas

Room writes one JSON file per database version here at build time
(`room.schemaLocation` in `app/build.gradle.kts`).

**These files are committed on purpose.** They are the input to
`AppDatabaseMigrationTest` and the only way to prove a schema change ships with
a working migration. `.gitignore` explicitly does not exclude this directory.

## Workflow when changing an entity

1. Add a `Migration` to `AppDatabase.MIGRATIONS` and bump `@Database(version = …)`.
2. Run `./gradlew assembleDebug` so Room exports the new `<version>.json`.
3. Commit the generated JSON alongside the entity change.
4. Add a `migrateNtoM` case to `AppDatabaseMigrationTest`.

Android CI fails if the committed schema is stale relative to the entities.

## Bootstrapping

This directory was previously gitignored, so no baseline JSON exists yet. The
first green Android CI run uploads the generated schema as the `room-schemas`
artifact — download it and commit `1.json` to arm the CI gate. The exported
JSON cannot be hand-authored because Room derives an `identityHash` from the
compiled entities during annotation processing.
