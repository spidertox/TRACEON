package com.traceon.feature.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.ui.components.SectionTitle
import com.traceon.core.ui.components.TraceonCard
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.domain.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BackupUi(
    val busy: Boolean = false,
    val message: String? = null,
    /** Set when bytes are ready and the system file picker should be opened with this suggested name. */
    val saveAs: String? = null,
    val saveMime: String = "application/octet-stream",
    val askPassphrase: Boolean = false,
)

class BackupViewModel(private val resolver: ContentResolver, private val repo: BackupRepository) : ViewModel() {
    val ui = MutableStateFlow(BackupUi())
    private var pendingBytes: ByteArray? = null
    private var pendingRestore: ByteArray? = null
    private fun stamp() = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now())

    fun prepareBackup(passphrase: String) {
        viewModelScope.launch {
            ui.value = BackupUi(busy = true)
            try {
                pendingBytes = repo.export(passphrase.ifEmpty { null })
                ui.value = BackupUi(saveAs = "traceon-backup-${stamp()}.${if (passphrase.isEmpty()) "json" else "tcbackup"}", saveMime = if (passphrase.isEmpty()) "application/json" else "application/octet-stream")
            } catch (e: Exception) {
                ui.value = BackupUi(message = "Backup failed: ${e.message}")
            }
        }
    }

    fun prepareCsv() {
        viewModelScope.launch {
            ui.value = BackupUi(busy = true)
            try {
                pendingBytes = repo.exportCsv().toByteArray(Charsets.UTF_8)
                ui.value = BackupUi(saveAs = "traceon-history-${stamp()}.csv", saveMime = "text/csv")
            } catch (e: Exception) {
                ui.value = BackupUi(message = "Export failed: ${e.message}")
            }
        }
    }

    fun consumeSaveRequest() { ui.update { it.copy(saveAs = null) } }

    fun writePending(uri: Uri) {
        viewModelScope.launch {
            val bytes = pendingBytes
            val ok = withContext(Dispatchers.IO) {
                try { bytes != null && resolver.openOutputStream(uri)?.use { it.write(bytes); true } == true } catch (e: Exception) { false }
            }
            pendingBytes = null
            ui.value = BackupUi(message = if (ok) "File saved. Keep it somewhere safe; TRACEON never uploads it." else "The file could not be written.")
        }
    }

    fun readForRestore(uri: Uri) {
        viewModelScope.launch {
            ui.value = BackupUi(busy = true)
            val bytes = try {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { s ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = s.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > 200L * 1024 * 1024) error("This file is larger than 200 MB.")
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    }
                }
            } catch (e: Exception) {
                ui.value = BackupUi(message = "Could not read the file: ${e.message}")
                return@launch
            }
            if (bytes == null) { ui.value = BackupUi(message = "Could not open the file."); return@launch }
            if (repo.isEncrypted(bytes)) {
                pendingRestore = bytes
                ui.value = BackupUi(askPassphrase = true)
            } else restore(bytes, null)
        }
    }

    fun restoreEncrypted(passphrase: String) {
        val bytes = pendingRestore ?: return
        viewModelScope.launch { restore(bytes, passphrase) }
    }

    fun cancelRestore() { pendingRestore = null; ui.value = BackupUi() }

    private suspend fun restore(bytes: ByteArray, passphrase: String?) {
        ui.value = BackupUi(busy = true)
        try {
            val r = repo.restore(bytes, passphrase)
            pendingRestore = null
            ui.value = BackupUi(message = "Restored ${r.items} items (${r.newItems} new, the rest were already present) and ${r.appSessions} app sessions.")
        } catch (e: IllegalArgumentException) {
            ui.value = if (pendingRestore != null) BackupUi(askPassphrase = true, message = e.message) else BackupUi(message = e.message)
        } catch (e: Exception) {
            ui.value = BackupUi(message = "Restore failed: ${e.message}")
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var passphrase by remember { mutableStateOf("") }
    var restorePass by remember { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(remember(ui.saveMime) { ActivityResultContracts.CreateDocument(ui.saveMime) }) { uri -> if (uri != null) vm.writePending(uri) }
    val opener = rememberLauncherForActivityResult(remember { ActivityResultContracts.OpenDocument() }) { uri -> if (uri != null) vm.readForRestore(uri) }
    LaunchedEffect(ui.saveAs) { ui.saveAs?.let { saver.launch(it); vm.consumeSaveRequest() } }

    TraceonScaffold(title = "Backup & Restore", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.message?.let { TraceonCard { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            TraceonCard {
                SectionTitle("Create a backup")
                Text("Writes your items, tags, notes, events and app sessions to a file you choose. Demo data is never included. Add a passphrase to encrypt it (AES-256-GCM); without the passphrase an encrypted backup cannot be opened by anyone, including TRACEON.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase (optional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.prepareBackup(passphrase) }, enabled = !ui.busy) { Text(if (passphrase.isEmpty()) "Create backup" else "Create encrypted backup") }
            }
            TraceonCard {
                SectionTitle("Restore")
                Text("Merges a backup into your library. Items already present are not duplicated.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { opener.launch(arrayOf("*/*")) }, enabled = !ui.busy) { Text("Choose backup file") }
            }
            TraceonCard {
                SectionTitle("Export")
                Text("A spreadsheet-friendly CSV of your history (demo data excluded).", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = vm::prepareCsv, enabled = !ui.busy) { Text("Export CSV") }
            }
        }
    }
    if (ui.askPassphrase) {
        AlertDialog(
            onDismissRequest = { vm.cancelRestore(); restorePass = "" },
            title = { Text("Encrypted backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(restorePass, { restorePass = it }, label = { Text("Passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton(onClick = { vm.restoreEncrypted(restorePass); restorePass = "" }, enabled = restorePass.isNotEmpty()) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { vm.cancelRestore(); restorePass = "" }) { Text("Cancel") } },
        )
    }
}
