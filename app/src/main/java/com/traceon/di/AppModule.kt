package com.traceon.di

import androidx.room.Room
import com.traceon.core.common.Clock
import com.traceon.core.common.WallClock
import com.traceon.data.backup.BackupRepositoryImpl
import com.traceon.data.local.Migrations
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.repository.AnalyticsRepositoryImpl
import com.traceon.data.repository.ContentRepositoryImpl
import com.traceon.data.repository.ImportRepositoryImpl
import com.traceon.data.repository.SettingsRepositoryImpl
import com.traceon.data.repository.TrackingRepositoryImpl
import com.traceon.domain.classification.ContentClassifier
import com.traceon.domain.repository.AnalyticsRepository
import com.traceon.domain.repository.BackupRepository
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.ImportRepository
import com.traceon.domain.repository.SettingsRepository
import com.traceon.domain.repository.TrackingRepository
import com.traceon.domain.usecase.DiscoveryEngine
import com.traceon.feature.activity.ActivityViewModel
import com.traceon.feature.analytics.AnalyticsViewModel
import com.traceon.feature.backup.BackupViewModel
import com.traceon.feature.browser.BrowserViewModel
import com.traceon.feature.contentdetails.DetailViewModel
import com.traceon.feature.contentmap.ContentMapViewModel
import com.traceon.feature.dashboard.DashboardViewModel
import com.traceon.feature.discovery.DiscoveryViewModel
import com.traceon.feature.importcenter.DemoDataSeeder
import com.traceon.feature.importcenter.ImportViewModel
import com.traceon.feature.importcenter.ManualEntryViewModel
import com.traceon.feature.importcenter.ShareViewModel
import com.traceon.feature.search.SearchViewModel
import com.traceon.feature.settings.PermissionsViewModel
import com.traceon.feature.settings.SettingsViewModel
import com.traceon.feature.tracking.AndroidUsageEventSource
import com.traceon.feature.tracking.PermissionChecker
import com.traceon.feature.tracking.UsageEventSource
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<Clock> { WallClock }
    single {
        Room.databaseBuilder(androidContext(), TraceonDatabase::class.java, "traceon.db")
            .addMigrations(*Migrations.ALL)
            .build()
    }
    single { ContentClassifier() }

    single<ContentRepository> { ContentRepositoryImpl(get(), get(), get()) }
    single<AnalyticsRepository> {
        val db = get<TraceonDatabase>()
        AnalyticsRepositoryImpl(db.analyticsDao(), db.contentDao(), get())
    }
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    single<ImportRepository> { ImportRepositoryImpl(get(), get(), get()) }
    single<UsageEventSource> { AndroidUsageEventSource(androidContext()) }
    single<TrackingRepository> { TrackingRepositoryImpl(get(), get(), get()) }
    single<BackupRepository> { BackupRepositoryImpl(get(), get(), get()) }
    single { DiscoveryEngine(get(), get()) }
    single { DemoDataSeeder(get(), get()) }
    single { PermissionChecker(androidContext()) }

    viewModel { DashboardViewModel(get(), get(), get(), get(), get()) }
    viewModel { ActivityViewModel(get(), get()) }
    viewModel { params -> DetailViewModel(params.get(), get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { AnalyticsViewModel(get(), get(), get()) }
    viewModel { DiscoveryViewModel(get()) }
    viewModel { ContentMapViewModel(get(), get()) }
    viewModel { ImportViewModel(androidContext().contentResolver, get(), get(), get(), get()) }
    viewModel { ManualEntryViewModel(get(), get()) }
    viewModel { ShareViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(androidContext(), androidContext().packageManager, get(), get(), get()) }
    viewModel { PermissionsViewModel(get(), get()) }
    viewModel { BackupViewModel(androidContext().contentResolver, get()) }
    viewModel { BrowserViewModel(get(), get(), get()) }
}
