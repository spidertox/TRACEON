package com.traceon.data.repository

import androidx.room.withTransaction
import com.traceon.core.common.Clock
import com.traceon.core.common.PlatformCatalog
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.local.entity.ApplicationEntity
import com.traceon.data.local.entity.PermissionStatusEntity
import com.traceon.data.local.entity.SearchHistoryEntity
import com.traceon.data.local.entity.TrackingSourceEntity
import com.traceon.data.local.entity.UserPreferenceEntity
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ThemeMode
import com.traceon.domain.model.TrackedApp
import com.traceon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(private val db: TraceonDatabase, private val clock: Clock) : SettingsRepository {
    private val system = db.systemDao()
    private val content = db.contentDao()
    private val activity = db.activityDao()
    private val taxonomy = db.taxonomyDao()

    override fun themeMode(): Flow<ThemeMode> = system.observePref(KEY_THEME).map { v ->
        v?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }

    override suspend fun setThemeMode(mode: ThemeMode) = system.setPref(UserPreferenceEntity(KEY_THEME, mode.name))

    override fun sourceStates(): Flow<Map<CollectionMethod, Boolean>> =
        system.observeSources().map { rows -> rows.associate { it.method to it.enabled } }

    override suspend fun setSourceEnabled(method: CollectionMethod, enabled: Boolean) =
        system.upsertSource(TrackingSourceEntity(method = method, enabled = enabled, updatedAt = clock.now()))

    override fun trackedApps(installed: (String) -> Boolean): Flow<List<TrackedApp>> = system.observeApps().map { rows ->
        val enabled = rows.filter { it.trackingEnabled }.map { it.packageName }.toSet()
        PlatformCatalog.trackableApps
            .flatMap { d -> d.packages.map { pkg -> TrackedApp(pkg, d.name, d.slug, installed(pkg), pkg in enabled) } }
            .filter { it.installed }
    }

    override suspend fun setAppTracking(packageName: String, label: String, platformSlug: String?, enabled: Boolean) {
        db.withTransaction {
            val platformId = platformSlug?.let { ensurePlatform(taxonomy, it) }
            system.insertApp(ApplicationEntity(packageName = packageName, label = label, platformId = platformId, trackingEnabled = enabled))
            system.setAppTracking(packageName, enabled)
        }
    }

    override suspend fun recordPermission(key: String, granted: Boolean) =
        system.upsertPermission(PermissionStatusEntity(permissionKey = key, granted = granted, checkedAt = clock.now()))

    override fun permissionStates(): Flow<Map<String, Boolean>> =
        system.observePermissions().map { rows -> rows.associate { it.permissionKey to it.granted } }

    override suspend fun getPref(key: String): String? = system.getPref(key)
    override suspend fun setPref(key: String, value: String) = system.setPref(UserPreferenceEntity(key, value))

    override fun recentSearches(limit: Int): Flow<List<String>> = system.observeRecentSearches(limit).map { rows -> rows.map { it.queryText } }
    override fun savedSearches(): Flow<List<String>> = system.observeSavedSearches().map { rows -> rows.map { it.queryText } }

    override suspend fun rememberSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        system.insertSearchHistory(SearchHistoryEntity(queryText = q, createdAt = clock.now()))
        system.touchSearchHistory(q, clock.now())
    }

    override suspend fun setSearchSaved(query: String, saved: Boolean) {
        rememberSearch(query)
        system.markSearchSaved(query.trim(), saved)
    }

    override suspend fun clearRecentSearches() = system.clearRecentSearches()

    override suspend fun deleteDemoData(): Int = db.withTransaction {
        val n = content.deleteDemo()
        activity.deleteDemoAppSessions()
        n
    }

    override suspend fun deleteAllData() {
        db.withTransaction {
            content.deleteAll()
            activity.deleteAppSessions()
            system.deleteAllJobs()
            system.deleteAllSearchHistory()
            taxonomy.deleteAllTopics()
            taxonomy.deleteAllCreators()
            taxonomy.deleteAllTags()
            system.setPref(UserPreferenceEntity("usage_last_sync", "0"))
        }
    }

    companion object {
        const val KEY_THEME = "theme_mode"
    }
}
