package com.traceon.feature.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.traceon.domain.repository.TrackingRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

interface UsageEventSource {
    fun hasPermission(): Boolean
    fun query(fromMs: Long, toMs: Long): List<RawUsageEvent>
}

/** Reads "Usage access" state. The user grants it in system settings; the app cannot grant it itself. */
class PermissionChecker(private val context: Context) {
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun usageAccessFallbackIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

class AndroidUsageEventSource(private val context: Context) : UsageEventSource {
    override fun hasPermission(): Boolean = PermissionChecker(context).hasUsageAccess()

    @Suppress("DEPRECATION")
    override fun query(fromMs: Long, toMs: Long): List<RawUsageEvent> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(fromMs, toMs) ?: return emptyList()
        val out = ArrayList<RawUsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> out += RawUsageEvent(e.timeStamp, e.packageName, RawUsageEvent.Type.FOREGROUND)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> out += RawUsageEvent(e.timeStamp, e.packageName, RawUsageEvent.Type.BACKGROUND)
            }
        }
        return out
    }
}

class UsageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val tracking: TrackingRepository by inject()

    override suspend fun doWork(): Result = try {
        tracking.syncUsage()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

object UsageSyncScheduler {
    private const val PERIODIC = "traceon_usage_sync"
    private const val ONCE = "traceon_usage_sync_now"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<UsageSyncWorker>().build())
    }
}
