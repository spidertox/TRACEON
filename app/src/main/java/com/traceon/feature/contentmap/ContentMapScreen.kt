@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalCoroutinesApi::class)

package com.traceon.feature.contentmap

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.Clock
import com.traceon.core.common.PlatformCatalog
import com.traceon.core.common.TimeUtils
import com.traceon.core.graph.GraphBuilder
import com.traceon.core.graph.GraphExport
import com.traceon.core.graph.GraphSnapshot
import com.traceon.core.graph.GraphState
import com.traceon.core.graph.NodeKind
import com.traceon.core.graph.Pt
import com.traceon.core.graph.RadialLayout
import com.traceon.core.ui.components.DropdownChip
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.OverflowMenu
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.FilterOptions
import com.traceon.domain.model.GraphFilter
import com.traceon.domain.model.GraphItem
import com.traceon.domain.model.RelationEdge
import com.traceon.domain.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

data class GraphUi(
    val snapshot: GraphSnapshot = GraphSnapshot(emptyList(), emptyList()),
    val positions: Map<String, Pt> = emptyMap(),
    val selected: Set<String> = emptySet(),
    val highlight: Set<String> = emptySet(),
    val itemCount: Int = 0,
    val truncated: Boolean = false,
    val loaded: Boolean = false,
)

class ContentMapViewModel(private val repo: ContentRepository, private val clock: Clock) : ViewModel() {
    private val builder = GraphBuilder()
    private val _filter = MutableStateFlow(GraphFilter())
    private val graphState = MutableStateFlow(GraphState())
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    @Volatile private var latestItems: List<GraphItem> = emptyList()

    val filter: StateFlow<GraphFilter> = _filter
    val options = MutableStateFlow(FilterOptions(emptyList(), emptyList(), emptyList(), emptyList()))
    val fitKey = MutableStateFlow(0)
    val focus = MutableStateFlow<String?>(null)

    private val items = _filter.flatMapLatest { repo.observeGraphItems(it, MAX_ITEMS) }
    private val relations = repo.observeRelationEdges()

    val ui: StateFlow<GraphUi> = combine(items, relations, graphState, selected) { its, rels, gs, sel ->
        latestItems = its
        val snap = builder.build(its, rels, gs)
        val ids = snap.nodes.map { it.id }.toHashSet()
        val sel2 = sel.filter { it in ids }.toSet()
        GraphUi(snap, RadialLayout.compute(snap), sel2, neighbours(snap, rels, sel2), its.size, its.size >= MAX_ITEMS, true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphUi())

    private fun neighbours(snap: GraphSnapshot, rels: List<RelationEdge>, sel: Set<String>): Set<String> {
        if (sel.isEmpty()) return emptySet()
        val out = HashSet(sel)
        for (e in snap.edges) {
            if (e.from in sel) out += e.to
            if (e.to in sel) out += e.from
        }
        return out
    }

    fun refreshOptions() { viewModelScope.launch { options.value = repo.filterOptions() } }
    fun setFilter(f: GraphFilter) { _filter.value = f }

    fun tap(id: String) {
        val node = ui.value.snapshot.byId[id] ?: return
        when (node.kind) {
            NodeKind.MORE -> {
                val parent = node.parentId ?: return
                graphState.update { it.copy(extraPages = it.extraPages + (parent to ((it.extraPages[parent] ?: 0) + 1))) }
            }
            NodeKind.CONTENT -> selected.value = setOf(id)
            else -> {
                selected.value = setOf(id)
                graphState.update { it.copy(expanded = if (id in it.expanded) it.expanded - id else it.expanded + id) }
            }
        }
    }

    fun longPress(id: String) { selected.update { if (id in it) it - id else it + id } }
    fun clearSelection() { selected.value = emptySet() }
    fun collapseAll() { graphState.value = GraphState(); selected.value = emptySet(); fitKey.update { it + 1 } }
    fun fit() { fitKey.update { it + 1 } }
    fun consumeFocus() { focus.value = null }

    fun focusContent(contentId: Long) {
        viewModelScope.launch {
            _filter.value = GraphFilter()
            val all = repo.observeGraphItems(GraphFilter(), MAX_ITEMS).first()
            graphState.value = builder.reveal(all, contentId, graphState.value)
            val nodeId = builder.nodeIdForContent(contentId)
            selected.value = setOf(nodeId)
            focus.value = nodeId
        }
    }

    fun revealRelated(contentId: Long) {
        viewModelScope.launch {
            val related = repo.observeRelated(contentId).first()
            var gs = graphState.value
            related.forEach { gs = builder.reveal(latestItems, it.item.id, gs) }
            graphState.value = gs
        }
    }

    fun linkSelected() {
        val ids = selected.value.mapNotNull { ui.value.snapshot.byId[it]?.contentId }
        if (ids.size == 2) viewModelScope.launch { repo.linkItems(ids[0], ids[1]) }
    }

    fun searchNodes(q: String): List<GraphItem> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return latestItems.filter {
            it.title.lowercase().contains(needle) || (it.category?.lowercase()?.contains(needle) == true) ||
                (it.creator?.lowercase()?.contains(needle) == true) || PlatformCatalog.nameOf(it.platformSlug).lowercase().contains(needle)
        }.take(8)
    }

    fun itemInfo(contentId: Long): GraphItem? = latestItems.firstOrNull { it.id == contentId }
    fun exportJson(): String = ui.value.let { GraphExport.toJson(it.snapshot, it.positions) }
    fun exportBitmap(): Bitmap = ui.value.let { GraphExport.toBitmap(it.snapshot, it.positions) }
    fun daysAgo(days: Int): Long = clock.now() - days * TimeUtils.DAY_MS

    companion object { const val MAX_ITEMS = 5000 }
}

