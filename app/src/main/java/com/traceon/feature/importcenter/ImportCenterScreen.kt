package com.traceon.feature.importcenter

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.PlatformCatalog
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.DropdownChip
import com.traceon.core.ui.components.KeyValueRow
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.model.ImportJob
import com.traceon.domain.model.ImportSource
import com.traceon.domain.model.ImportSummary
import com.traceon.domain.repository.AnalyticsRepository
import com.traceon.domain.repository.ImportRepository
import com.traceon.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.ByteArrayOutputStream

data class ImportUi(val busy: Boolean = false, val progress: Pair<Int, Int>? = null, val summary: ImportSummary? = null, val error: String? = null)

class ImportViewModel(
    private val resolver: ContentResolver,
    private val importer: ImportRepository,
    analytics: AnalyticsRepository,
    private val settings: SettingsRepository,
    private val seeder: DemoDataSeeder,
) : ViewModel() {
    val ui = MutableStateFlow(ImportUi())
    val jobs: StateFlow<List<ImportJob>> = importer.observeJobs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val demoCount: StateFlow<Int> = analytics.demoCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun startImport(uri: Uri, source: ImportSource, browserSlug: String) {
        viewModelScope.launch {
            ui.value = ImportUi(busy = true)
            val read = try {
                withContext(Dispatchers.IO) { readText(uri) }
            } catch (e: Exception) {
                ui.value = ImportUi(error = e.message ?: "The file could not be read.")
                return@launch
            }
            val summary = importer.importFile(source, read.first, read.second, browserSlug) { done, total -> ui.update { it.copy(progress = done to total) } }
            ui.value = ImportUi(summary = summary)
        }
    }

    fun loadDemo() { viewModelScope.launch { seeder.seed() } }
    fun removeDemo() { viewModelScope.launch { settings.deleteDemoData() } }

    private fun readText(uri: Uri): Pair<String, String> {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "import"
        val input = resolver.openInputStream(uri) ?: error("The file could not be opened.")
        val text = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) error("This file is larger than 60 MB. Export a shorter date range and try again.")
                out.write(buffer, 0, n)
            }
            out.toString(Charsets.UTF_8.name())
        }
        return name to text
    }

    companion object { const val MAX_BYTES = 60L * 1024 * 1024 }
}

@Composable
fun ImportCenterScreen(onBack: () -> Unit, onManual: () -> Unit, vm: ImportViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val demoCount by vm.demoCount.collectAsStateWithLifecycle()
    var browser by remember { mutableStateOf("chrome") }
    val browserLauncher = rememberLauncherForActivityResult(remember { ActivityResultContracts.OpenDocument() }) { uri -> if (uri != null) vm.startImport(uri, ImportSource.BROWSER_HISTORY, browser) }
    val googleLauncher = rememberLauncherForActivityResult(remember { ActivityResultContracts.OpenDocument() }) { uri -> if (uri != null) vm.startImport(uri, ImportSource.GOOGLE_ACTIVITY, "chrome") }

    TraceonScaffold(title = "Import Center", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "TRACEON cannot read other apps' or browsers' private history. These are the supported ways to bring activity in. Imported records are labeled as imported, and importing the same file twice never double-counts.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                TraceonCard {
                    SectionTitle("Browser history file")
                    Text("Accepts Google Takeout Chrome BrowserHistory.json, a JSON list of {url, title, time}, or CSV with url and time columns. Search-engine result pages become searches; incognito history is never in these exports.", style = MaterialTheme.typography.bodyMedium)
                    DropdownChip("Browser", PlatformCatalog.nameOf(browser), PlatformCatalog.browsers.filter { it.slug != "traceon_browser" }.map { it.name to it.slug as String? }, { browser = it ?: "chrome" })
                    Button(onClick = { browserLauncher.launch(arrayOf("*/*")) }, enabled = !ui.busy) { Text("Choose history file") }
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Google activity (Takeout)")
                    Text("At takeout.google.com export \"My Activity\" or \"YouTube\" as JSON, then choose the file. Watched, Searched for and Visited entries are imported; ad impressions are ignored.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { googleLauncher.launch(arrayOf("*/*")) }, enabled = !ui.busy) { Text("Choose activity JSON") }
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Share and manual entry")
                    Text("In any app, use Share and pick \"Save to TRACEON\". You confirm each item before it is saved. You can also add items by hand.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onManual) { Text("Add an item manually") }
                }
            }
            if (ui.busy) item {
                TraceonCard {
                    Text("Importing...", style = MaterialTheme.typography.titleMedium)
                    val p = ui.progress
                    if (p != null && p.second > 0) LinearProgressIndicator(progress = { p.first.toFloat() / p.second }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            ui.error?.let { err -> item { TraceonCard { Text("Import failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error); Text(err, style = MaterialTheme.typography.bodyMedium) } } }
            ui.summary?.let { s ->
                item {
                    TraceonCard {
                        Text(if (s.imported > 0) "Import finished" else "Nothing new was imported", style = MaterialTheme.typography.titleMedium)
                        KeyValueRow("New events recorded", s.imported.toString())
                        KeyValueRow("Already present (skipped)", s.duplicates.toString())
                        KeyValueRow("Unreadable or unsupported", s.skipped.toString())
                        s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Demo data")
                    Text("Labeled sample items for exploring the app. They are marked DEMO everywhere and excluded from backups.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (demoCount == 0) OutlinedButton(onClick = vm::loadDemo) { Text("Load demo data") } else OutlinedButton(onClick = vm::removeDemo) { Text("Remove $demoCount demo items") }
                    }
                }
            }
            if (jobs.isNotEmpty()) {
                item { SectionTitle("Import history") }
                item {
                    TraceonCard {
                        jobs.take(10).forEach { j ->
                            KeyValueRow("${TimeUtils.formatDateTime(j.startedAt)} ${j.source.label}", "${j.status.name.lowercase()} - ${j.imported} new, ${j.duplicates} dup, ${j.skipped} skipped")
                        }
                    }
                }
            }
        }
    }
}
