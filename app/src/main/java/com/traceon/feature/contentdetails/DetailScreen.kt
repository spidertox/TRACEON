@file:OptIn(ExperimentalLayoutApi::class)

package com.traceon.feature.contentdetails

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.components.ConfidenceBar
import com.traceon.core.ui.components.ConfirmDialog
import com.traceon.core.ui.components.ContentRowCard
import com.traceon.core.ui.components.EmptyState
import com.traceon.core.ui.components.InfoChip
import com.traceon.core.ui.components.KeyValueRow
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.StatusBadge
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.core.ui.components.openUrl
import com.traceon.domain.model.ContentEvent
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.DataStatus
import com.traceon.domain.model.RelatedItem
import com.traceon.domain.model.RelationType
import com.traceon.domain.model.TopicScore
import com.traceon.domain.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

sealed interface ItemState {
    data object Loading : ItemState
    data object Missing : ItemState
    data class Found(val item: ContentItem) : ItemState
}

class DetailViewModel(private val id: Long, private val repo: ContentRepository) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.hold(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val item = repo.observeItem(id).map { if (it == null) ItemState.Missing else ItemState.Found(it) }.hold<ItemState>(ItemState.Loading)
    val events = repo.observeEvents(id).hold(emptyList<ContentEvent>())
    val tags = repo.observeTags(id).hold(emptyList<String>())
    val topics = repo.observeTopics(id).hold(emptyList<TopicScore>())
    val related = repo.observeRelated(id).hold(emptyList<RelatedItem>())
    val categories = repo.observeCategories().hold(emptyList<String>())

    fun saveDetails(title: String, description: String, notes: String) { viewModelScope.launch { repo.updateDetails(id, title, description, notes) } }
    fun setCategory(name: String?) { viewModelScope.launch { repo.setCategory(id, name) } }
    fun addTag(tag: String) { viewModelScope.launch { repo.addTag(id, tag) } }
    fun removeTag(tag: String) { viewModelScope.launch { repo.removeTag(id, tag) } }
    fun unlink(otherId: Long) { viewModelScope.launch { repo.unlinkItems(id, otherId) } }
    fun delete(done: () -> Unit) { viewModelScope.launch { repo.delete(id); done() } }
}