private suspend fun writeTo(context: Context, uri: Uri, write: (java.io.OutputStream) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { write(it); true } ?: false
    } catch (e: Exception) {
        false
    }
}

@Composable
fun ContentMapScreen(focusId: Long?, onOpenContent: (Long) -> Unit, vm: ContentMapViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val fitKey by vm.fitKey.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { vm.refreshOptions() }
    LaunchedEffect(focusId) { if (focusId != null && focusId > 0) vm.focusContent(focusId) }

    val jsonLauncher = rememberLauncherForActivityResult(remember { ActivityResultContracts.CreateDocument("application/json") }) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportJson()
            val ok = writeTo(context, uri) { it.write(text.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, if (ok) "Graph exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val pngLauncher = rememberLauncherForActivityResult(remember { ActivityResultContracts.CreateDocument("image/png") }) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.Default) { vm.exportBitmap() }
            val ok = writeTo(context, uri) { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(context, if (ok) "Image exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    TraceonScaffold(
        title = "Content Map", bottomBarPresent = true,
        actions = {
            IconButton(onClick = vm::fit) { Icon(Icons.Default.Refresh, contentDescription = "Fit graph to screen") }
            IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.Settings, contentDescription = "Filters") }
            OverflowMenu(
                listOf(
                    "Collapse all" to { vm.collapseAll() },
                    "Export as JSON" to { jsonLauncher.launch("traceon-graph.json") },
                    "Export as image" to { pngLauncher.launch("traceon-graph.png") },
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
            if (ui.loaded && ui.itemCount == 0) {
                EmptyState(
                    "Your map has nothing to show yet",
                    if (filter != GraphFilter()) "No stored item matches the current filters." else "The map is drawn from items TRACEON has actually recorded. Import history, share links to TRACEON, or add items; then platforms, types and topics appear here.",
                    actionLabel = if (filter != GraphFilter()) "Clear filters" else null,
                    onAction = if (filter != GraphFilter()) ({ vm.setFilter(GraphFilter()); days = 0 }) else null,
                )
            } else {
                GraphCanvas(
                    snapshot = ui.snapshot, positions = ui.positions, selected = ui.selected, highlight = ui.highlight,
                    fitKey = fitKey, focusId = focus, onFocusConsumed = vm::consumeFocus,
                    onTap = vm::tap, onLongPress = vm::longPress, onBackgroundTap = vm::clearSelection,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp)) {
                    val matches = remember(query, ui.itemCount) { vm.searchNodes(query) }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Find a node") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                    if (matches.isNotEmpty()) {
                        TraceonCard {
                            matches.forEach { m ->
                                TextButton(onClick = { vm.focusContent(m.id); query = "" }, modifier = Modifier.fillMaxWidth()) { Text(m.title, maxLines = 1) }
                            }
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                    if (ui.truncated) Text("Showing the ${ContentMapViewModel.MAX_ITEMS} most recent matching items.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ui.selected.isNotEmpty()) SelectionPanel(ui, vm, onOpenContent)
                    else Text("Tap a node to open it, long-press to multi-select, pinch to zoom, drag to pan.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Filter the map", style = MaterialTheme.typography.titleMedium)
                Text("Platform", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.platforms.forEach { (slug, name) -> FilterChip(selected = filter.platform == slug, onClick = { vm.setFilter(filter.copy(platform = if (filter.platform == slug) null else slug)) }, label = { Text(name) }) }
                }
                Text("Content type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.types.forEach { t: ContentType -> FilterChip(selected = filter.type == t, onClick = { vm.setFilter(filter.copy(type = if (filter.type == t) null else t)) }, label = { Text(t.plural) }) }
                }
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.categories.forEach { c -> FilterChip(selected = filter.category == c, onClick = { vm.setFilter(filter.copy(category = if (filter.category == c) null else c)) }, label = { Text(c) }) }
                }
                Text("Date", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "All time", 7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, label) ->
                        FilterChip(selected = days == d, onClick = { days = d; vm.setFilter(filter.copy(from = if (d == 0) null else vm.daysAgo(d))) }, label = { Text(label) })
                    }
                }
                DropdownChip(
                    "Creator", filter.creator,
                    listOf<Pair<String, String?>>("Any creator" to null) + options.creators.map { it to it },
                    { vm.setFilter(filter.copy(creator = it)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setFilter(GraphFilter()); days = 0 }) { Text("Reset") }
                    Button(onClick = { showFilters = false }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SelectionPanel(ui: GraphUi, vm: ContentMapViewModel, onOpenContent: (Long) -> Unit) {
    val nodes = ui.selected.mapNotNull { ui.snapshot.byId[it] }
    TraceonCard {
        if (nodes.size == 1) {
            val n = nodes.first()
            Text(n.label, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val info = n.contentId?.let { vm.itemInfo(it) }
            if (n.contentId != null && info != null) {
                Text(
                    listOfNotNull(PlatformCatalog.nameOf(info.platformSlug), info.contentType.label, info.category, info.creator, "last seen ${TimeUtils.formatDateTime(info.lastSeen)}").joinToString(" - "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenContent(n.contentId) }) { Text("Open details") }
                    OutlinedButton(onClick = { vm.revealRelated(n.contentId) }) { Text("Show related") }
                }
            } else {
                Text("${n.count} item${if (n.count == 1) "" else "s"} under ${n.kind.name.lowercase()}. Tap again to ${if (n.expanded) "collapse" else "expand"}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("${nodes.size} nodes selected", style = MaterialTheme.typography.titleMedium)
            nodes.take(4).forEach { Text(it.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nodes.count { it.contentId != null } == 2) Button(onClick = vm::linkSelected) { Text("Link these two") }
                OutlinedButton(onClick = vm::clearSelection) { Text("Clear selection") }
            }
        }
    }
}
