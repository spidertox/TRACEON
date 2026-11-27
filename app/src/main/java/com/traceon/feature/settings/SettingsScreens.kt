@file:OptIn(ExperimentalLayoutApi::class)

package com.traceon.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.ui.components.ConfirmDialog
import com.traceon.core.ui.components.InfoChip
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.core.ui.theme.Brand
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ThemeMode
import com.traceon.domain.model.TrackedApp
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.SettingsRepository
import com.traceon.feature.tracking.PermissionChecker
import com.traceon.feature.tracking.UsageSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class SettingsViewModel(
    private val appContext: Context,
    private val pm: PackageManager,
    private val settings: SettingsRepository,
    private val content: ContentRepository,
    private val checker: PermissionChecker,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.themeMode().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.DARK)
    val sources: StateFlow<Map<CollectionMethod, Boolean>> = settings.sourceStates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val apps: StateFlow<List<TrackedApp>> = settings.trackedApps { pm.getLaunchIntentForPackage(it) != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow<String?>(null)
    val usageGranted = MutableStateFlow(checker.hasUsageAccess())

    fun refreshPermission() { usageGranted.value = checker.hasUsageAccess() }
    fun setTheme(m: ThemeMode) { viewModelScope.launch { settings.setThemeMode(m) } }

    fun setUsageTracking(enabled: Boolean) {
        viewModelScope.launch {
            settings.setSourceEnabled(CollectionMethod.USAGE_STATS, enabled)
            if (enabled) { UsageSyncScheduler.schedule(appContext); UsageSyncScheduler.syncNow(appContext) } else UsageSyncScheduler.cancel(appContext)
        }
    }

    fun setBrowserTracking(enabled: Boolean) { viewModelScope.launch { settings.setSourceEnabled(CollectionMethod.TRACEON_BROWSER, enabled) } }
    fun setApp(app: TrackedApp, enabled: Boolean) { viewModelScope.launch { settings.setAppTracking(app.packageName, app.label, app.platformSlug, enabled) } }
    fun reclassify() { viewModelScope.launch { message.value = "Re-classified ${content.reclassifyAll()} items (items you categorised yourself were left alone)." } }
    fun deleteDemo() { viewModelScope.launch { message.value = "Removed ${settings.deleteDemoData()} demo items." } }
    fun deleteAll() { viewModelScope.launch { settings.deleteAllData(); message.value = "All TRACEON data was deleted from this device." } }
    fun clearMessage() { message.value = null }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit, vm: SettingsViewModel = koinViewModel()) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val granted by vm.usageGranted.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshPermission() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val usageOn = sources[CollectionMethod.USAGE_STATS] == true

    TraceonScaffold(title = "Settings", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { m -> item { TraceonCard { Text(m, style = MaterialTheme.typography.bodyMedium); TextButton(onClick = vm::clearMessage) { Text("Dismiss") } } } }
            item {
                TraceonCard {
                    SectionTitle("Appearance")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.values().forEach { m -> FilterChip(selected = theme == m, onClick = { vm.setTheme(m) }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                    }
                }
            }
            item {
                TraceonCard {
                    SectionTitle("What TRACEON collects, and why")
                    Text("Only what you switch on below, only on this device:", style = MaterialTheme.typography.bodyMedium)
                    Text("- App usage (off by default): the start and end time of foreground use of apps you choose. Never what is on screen.", style = MaterialTheme.typography.bodyMedium)
                    Text("- Shared links: only items you confirm in the Share screen.", style = MaterialTheme.typography.bodyMedium)
                    Text("- Imported files: only files you pick, labeled as imported.", style = MaterialTheme.typography.bodyMedium)
                    Text("- TRACEON browser: pages you open inside it, never in Private mode.", style = MaterialTheme.typography.bodyMedium)
                    Text("Never collected: passwords, messages, payment details, screenshots, incognito sessions. No automatic cloud upload.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Tracking controls")
                    SwitchRow("App usage tracking", "Measures time in the apps you pick below. Needs Usage access.", usageOn) { vm.setUsageTracking(it) }
                    if (usageOn && !granted) {
                        Text("Usage access is not granted, so nothing is being recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { onNavigate("permissions") }) { Text("Grant access") }
                    }
                    SwitchRow("Record pages in the TRACEON browser", "Private mode always records nothing.", sources[CollectionMethod.TRACEON_BROWSER] ?: true) { vm.setBrowserTracking(it) }
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Applications")
                    if (apps.isEmpty()) Text("None of the supported apps (YouTube, Instagram, Facebook, Snapchat, Moj, Josh and common browsers) were found on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Text("Choose which apps may be measured. Off means nothing is recorded for that app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(apps, key = { it.packageName }) { app ->
                TraceonCard { SwitchRow(app.label, app.packageName, app.enabled) { vm.setApp(app, it) } }
            }
            item {
                TraceonCard {
                    SectionTitle("More")
                    TextButton(onClick = { onNavigate("permissions") }) { Text("Permissions") }
                    TextButton(onClick = { onNavigate("import") }) { Text("Import Center") }
                    TextButton(onClick = { onNavigate("backup") }) { Text("Backup, restore and export") }
                    TextButton(onClick = { onNavigate("browser") }) { Text("TRACEON browser") }
                    TextButton(onClick = { onNavigate("discovery") }) { Text("Smart Discovery") }
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Your data")
                    TextButton(onClick = vm::reclassify) { Text("Re-run classification") }
                    TextButton(onClick = { confirm = "demo" }) { Text("Remove demo data") }
                    TextButton(onClick = { confirm = "all" }) { Text("Delete all TRACEON data", color = MaterialTheme.colorScheme.error) }
                    Text("Version 0.1.0. Data lives only in this app's private storage; Android backup is disabled.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    when (confirm) {
        "demo" -> ConfirmDialog("Remove demo data?", "Only items labeled DEMO are removed.", "Remove", { vm.deleteDemo(); confirm = null }, { confirm = null })
        "all" -> ConfirmDialog("Delete everything?", "All items, sessions, searches, imports and tags are removed from this device. This cannot be undone. Make a backup first if you want to keep anything.", "Delete all", { vm.deleteAll(); confirm = null }, { confirm = null })
        else -> Unit
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

class PermissionsViewModel(private val checker: PermissionChecker, private val settings: SettingsRepository) : ViewModel() {
    val granted = MutableStateFlow(checker.hasUsageAccess())
    val usageEnabled: StateFlow<Boolean> = settings.sourceStates().map { it[CollectionMethod.USAGE_STATS] == true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refresh() {
        val g = checker.hasUsageAccess()
        granted.value = g
        viewModelScope.launch { settings.recordPermission("usage_access", g) }
    }

    fun settingsIntents() = checker.usageAccessIntent() to checker.usageAccessFallbackIntent()
}

@Composable
fun PermissionsScreen(onBack: () -> Unit, vm: PermissionsViewModel = koinViewModel()) {
    val granted by vm.granted.collectAsStateWithLifecycle()
    val enabled by vm.usageEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    TraceonScaffold(title = "Permissions", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TraceonCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usage access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    InfoChip(if (granted) "Granted" else "Not granted", color = if (granted) Brand.Mint else Brand.Coral)
                }
                Text("Lets Android tell TRACEON when an app you chose was in the foreground, and for how long. It cannot show what you did inside the app, and it never includes passwords, messages or payments.", style = MaterialTheme.typography.bodyMedium)
                if (enabled && !granted) Text("App usage tracking is switched on but access was revoked, so recording is paused until you grant it again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    val (specific, fallback) = vm.settingsIntents()
                    try { context.startActivity(specific) } catch (e: Exception) {
                        try { context.startActivity(fallback) } catch (e2: Exception) { /* no settings screen available */ }
                    }
                }) { Text(if (granted) "Review in system settings" else "Open Usage access settings") }
                Text("Android keeps this decision in system settings; TRACEON cannot grant it itself and re-checks when you return.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TraceonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accessibility service", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    InfoChip("Not used", color = Brand.Amber)
                }
                Text("This version does not use an accessibility service, so it cannot detect individual videos or reels inside other apps. Use history import, Share, or the TRACEON browser for item-level records.", style = MaterialTheme.typography.bodyMedium)
            }
            TraceonCard {
                Text("Network", style = MaterialTheme.typography.titleMedium)
                Text("TRACEON makes no network requests of its own. Only the TRACEON browser loads the pages you open. No data is uploaded.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
