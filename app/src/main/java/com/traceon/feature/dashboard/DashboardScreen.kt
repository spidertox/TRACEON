package com.traceon.feature.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.R
import com.traceon.core.common.Clock
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.ContentRowCard
import com.traceon.core.ui.components.DonutChart
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.HBarList
import com.traceon.core.ui.components.BarChart
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.StatTile
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DayCount
import com.traceon.domain.model.LabeledValue
import com.traceon.domain.repository.AnalyticsRepository
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.SettingsRepository
import com.traceon.feature.importcenter.DemoDataSeeder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

data class DashboardState(
    val totalItems: Int,
    val items: Map<ContentType, Int>,
    val events: Map<ContentType, Int>,
    val viewingMs: Long?,
    val readingMs: Long?,
    val searches: Int,
    val topApps: List<LabeledValue>,
    val topDomains: List<LabeledValue>,
    val topCategories: List<LabeledValue>,
    val topTopics: List<LabeledValue>,
    val platforms: List<LabeledValue>,
    val daily: List<DayCount>,
    val recent: List<ContentItem>,
    val demoCount: Int,
)

private class Totals(val items: Map<ContentType, Int>, val events: Map<ContentType, Int>, val total: Int, val viewing: Long?, val reading: Long?)
private class Lists(val apps: List<LabeledValue>, val domains: List<LabeledValue>, val categories: List<LabeledValue>, val topics: List<LabeledValue>, val platforms: List<LabeledValue>)
private class Extra(val daily: List<DayCount>, val searches: Int, val recent: List<ContentItem>, val demo: Int)

