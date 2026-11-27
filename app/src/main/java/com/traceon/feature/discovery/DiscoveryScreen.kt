@file:OptIn(ExperimentalLayoutApi::class)

package com.traceon.feature.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.ContentRowCard
import com.traceon.core.ui.components.HBarList
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.usecase.DiscoveryAnswer
import com.traceon.domain.usecase.DiscoveryEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class DiscoveryUi(val history: List<Pair<String, DiscoveryAnswer>> = emptyList(), val busy: Boolean = false)

class DiscoveryViewModel(private val engine: DiscoveryEngine) : ViewModel() {
    private val _ui = MutableStateFlow(DiscoveryUi())
    val ui: StateFlow<DiscoveryUi> = _ui

    fun ask(question: String) {
        if (question.isBlank() || _ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val answer = engine.answer(question.trim())
            _ui.update { DiscoveryUi(listOf(question.trim() to answer) + it.history, false) }
        }
    }
}

private val EXAMPLES = listOf(
    "What AI topics did I explore?",
    "Which programming tutorials did I watch?",
    "What websites did I visit about Android?",
    "Show all content related to machine learning",
    "What did I watch last Sunday?",
    "Which topics have I explored most?",
    "Show connections between my searches and videos",
)

@Composable
fun DiscoveryScreen(onBack: () -> Unit, onOpenContent: (Long) -> Unit, vm: DiscoveryViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    TraceonScaffold(title = "Smart Discovery", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Ask about your own library. Answers are built only from records stored on this device, work offline, and list the records they rest on. Nothing is sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Your question") }, singleLine = true,
                    trailingIcon = { IconButton(onClick = { vm.ask(text); text = "" }, enabled = text.isNotBlank() && !ui.busy) { Icon(Icons.Default.Search, contentDescription = "Ask") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.ask(text); text = "" }),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXAMPLES.forEach { q -> SuggestionChip(onClick = { vm.ask(q) }, label = { Text(q) }) }
                }
            }
            if (ui.busy) item { Text("Searching your library...", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ui.history.forEachIndexed { index, (question, answer) ->
                item(key = "q-$index") {
                    TraceonCard {
                        Text(question, style = MaterialTheme.typography.titleMedium)
                        if (answer.interpretation.isNotBlank()) Text("Interpreted as: ${answer.interpretation}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(answer.summary, style = MaterialTheme.typography.bodyMedium)
                        if (answer.topics.isNotEmpty()) HBarList(answer.topics)
                    }
                }
                items(answer.pairs, key = { "p-$index-${it.first.id}-${it.second.id}" }) { (search, other) ->
                    TraceonCard {
                        SectionTitle("Search")
                        Text(search.title, style = MaterialTheme.typography.titleMedium)
                        Text("matches", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ContentRowCard(other, onClick = { onOpenContent(other.id) }, whenText = TimeUtils.formatDateTime(other.lastSeen))
                    }
                }
                items(answer.records, key = { "r-$index-${it.id}" }) { item ->
                    ContentRowCard(item, onClick = { onOpenContent(item.id) }, whenText = TimeUtils.formatDateTime(item.lastSeen))
                }
            }
        }
    }
}
