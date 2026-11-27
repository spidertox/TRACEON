@file:OptIn(ExperimentalLayoutApi::class)

package com.traceon.feature.importcenter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.traceon.core.common.SharedContent
import com.traceon.core.common.SharedContentParser
import com.traceon.core.ui.components.DropdownChip
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.core.ui.theme.TraceonTheme
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import com.traceon.domain.model.SaveResult
import com.traceon.domain.model.ThemeMode
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class ManualEntryViewModel(private val repo: ContentRepository, private val clock: Clock) : ViewModel() {
    val similar = MutableStateFlow<List<ContentItem>>(emptyList())
    val result = MutableStateFlow<SaveResult?>(null)
    private var pending: ContentDraft? = null

    fun draft(title: String, url: String, type: ContentType?, creator: String, description: String, tags: String, notes: String) = ContentDraft(
        title = title.trim(), url = url.trim().ifEmpty { null }, contentType = type, creator = creator.trim().ifEmpty { null },
        description = description.trim(), eventTime = clock.now(), collectionMethod = CollectionMethod.MANUAL, origin = DataStatus.MANUAL,
        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, notes = notes.trim(),
    )

    fun submit(d: ContentDraft) {
        viewModelScope.launch {
            if (d.url == null) {
                val found = repo.findSimilarTitles(d.title)
                if (found.isNotEmpty()) { pending = d; similar.value = found; return@launch }
            }
            result.value = repo.save(d)
        }
    }

    fun mergeInto(id: Long) { viewModelScope.launch { pending?.let { result.value = repo.mergeInto(id, it) }; similar.value = emptyList() } }
    fun keepSeparate() { viewModelScope.launch { pending?.let { result.value = repo.save(it) }; similar.value = emptyList() } }
    fun cancelMerge() { similar.value = emptyList(); pending = null }
}

@Composable
fun ManualEntryScreen(onBack: () -> Unit, vm: ManualEntryViewModel = koinViewModel()) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<ContentType?>(null) }
    var creator by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val similar by vm.similar.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(result) {
        result?.let {
            Toast.makeText(context, if (it.created) "Saved to your library" else "Added to the existing item", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    TraceonScaffold(title = "Add manually", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Things you enter here are labeled \"Entered manually\". Nothing is fetched from the network.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("URL (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            DropdownChip("Type: auto-detect", type?.label, listOf<Pair<String, ContentType?>>("Auto-detect" to null) + ContentType.values().map { it.label to it }, { type = it })
            OutlinedTextField(creator, { creator = it }, label = { Text("Creator (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button(onClick = { vm.submit(vm.draft(title, url, type, creator, description, tags, notes)) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
    if (similar.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::cancelMerge,
            title = { Text("Looks similar to something you saved") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TRACEON never merges items just because titles look alike. Choose what to do:", style = MaterialTheme.typography.bodyMedium)
                    similar.forEach { s -> TextButton(onClick = { vm.mergeInto(s.id) }) { Text("Merge into: ${s.title}", maxLines = 2) } }
                }
            },
            confirmButton = { TextButton(onClick = vm::keepSeparate) { Text("Keep separate") } },
            dismissButton = { TextButton(onClick = vm::cancelMerge) { Text("Cancel") } },
        )
    }
}

class ShareViewModel(private val repo: ContentRepository, settings: SettingsRepository, private val clock: Clock) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.themeMode().stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    val result = MutableStateFlow<SaveResult?>(null)

    fun save(title: String, url: String?, notes: String, tags: String) {
        viewModelScope.launch {
            result.value = repo.save(
                ContentDraft(
                    title = title.trim(), url = url, eventTime = clock.now(), collectionMethod = CollectionMethod.SHARE, origin = DataStatus.OBSERVED,
                    notes = notes.trim(), tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                ),
            )
        }
    }
}

/** Receives ACTION_SEND text. Shows exactly what will be stored and saves only after the user confirms. */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = SharedContentParser.parse(intent?.getStringExtra(Intent.EXTRA_TEXT), intent?.getStringExtra(Intent.EXTRA_SUBJECT))
        setContent { ShareRoot(shared, onClose = { finish() }) }
    }
}

@Composable
private fun ShareRoot(shared: SharedContent, onClose: () -> Unit, vm: ShareViewModel = koinViewModel()) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var title by remember { mutableStateOf(shared.title) }
    var notes by remember { mutableStateOf(shared.note) }
    var tags by remember { mutableStateOf("") }
    LaunchedEffect(result) {
        result?.let {
            Toast.makeText(context, if (it.created) "Saved to TRACEON" else "Already in TRACEON - recorded again", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    TraceonTheme(theme) {
        TraceonScaffold(title = "Save to TRACEON", onBack = onClose) { pad ->
            Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (shared.url == null && shared.title.isBlank()) {
                    Text("The sending app did not share any text or link, so there is nothing to save.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onClose) { Text("Close") }
                } else {
                    TraceonCard {
                        Text(shared.url ?: "No link in the shared text", style = MaterialTheme.typography.bodyMedium)
                        Text("Nothing is downloaded. The title comes only from what the sending app shared; leave it empty if there is none.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags, comma separated") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.save(title, shared.url, notes, tags) }, enabled = title.isNotBlank() || shared.url != null) { Text("Save") }
                        OutlinedButton(onClick = onClose) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
