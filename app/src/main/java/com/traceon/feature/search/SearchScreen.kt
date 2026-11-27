@file:OptIn(ExperimentalLayoutApi::class, FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.traceon.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.Clock
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.ContentRowCard
import com.traceon.core.ui.components.DropdownChip
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.FilterOptions
import com.traceon.domain.model.SearchFilters
import com.traceon.domain.model.SearchSort
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class SearchUi(val results: List<ContentItem> = emptyList(), val searched: Boolean = false)

class SearchViewModel(private val repo: ContentRepository, private val settings: SettingsRepository, private val clock: Clock) : ViewModel() {
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(SearchFilters())
    val options = MutableStateFlow(FilterOptions(emptyList(), emptyList(), emptyList(), emptyList()))
    val dateDays = MutableStateFlow<Int?>(null)

    val results: StateFlow<SearchUi> = combine(query.debounce(200), filters, dateDays) { q, f, d -> Triple(q, f, d) }
        .flatMapLatest { (q, f, d) ->
            flow {
                val active = q.isNotBlank() || f != SearchFilters() || d != null
                if (!active) emit(SearchUi()) else {
                    val withDate = if (d != null) f.copy(from = clock.now() - d * TimeUtils.DAY_MS, to = clock.now()) else f
                    emit(SearchUi(repo.search(q, withDate, 200), true))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    val suggestions: StateFlow<List<String>> = query.debounce(150)
        .flatMapLatest { q -> flow { emit(repo.suggestions(q)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents = settings.recentSearches(8).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saved = settings.savedSearches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshOptions() { viewModelScope.launch { options.value = repo.filterOptions() } }
    fun submit() { val q = query.value.trim(); if (q.isNotEmpty()) viewModelScope.launch { settings.rememberSearch(q) } }
    fun saveCurrent() { val q = query.value.trim(); if (q.isNotEmpty()) viewModelScope.launch { settings.setSearchSaved(q, true) } }
    fun unsave(q: String) { viewModelScope.launch { settings.setSearchSaved(q, false) } }
    fun clearRecents() { viewModelScope.launch { settings.clearRecentSearches() } }
    fun update(f: (SearchFilters) -> SearchFilters) { filters.value = f(filters.value) }
}

@Composable
fun SearchScreen(onOpenContent: (Long) -> Unit, vm: SearchViewModel = koinViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val f by vm.filters.collectAsStateWithLifecycle()
    val days by vm.dateDays.collectAsStateWithLifecycle()
    val ui by vm.results.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshOptions() }

    TraceonScaffold(title = "Search", bottomBarPresent = true) { pad ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { vm.query.value = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Search titles, notes, tags, topics, creators, domains") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (q.isNotEmpty()) Row {
                            IconButton(onClick = vm::saveCurrent) { Icon(Icons.Default.Star, contentDescription = "Save this search") }
                            IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        DropdownChip("Platform", options.platforms.firstOrNull { it.first == f.platform }?.second,
                            listOf<Pair<String, String?>>("Any platform" to null) + options.platforms.map { it.second to it.first }, { s -> vm.update { it.copy(platform = s) } })
                    }
                    item {
                        DropdownChip("Type", f.types.firstOrNull()?.label,
                            listOf<Pair<String, ContentType?>>("Any type" to null) + options.types.map { it.label to it }, { t -> vm.update { it.copy(types = if (t == null) emptySet() else setOf(t)) } })
                    }
                    item {
                        DropdownChip("Category", f.category,
                            listOf<Pair<String, String?>>("Any category" to null) + options.categories.map { it to it }, { c -> vm.update { it.copy(category = c) } })
                    }
                    item {
                        DropdownChip("Date", days?.let { "Last $it days" },
                            listOf<Pair<String, Int?>>("Any time" to null, "Last 7 days" to 7, "Last 30 days" to 30, "Last 90 days" to 90, "Last year" to 365), { d -> vm.dateDays.value = d })
                    }
                    item {
                        DropdownChip("Sort", if (f.sort == SearchSort.RELEVANCE) null else f.sort.label,
                            SearchSort.values().map { it.label to it }, { s -> vm.update { it.copy(sort = s ?: SearchSort.RELEVANCE) } })
                    }
                }
            }
            if (q.isNotBlank() && suggestions.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s -> SuggestionChip(onClick = { vm.query.value = s }, label = { Text(s, maxLines = 1) }) }
                    }
                }
            }
            if (!ui.searched) {
                if (saved.isNotEmpty()) {
                    item { SectionTitle("Saved searches") }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            saved.forEach { s -> AssistChip(onClick = { vm.query.value = s }, label = { Text(s) }, trailingIcon = { IconButton(onClick = { vm.unsave(s) }) { Icon(Icons.Default.Clear, contentDescription = "Remove saved search") } }) }
                        }
                    }
                }
                if (recents.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { SectionTitle("Recent searches"); TextButton(onClick = vm::clearRecents) { Text("Clear") } } }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            recents.forEach { s -> SuggestionChip(onClick = { vm.query.value = s }, label = { Text(s) }) }
                        }
                    }
                }
                item {
                    EmptyState("Search your digital memory", "Everything is searched on this device, instantly: reels, shorts, videos, articles, websites, searches, tutorials, topics, creators, domains, tags and notes.")
                }
            } else if (ui.results.isEmpty()) {
                item { EmptyState("No stored record matches", "Nothing in your library matches this query and these filters. Remove a filter or import more history.") }
            } else {
                item { Text("${ui.results.size}${if (ui.results.size >= 200) "+" else ""} result${if (ui.results.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                items(ui.results, key = { it.id }) { item ->
                    ContentRowCard(item, onClick = { vm.submit(); onOpenContent(item.id) }, whenText = TimeUtils.formatDateTime(item.lastSeen))
                }
            }
        }
    }
}