class DashboardViewModel(
    analytics: AnalyticsRepository,
    content: ContentRepository,
    private val settings: SettingsRepository,
    private val seeder: DemoDataSeeder,
    clock: Clock,
) : ViewModel() {
    private fun typeMap(list: List<LabeledValue>): Map<ContentType, Int> =
        list.associate { (runCatching { ContentType.valueOf(it.label) }.getOrDefault(ContentType.OTHER)) to it.value.toInt() }

    private val totals = combine(
        analytics.itemsByType(), analytics.eventsByType(), analytics.totalItems(), analytics.viewingMs(), analytics.readingMs(),
    ) { a, b, c, d, e -> Totals(typeMap(a), typeMap(b), c, d, e) }

    private val lists = combine(
        analytics.topApps(clock.now() - 7 * TimeUtils.DAY_MS, 6), analytics.topDomains(6), analytics.topCategories(8),
        analytics.topTopics(8), analytics.platformItems(),
    ) { a, d, c, t, p -> Lists(a, d, c, t, p) }

    private val extra = combine(
        analytics.dailyActivity(14), analytics.searchCount(), content.observeRecent(5), analytics.demoCount(),
    ) { d, s, r, demo -> Extra(d, s, r, demo) }

    val state: StateFlow<DashboardState?> = combine(totals, lists, extra) { t, l, e ->
        DashboardState(t.total, t.items, t.events, t.viewing, t.reading, e.searches, l.apps, l.domains, l.categories, l.topics, l.platforms, e.daily, e.recent, e.demo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun loadDemo() { viewModelScope.launch { seeder.seed() } }
    fun removeDemo() { viewModelScope.launch { settings.deleteDemoData() } }
}

private data class Kpi(val label: String, val value: String, val sub: String? = null)

private fun last14(daily: List<DayCount>, today: LocalDate): Pair<List<Long>, List<String>> {
    val byDay = daily.associate { it.day to it.value }
    val end = today.toEpochDay()
    val values = (13 downTo 0).map { byDay[end - it] ?: 0L }
    val labels = (13 downTo 0).map { off -> if (off % 3 == 0) today.minusDays(off.toLong()).dayOfMonth.toString() else "" }
    return values to labels
}

@Composable
fun HomeScreen(onOpenContent: (Long) -> Unit, onNavigate: (String) -> Unit, vm: DashboardViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    TraceonScaffold(
        title = "Home", bottomBarPresent = true,
        actions = { IconButton(onClick = { onNavigate("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Settings") } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.traceon_mark), contentDescription = "TRACEON logo", modifier = Modifier.size(width = 76.dp, height = 60.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("TRACEON", style = MaterialTheme.typography.headlineLarge)
                        Text("YOUR DIGITAL MEMORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val st = s
            if (st == null) {
                item { Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@LazyColumn
            }
            if (st.demoCount > 0) {
                item {
                    TraceonCard {
                        Text("Demo data is loaded", style = MaterialTheme.typography.titleMedium)
                        Text("These ${st.demoCount} items are labeled samples, not your real activity. They are excluded from backups and exports.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = vm::removeDemo) { Text("Remove demo data") }
                    }
                }
            }
            if (st.totalItems == 0) {
                item {
                    EmptyState(
                        title = "Your digital memory is empty",
                        message = "TRACEON shows only what it has really recorded. Import your history, share links to TRACEON from other apps, add items by hand, or turn on app-usage tracking.",
                        actionLabel = "Open Import Center", onAction = { onNavigate("import") },
                        secondaryLabel = "Load labeled demo data", onSecondary = vm::loadDemo,
                    )
                }
                return@LazyColumn
            }
            val articles = listOf(ContentType.ARTICLE, ContentType.BLOG, ContentType.TUTORIAL, ContentType.DOCUMENTATION, ContentType.COURSE, ContentType.DOCUMENT).sumOf { st.items[it] ?: 0 }
            fun ev(vararg t: ContentType) = t.sumOf { st.events[it] ?: 0 }
            val kpis = listOf(
                Kpi("Tracked items", st.totalItems.toString(), "${st.events.values.sum()} recorded events"),
                Kpi("Searches", st.searches.toString(), "${st.items[ContentType.SEARCH_QUERY] ?: 0} unique queries"),
                Kpi("Reels", (st.items[ContentType.REEL] ?: 0).toString(), "${ev(ContentType.REEL)} views"),
                Kpi("Shorts", (st.items[ContentType.SHORT] ?: 0).toString(), "${ev(ContentType.SHORT)} views"),
                Kpi("Videos", (st.items[ContentType.VIDEO] ?: 0).toString(), "${ev(ContentType.VIDEO)} views"),
                Kpi("Articles & docs", articles.toString(), "${ev(ContentType.ARTICLE, ContentType.BLOG, ContentType.TUTORIAL, ContentType.DOCUMENTATION, ContentType.COURSE, ContentType.DOCUMENT)} visits"),
                Kpi("Websites", (st.items[ContentType.WEBSITE] ?: 0).toString(), "${ev(ContentType.WEBSITE)} visits"),
                Kpi("Viewing time", TimeUtils.formatDuration(st.viewingMs), "only where measurable"),
                Kpi("Reading time", TimeUtils.formatDuration(st.readingMs), "only where measurable"),
            )
            items(kpis.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { StatTile(it.label, it.value, Modifier.weight(1f), it.sub) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                TraceonCard {
                    SectionTitle("Daily activity - last 14 days")
                    val (values, labels) = last14(st.daily, LocalDate.now())
                    BarChart(values, labels)
                }
            }
            if (st.platforms.isNotEmpty()) item { TraceonCard { SectionTitle("Platform comparison (items)"); HBarList(st.platforms) } }
            if (st.topCategories.isNotEmpty()) item { TraceonCard { SectionTitle("Category distribution"); DonutChart(st.topCategories) } }
            if (st.topTopics.isNotEmpty()) item { TraceonCard { SectionTitle("Most explored topics"); HBarList(st.topTopics) } }
            item {
                TraceonCard {
                    SectionTitle("Most used applications - last 7 days")
                    if (st.topApps.isEmpty()) {
                        Text("No app usage recorded. Turn on app-usage tracking in Settings to measure time in apps you choose.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onNavigate("settings") }) { Text("Open tracking controls") }
                    } else HBarList(st.topApps, format = { TimeUtils.formatDuration(it) })
                }
            }
            if (st.topDomains.isNotEmpty()) item { TraceonCard { SectionTitle("Most visited domains"); HBarList(st.topDomains, format = { "$it visits" }) } }
            item {
                SectionTitle("Recently tracked")
            }
            items(st.recent, key = { it.id }) { item ->
                ContentRowCard(item, onClick = { onOpenContent(item.id) }, whenText = TimeUtils.formatDateTime(item.lastSeen))
            }
            item {
                SectionTitle("Quick actions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onNavigate("import") }, modifier = Modifier.weight(1f)) { Text("Import", maxLines = 1) }
                    FilledTonalButton(onClick = { onNavigate("manual") }, modifier = Modifier.weight(1f)) { Text("Add", maxLines = 1) }
                    FilledTonalButton(onClick = { onNavigate("discovery") }, modifier = Modifier.weight(1f)) { Text("Ask", maxLines = 1) }
                    FilledTonalButton(onClick = { onNavigate("browser") }, modifier = Modifier.weight(1f)) { Text("Browse", maxLines = 1) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
