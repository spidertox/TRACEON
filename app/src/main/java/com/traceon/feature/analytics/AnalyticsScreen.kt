@file:OptIn(ExperimentalLayoutApi::class, ExperimentalCoroutinesApi::class)

package com.traceon.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.Clock
import com.traceon.core.common.PlatformCatalog
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.BarChart
import com.traceon.core.ui.components.ContentRowCard
import com.traceon.core.ui.components.DonutChart
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.HBarList
import com.traceon.core.ui.components.KeyValueRow
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.StatTile
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DayCount
import com.traceon.domain.model.LabeledValue
import com.traceon.domain.model.SearchFilters
import com.traceon.domain.model.TimelineEntry
import com.traceon.domain.model.TimelineFilter
import com.traceon.domain.repository.AnalyticsRepository
import com.traceon.domain.repository.ContentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class AnalyticsViewModel(private val analytics: AnalyticsRepository, private val content: ContentRepository, clock: Clock) : ViewModel() {
    private fun <T> Flow<T>.hold(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    private val now = clock.now()
    private val empty = emptyList<LabeledValue>()

    val daily = analytics.dailyActivity(365).hold(emptyList<DayCount>())
    val platformItems = analytics.platformItems().hold(empty)
    val platformEvents = analytics.platformEvents().hold(empty)
    val categories = analytics.topCategories(10).hold(empty)
    val apps7 = analytics.topApps(now - 7 * TimeUtils.DAY_MS, 10).hold(empty)
    val apps30 = analytics.topApps(now - 30 * TimeUtils.DAY_MS, 10).hold(empty)

    val topics = content.observeTopicsOverview().hold(empty)
    val selectedTopic = MutableStateFlow<String?>(null)
    val topicItems = selectedTopic.flatMapLatest { t -> flow { emit(if (t == null) emptyList() else content.itemsForTopic(t, 50)) } }.hold(emptyList<ContentItem>())
    val relatedTopics = selectedTopic.flatMapLatest { t -> flow { emit(if (t == null) emptyList() else content.relatedTopics(t, 8)) } }.hold(empty)

    val searches = analytics.searchCount().hold(0)
    val pages = analytics.distinctPages().hold(0)
    val domainCount = analytics.distinctDomains().hold(0)
    val domains = analytics.topDomains(15).hold(empty)
    val browsers = analytics.browserActivity().hold(empty)
    val engines = analytics.engineActivity().hold(empty)
    val browsing = analytics.dailyBrowsing(365).hold(emptyList<DayCount>())
    val repeated = analytics.repeatedPages(8).hold(empty)
    val recentPages = content.observeTimeline(TimelineFilter(0L, Long.MAX_VALUE, null, "BROWSER"), 10).hold(emptyList<TimelineEntry>())
    val recentSearches = content.observeTimeline(TimelineFilter(0L, Long.MAX_VALUE, null, "SEARCH"), 10).hold(emptyList<TimelineEntry>())
    val searchTopics = analytics.searchCount().flatMapLatest {
        flow {
            val items = content.search("", SearchFilters(types = setOf(ContentType.SEARCH_QUERY)), 2000)
            emit(items.groupingBy { it.category ?: "Unclassified" }.eachCount().map { LabeledValue(it.key, it.value.toLong()) }.sortedByDescending { it.value }.take(8))
        }
    }.hold(empty)

    fun selectTopic(t: String?) { selectedTopic.value = t }
}

private fun rolling7(days: List<DayCount>, today: LocalDate, windows: Int = 12): Pair<List<Long>, List<String>> {
    val byDay = days.associate { it.day to it.value }
    val end = today.toEpochDay()
    val values = (windows - 1 downTo 0).map { w -> (0 until 7).sumOf { d -> byDay[end - (w * 7 + d)] ?: 0L } }
    val labels = (windows - 1 downTo 0).map { w -> if (w % 3 == 0) "-${w}w" else "" }
    return values to labels
}

private fun monthly(days: List<DayCount>, today: LocalDate, months: Int = 6): Pair<List<Long>, List<String>> {
    val sums = HashMap<YearMonth, Long>()
    for (d in days) {
        val ym = YearMonth.from(LocalDate.ofEpochDay(d.day))
        sums[ym] = (sums[ym] ?: 0L) + d.value
    }
    val current = YearMonth.from(today)
    val range = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
    return range.map { sums[it] ?: 0L } to range.map { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

private fun lastDays(days: List<DayCount>, today: LocalDate, n: Int = 14): Pair<List<Long>, List<String>> {
    val byDay = days.associate { it.day to it.value }
    val end = today.toEpochDay()
    return (n - 1 downTo 0).map { byDay[end - it] ?: 0L } to (n - 1 downTo 0).map { if (it % 3 == 0) today.minusDays(it.toLong()).dayOfMonth.toString() else "" }
}

private fun label(slug: String) = PlatformCatalog.nameOf(slug)

@Composable
fun AnalyticsScreen(onOpenContent: (Long) -> Unit, vm: AnalyticsViewModel = koinViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    TraceonScaffold(title = "Analytics", bottomBarPresent = true) { pad ->
        androidx.compose.foundation.layout.Column(Modifier.padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                listOf("Overview", "Platforms", "Topics", "Web").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> OverviewTab(vm)
                1 -> PlatformsTab(vm)
                2 -> TopicsTab(vm, onOpenContent)
                else -> WebTab(vm, onOpenContent)
            }
        }
    }
}

private val tabPadding = PaddingValues(16.dp)

@Composable
private fun OverviewTab(vm: AnalyticsViewModel) {
    val daily by vm.daily.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val end = today.toEpochDay()
    val byDay = daily.associate { it.day to it.value }
    val current = (0L..6L).sumOf { byDay[end - it] ?: 0L }
    val previous = (7L..13L).sumOf { byDay[end - it] ?: 0L }
    LazyColumn(contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (daily.isEmpty()) item { EmptyState("No activity to chart yet", "Charts are built only from recorded events. Import history, share links or add items first.") }
        item {
            TraceonCard {
                SectionTitle("Trend - last 7 days vs the 7 before")
                val delta = if (previous == 0L) null else (current - previous) * 100 / previous
                Text("$current events", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (delta == null) "No events in the previous 7 days to compare with." else "${if (delta >= 0) "+" else ""}$delta% vs the previous 7 days ($previous events)",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { TraceonCard { SectionTitle("Daily - last 14 days"); val (v, l) = lastDays(daily, today); BarChart(v, l) } }
        item { TraceonCard { SectionTitle("Weekly - rolling 7-day windows, 12 weeks"); val (v, l) = rolling7(daily, today); BarChart(v, l) } }
        item { TraceonCard { SectionTitle("Monthly - last 6 months"); val (v, l) = monthly(daily, today); BarChart(v, l) } }
        if (categories.isNotEmpty()) item { TraceonCard { SectionTitle("Category distribution"); DonutChart(categories) } }
    }
}

@Composable
private fun PlatformsTab(vm: AnalyticsViewModel) {
    val items by vm.platformItems.collectAsStateWithLifecycle()
    val events by vm.platformEvents.collectAsStateWithLifecycle()
    val apps7 by vm.apps7.collectAsStateWithLifecycle()
    val apps30 by vm.apps30.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items.isEmpty()) item { EmptyState("No platform data yet", "Platform comparison appears once content has been recorded.") }
        if (items.isNotEmpty()) item { TraceonCard { SectionTitle("Distinct items per platform"); HBarList(items, maxRows = 12) } }
        if (events.isNotEmpty()) item { TraceonCard { SectionTitle("Recorded events per platform"); HBarList(events, maxRows = 12) } }
        item {
            TraceonCard {
                SectionTitle("App usage - time in apps you enabled")
                if (apps30.isEmpty()) Text("No app usage recorded. Enable it in Settings; it measures time in an app, never which video or page was open.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text("Last 7 days", style = MaterialTheme.typography.labelMedium)
                    HBarList(apps7, format = { TimeUtils.formatDuration(it) })
                    Spacer(Modifier.height(10.dp))
                    Text("Last 30 days", style = MaterialTheme.typography.labelMedium)
                    HBarList(apps30, format = { TimeUtils.formatDuration(it) })
                }
            }
        }
    }
}

@Composable
private fun TopicsTab(vm: AnalyticsViewModel, onOpenContent: (Long) -> Unit) {
    val topics by vm.topics.collectAsStateWithLifecycle()
    val selected by vm.selectedTopic.collectAsStateWithLifecycle()
    val items by vm.topicItems.collectAsStateWithLifecycle()
    val related by vm.relatedTopics.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (topics.isEmpty()) item { EmptyState("No topics yet", "Topics are detected offline from titles, descriptions, tags and domains. Items with too little metadata stay unclassified rather than guessed.") }
        if (topics.isNotEmpty()) {
            item { SectionTitle("Topic Explorer") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topics.forEach { t -> FilterChip(selected = selected == t.label, onClick = { vm.selectTopic(if (selected == t.label) null else t.label) }, label = { Text("${t.label} (${t.value})") }) }
                }
            }
        }
        if (selected != null) {
            if (related.isNotEmpty()) item { TraceonCard { SectionTitle("Often explored together with $selected"); HBarList(related) } }
            item { SectionTitle("Items in $selected") }
            items(items, key = { it.id }) { item -> ContentRowCard(item, onClick = { onOpenContent(item.id) }, whenText = TimeUtils.formatDateTime(item.lastSeen)) }
        }
    }
}

@Composable
private fun WebTab(vm: AnalyticsViewModel, onOpenContent: (Long) -> Unit) {
    val searches by vm.searches.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val domainCount by vm.domainCount.collectAsStateWithLifecycle()
    val domains by vm.domains.collectAsStateWithLifecycle()
    val browsers by vm.browsers.collectAsStateWithLifecycle()
    val engines by vm.engines.collectAsStateWithLifecycle()
    val browsing by vm.browsing.collectAsStateWithLifecycle()
    val repeated by vm.repeated.collectAsStateWithLifecycle()
    val recentPages by vm.recentPages.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    val searchTopics by vm.searchTopics.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    LazyColumn(contentPadding = tabPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (searches == 0 && pages == 0) item {
            EmptyState("No web activity yet", "Browser Intelligence is built from imported browser history, Google activity exports, shared links and the TRACEON browser. TRACEON cannot read other browsers' private history on its own.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Searches", searches.toString(), Modifier.weight(1f))
                StatTile("Pages", pages.toString(), Modifier.weight(1f))
                StatTile("Domains", domainCount.toString(), Modifier.weight(1f))
            }
        }
        if (searchTopics.isNotEmpty()) item { TraceonCard { SectionTitle("Most searched topics"); HBarList(searchTopics) } }
        if (domains.isNotEmpty()) item { TraceonCard { SectionTitle("Most visited domains"); HBarList(domains, format = { "$it visits" }, maxRows = 10) } }
        if (browsers.isNotEmpty()) item { TraceonCard { SectionTitle("Browser-wise activity"); HBarList(browsers.map { LabeledValue(label(it.label), it.value) }, format = { "$it visits" }) } }
        if (engines.isNotEmpty()) item { TraceonCard { SectionTitle("Search engine-wise activity"); HBarList(engines.map { LabeledValue(label(it.label), it.value) }, format = { "$it searches" }) } }
        if (browsing.isNotEmpty()) {
            item { TraceonCard { SectionTitle("Daily browsing - last 14 days"); val (v, l) = lastDays(browsing, today); BarChart(v, l) } }
            item { TraceonCard { SectionTitle("Monthly browsing - last 6 months"); val (v, l) = monthly(browsing, today); BarChart(v, l) } }
        }
        if (repeated.isNotEmpty()) item { TraceonCard { SectionTitle("Repeated website visits"); HBarList(repeated, format = { "$it visits" }) } }
        if (recentPages.isNotEmpty()) {
            item { SectionTitle("Recently visited pages") }
            itemsIndexed(recentPages, key = { i, e -> "p-$i-${e.item.id}-${e.eventTime}" }) { _, e -> ContentRowCard(e.item, onClick = { onOpenContent(e.item.id) }, whenText = TimeUtils.formatDateTime(e.eventTime)) }
        }
        if (recentSearches.isNotEmpty()) {
            item { SectionTitle("Recent searches") }
            itemsIndexed(recentSearches, key = { i, e -> "s-$i-${e.item.id}-${e.eventTime}" }) { _, e -> ContentRowCard(e.item, onClick = { onOpenContent(e.item.id) }, whenText = "${label(e.source ?: "")} - ${TimeUtils.formatDateTime(e.eventTime)}") }
        }
    }
}
