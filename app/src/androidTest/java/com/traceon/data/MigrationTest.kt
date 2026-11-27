package com.traceon.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.traceon.data.local.Migrations
import com.traceon.data.local.TraceonDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the exported schema (app/schemas) against Room. When version 2 is added, create the v1 database here,
 * insert representative rows, run MIGRATION_1_2 and assert the data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TraceonDatabase::class.java)

    @Test fun version1SchemaIsValid() {
        helper.createDatabase("migration-test", 1).close()
        helper.runMigrationsAndValidate("migration-test", 1, true, *Migrations.ALL).close()
    }
}
