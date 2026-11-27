package com.traceon.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.Clock
import com.traceon.core.common.RangePreset
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.ConfirmDialog
import com.traceon.core.ui.components.ContentRowCard
import com.traceon.core.ui.components.DateRangeDialog
import com.traceon.core.ui.components.DropdownChip
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.OverflowMenu
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.core.ui.components.openUrl
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.FilterOptions
import com.traceon.domain.model.TimelineEntry
import com.traceon.domain.model.TimelineFilter
import com.traceon.domain.repository.ContentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ActivityUi(
    val preset: RangePreset = RangePreset.LAST_7,
    val custom: Pair<Long, Long>? = null,
    val platform: String? = null,
    val entries: List<TimelineEntry> = emptyList(),
    val mayHaveMore: Boolean = false,
    val loaded: Boolean = false,
)

private data class Params(val preset: RangePreset, val custom: Pair<Long, Long>?, val platform: String?, val limit: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(private val repo: ContentRepository, private val clock: Clock) : ViewModel() {
    private val preset = MutableStateFlow(RangePreset.LAST_7)
    private val custom = MutableStateFlow<Pair<Long, Long>?>(null)
    private val platform = MutableStateFlow<String?>(null)
    private val limit = MutableStateFlow(PAGE)

    val options = MutableStateFlow(FilterOptions(emptyList(), emptyList(), emptyList(), emptyList()))

    val ui: StateFlow<ActivityUi> = combine(preset, custom, platform, limit) { p, c, pl, l -> Params(p, c, pl, l) }
        .flatMapLatest { prm ->
            val range = if (prm.preset == RangePreset.CUSTOM && prm.custom != null) prm.custom else TimeUtils.presetRange(prm.preset, clock.now())
            repo.observeTimeline(TimelineFilter(range.first, range.second, prm.platform), prm.limit)
                .map { ActivityUi(prm.preset, prm.custom, prm.platform, it, it.size >= prm.limit, true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUi())

    fun refreshOptions() { viewModelScope.launch { options.value = repo.filterOptions() } }
    fun setPreset(p: RangePreset) { preset.value = p; limit.value = PAGE }
    fun setCustom(from: Long, to: Long) { custom.value = from to to; preset.value = RangePreset.CUSTOM; limit.value = PAGE }
    fun setPlatform(slug: String?) { platform.value = slug; limit.value = PAGE }
    fun loadMore() { limit.value = limit.value + PAGE }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }

    companion object { const val PAGE = 100 }
}

@Composable
fun ActivityScreen(onOpenContent: (Long) -> Unit, vm: ActivityViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRange by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ContentItem?>(null) }
    LaunchedEffect(Unit) { vm.refreshOptions() }

    TraceonScaffold(title = "Activity", bottomBarPresent = true) { pad ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RangePreset.values().toList()) { p ->
                        FilterChip(
                            selected = ui.preset == p,
                            onClick = { if (p == RangePreset.CUSTOM) showRange = true else vm.setPreset(p) },
                            label = { Text(if (p == RangePreset.CUSTOM && ui.custom != null) "${TimeUtils.formatShort(ui.custom!!.first)} - ${TimeUtils.formatShort(ui.custom!!.second)}" else p.label) },
                        )
                    }
                }
            }
            item {
                DropdownChip(
                    label = "All platforms",
                    selectedText = options.platforms.firstOrNull { it.first == ui.platform }?.second,
                    options = listOf<Pair<String, String?>>("All platforms" to null) + options.platforms.map { it.second to it.first },
                    onSelect = { vm.setPlatform(it) },
                )
            }
            if (ui.loaded && ui.entries.isEmpty()) {
                item {
                    EmptyState(
                        "Nothing recorded in this period",
                        "Try a wider date range or another platform. Activity appears here after you import history, share links to TRACEON, add items, or browse in the TRACEON browser.",
                    )
                }
            }
            val now = System.currentTimeMillis()
            var previousDay: java.time.LocalDate? = null
            ui.entries.forEachIndexed { index, e ->
                val day = TimeUtils.toLocalDate(e.eventTime)
                if (day != previousDay) {
                    previousDay = day
                    item(key = "h-$day-$index") {
                        Text(TimeUtils.dayHeader(e.eventTime, now).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                item(key = "e-$index-${e.item.id}-${e.eventTime}") {
                    ContentRowCard(
                        e.item,
                        onClick = { onOpenContent(e.item.id) },
                        whenText = "${TimeUtils.formatTime(e.eventTime)} - ${e.method.label}",
                        durationMs = e.durationMs,
                        menu = {
                            OverflowMenu(
                                buildList {
                                    add("Open details & edit" to { onOpenContent(e.item.id) })
                                    e.item.url?.let { u -> add("Open original" to { openUrl(context, u) }) }
                                    add("Delete item" to { pendingDelete = e.item })
                                },
                            )
                        },
                    )
                }
            }
            if (ui.mayHaveMore) item { OutlinedButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
        }
    }
    if (showRange) DateRangeDialog(onDismiss = { showRange = false }, onConfirm = { f, t -> showRange = false; vm.setCustom(f, t) })
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete this item?",
            message = "\"${item.title}\" and all ${item.sessionCount} recorded events for it will be removed from TRACEON. The original content is not touched.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(item.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
