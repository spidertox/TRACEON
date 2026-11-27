package com.traceon.data.local

import androidx.room.migration.Migration

/**
 * Migration strategy: schemas are exported to app/schemas (see build.gradle.kts). Every schema change
 * bumps TraceonDatabase.version and adds an explicit Migration here plus a MigrationTestHelper test.
 * Destructive fallback is deliberately NOT enabled: an unhandled migration fails loudly instead of
 * silently wiping a user's history.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