@Composable
fun DetailScreen(id: Long, onBack: () -> Unit, onOpenContent: (Long) -> Unit, onShowInMap: (Long) -> Unit) {
    val vm: DetailViewModel = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.item.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val topics by vm.topics.collectAsStateWithLifecycle()
    val related by vm.related.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }

    TraceonScaffold(title = "Content", onBack = onBack) { pad ->
        when (val st = state) {
            ItemState.Loading -> Text("Loading...", Modifier.padding(pad).padding(16.dp))
            ItemState.Missing -> EmptyState("This item no longer exists", "It was deleted or removed with demo/all data.", Modifier.padding(pad), actionLabel = "Go back", onAction = onBack)
            is ItemState.Found -> {
                val item = st.item
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(item.title, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.platformName?.let { InfoChip(it, color = com.traceon.core.ui.theme.Brand.Cyan) }
                            InfoChip(item.contentType.label, color = com.traceon.core.ui.theme.Brand.Indigo)
                            StatusBadge(item.dataStatus)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isWebUrl(item.url)) Button(onClick = { openUrl(context, item.url.orEmpty()) }) { Text("Open original") }
                            OutlinedButton(onClick = { dialog = "edit" }) { Text("Edit") }
                            OutlinedButton(onClick = { onShowInMap(item.id) }) { Text("Show in map") }
                        }
                    }
                    item {
                        TraceonCard {
                            SectionTitle("Details")
                            KeyValueRow("Creator", item.creator ?: "Unknown")
                            KeyValueRow("Domain", item.domain ?: "n/a")
                            KeyValueRow("URL", item.url ?: "No URL stored")
                            KeyValueRow("First seen", TimeUtils.formatDateTime(item.firstSeen))
                            KeyValueRow("Last seen", TimeUtils.formatDateTime(item.lastSeen))
                            KeyValueRow("Sessions / events", item.sessionCount.toString())
                            KeyValueRow("Viewing time", TimeUtils.formatDuration(item.viewingDurationMs))
                            KeyValueRow("Reading time", TimeUtils.formatDuration(item.readingDurationMs))
                            KeyValueRow("First collected via", item.collectionMethod.label)
                            if (item.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        TraceonCard {
                            SectionTitle("Category & topics")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.category ?: "Unclassified", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { dialog = "category" }) { Text("Change") }
                            }
                            Text(
                                when {
                                    item.categoryLocked -> "Set by you."
                                    item.category == null -> "No category: the available metadata did not contain enough evidence."
                                    else -> "Inferred offline from title, description, tags and domain. Confidence ${(item.categoryConfidence * 100).toInt()}%."
                                },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (topics.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    topics.forEach { InfoChip(if (it.userSet) "${it.name} (you)" else "${it.name} ${(it.confidence * 100).toInt()}%") }
                                }
                            }
                            if (item.keywords.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Keywords: ${item.keywords.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        TraceonCard {
                            SectionTitle("Data quality")
                            Text("Metadata completeness ${(item.metadataConfidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            ConfidenceBar(item.metadataConfidence)
                            Spacer(Modifier.height(8.dp))
                            Text(provenance(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        TraceonCard {
                            SectionTitle("Tags")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                tags.forEach { t ->
                                    AssistChip(onClick = { vm.removeTag(t) }, label = { Text(t) }, trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Remove tag $t") })
                                }
                                AssistChip(onClick = { dialog = "tag" }, label = { Text("+ Add tag") })
                            }
                        }
                    }
                    item {
                        TraceonCard {
                            SectionTitle("Notes")
                            Text(item.notes.ifBlank { "No notes yet." }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (related.isNotEmpty()) {
                        item { SectionTitle("Graph connections") }
                        items(related, key = { "r-${it.item.id}-${it.type}" }) { r ->
                            ContentRowCard(
                                r.item, onClick = { onOpenContent(r.item.id) },
                                whenText = if (r.type == RelationType.USER) r.type.label else "${r.type.label} ${(r.score * 100).toInt()}%",
                            )
                        }
                    }
                    item {
                        SectionTitle("Recorded events (${events.size})")
                        TraceonCard {
                            if (events.isEmpty()) Text("No events.", style = MaterialTheme.typography.bodyMedium)
                            events.take(25).forEach { e ->
                                KeyValueRow(TimeUtils.formatDateTime(e.time), listOfNotNull(e.method.label, e.source, e.durationMs?.let { TimeUtils.formatDuration(it) }).joinToString(" - "))
                            }
                            if (events.size > 25) Text("+ ${events.size - 25} older events", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item { OutlinedButton(onClick = { dialog = "delete" }, modifier = Modifier.fillMaxWidth()) { Text("Delete this item") } }
                }

                when (dialog) {
                    "edit" -> EditDialog(item, onDismiss = { dialog = null }, onSave = { t, d, n -> vm.saveDetails(t, d, n); dialog = null })
                    "category" -> CategoryDialog(categories, onDismiss = { dialog = null }, onPick = { vm.setCategory(it); dialog = null })
                    "tag" -> TextPromptDialog("Add tag", "Tag", onDismiss = { dialog = null }, onConfirm = { vm.addTag(it); dialog = null })
                    "delete" -> ConfirmDialog(
                        "Delete this item?", "It and its ${item.sessionCount} recorded events are removed from TRACEON only.", "Delete",
                        onConfirm = { dialog = null; vm.delete(onBack) }, onDismiss = { dialog = null },
                    )
                    else -> Unit
                }
            }
        }
    }
}

private fun isWebUrl(u: String?) = u != null && (u.startsWith("http://", true) || u.startsWith("https://", true))

private fun provenance(item: ContentItem): String = when (item.dataStatus) {
    DataStatus.OBSERVED -> "Recorded on this device while you used it."
    DataStatus.IMPORTED -> "Imported from a file you provided. TRACEON did not observe this itself."
    DataStatus.MANUAL -> "You entered this yourself."
    DataStatus.INFERRED -> "The content type was inferred from the URL pattern or domain, so treat it as a best guess."
    DataStatus.UNAVAILABLE -> "The source did not provide a title or metadata, so only the URL is shown."
    DataStatus.SAMPLE -> "Demo sample, not real activity."
}

@Composable
private fun EditDialog(item: ContentItem, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var description by remember { mutableStateOf(item.description) }
    var notes by remember { mutableStateOf(item.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description, notes) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryDialog(categories: List<String>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set category") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your choice is kept even if classification is re-run.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onPick(null) }) { Text("Reset to automatic") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c -> AssistChip(onClick = { onPick(c) }, label = { Text(c) }) }
                }
                OutlinedTextField(custom, { custom = it.take(40) }, label = { Text("Or create a custom category") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onPick(custom.trim()) }))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) { Text("Use custom") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextPromptDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
