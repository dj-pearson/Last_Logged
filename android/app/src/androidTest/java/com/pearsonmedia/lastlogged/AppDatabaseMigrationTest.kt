package com.pearsonmedia.lastlogged

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pearsonmedia.lastlogged.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migration coverage for [AppDatabase].
 *
 * Reads the exported schema JSON from `app/schemas/` (wired into the androidTest
 * assets in build.gradle.kts). If these tests fail with "Cannot find the schema
 * file", run a debug build first so Room exports the schema, then commit it.
 *
 * When you bump [AppDatabase]'s version, add a `migrateNtoM` test below. The
 * pattern is: open the old version, insert a representative row, run the
 * migration, then assert the row survived with the expected shape.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Guards the baseline: version 1 must be creatable from the committed schema.
     * This is what catches an entity change that was never re-exported.
     */
    @Test
    @Throws(IOException::class)
    fun version1SchemaIsCreatable() {
        val db = helper.createDatabase(TEST_DB, 1)
        try {
            val tables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0))
                }
            }

            assertTrue("tracker_items missing from v1 schema", tables.contains("tracker_items"))
            assertTrue("completion_logs missing from v1 schema", tables.contains("completion_logs"))
            assertTrue(
                "tracker_categories missing from v1 schema",
                tables.contains("tracker_categories")
            )
        } finally {
            db.close()
        }
    }

    /**
     * Opening the database at the current version with the real migration list must
     * succeed and pass Room's schema validation. This fails loudly if someone bumps
     * the version without adding a migration to [AppDatabase.MIGRATIONS].
     */
    @Test
    @Throws(IOException::class)
    fun migratesFromV1ToLatest() {
        helper.createDatabase(TEST_DB, 1).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

        try {
            // Touching the open helper forces Room to run migrations and validate the
            // resulting schema against the compiled entities.
            assertTrue(db.openHelper.writableDatabase.isOpen)

            // The validated schema must also be usable through a DAO.
            assertEquals(0, runBlocking { db.trackerItemDao().getActiveItemCount() })
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }
}
