package com.traceon.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.traceon.core.common.Clock
import com.traceon.core.common.SearchEngines
import com.traceon.core.ui.components.TraceonScaffold
import com.traceon.core.ui.theme.Brand
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.DataStatus
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.androidx.compose.koinViewModel

/**
 * Records visits made inside TRACEON's own browser only. Nothing is recorded in Private mode or while tracking is off.
 * Time on page counts only while the screen is in the foreground.
 */
class BrowserViewModel(private val repo: ContentRepository, settings: SettingsRepository, private val clock: Clock) : ViewModel() {
    val privateMode = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = settings.sourceStates().map { it[CollectionMethod.TRACEON_BROWSER] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private class Visit(val contentId: Long, val visitTime: Long, val url: String, var accumulated: Long, var activeSince: Long?)

    private val lock = Mutex()
    private var current: Visit? = null

    fun onPageFinished(url: String?, title: String?, description: String?) {
        if (url.isNullOrBlank()) return
        viewModelScope.launch {
            lock.withLock {
                val now = clock.now()
                val existing = current
                if (existing != null && existing.url == url && now - existing.visitTime < 5_000) return@withLock
                persist(now)
                current = null
                if (privateMode.value || !recording.value) return@withLock
                val search = SearchEngines.match(url)
                val draft = if (search != null) ContentDraft(
                    title = search.query, url = url, eventTime = now, collectionMethod = CollectionMethod.TRACEON_BROWSER, origin = DataStatus.OBSERVED,
                    browserSlug = "traceon_browser", searchQuery = search.query, engineSlug = search.engineSlug,
                ) else ContentDraft(
                    title = title.orEmpty(), url = url, description = description.orEmpty(), eventTime = now,
                    collectionMethod = CollectionMethod.TRACEON_BROWSER, origin = DataStatus.OBSERVED, browserSlug = "traceon_browser",
                )
                val saved = repo.save(draft)
                if (search == null) current = Visit(saved.contentId, now, url, 0L, now)
            }
        }
    }

    fun onPause() { viewModelScope.launch { lock.withLock { val now = clock.now(); current?.let { v -> v.activeSince?.let { v.accumulated += now - it }; v.activeSince = null }; persist(now) } } }
    fun onResume() { viewModelScope.launch { lock.withLock { current?.activeSince = clock.now() } } }
    fun onLeave() { viewModelScope.launch { lock.withLock { val now = clock.now(); current?.let { v -> v.activeSince?.let { v.accumulated += now - it }; v.activeSince = null }; persist(now); current = null } } }

    private suspend fun persist(now: Long) {
        val v = current ?: return
        val total = v.accumulated + (v.activeSince?.let { now - it } ?: 0L)
        if (total > 0) repo.setBrowserDuration(v.contentId, v.visitTime, total)
    }

    fun setPrivate(on: Boolean) { privateMode.value = on }
}

private fun normalise(input: String): String {
    val t = input.trim()
    if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
    return if (!t.contains(' ') && t.contains('.')) "https://$t" else "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
}

private fun decodeJsString(raw: String?): String = try {
    if (raw == null || raw == "null") "" else (org.json.JSONTokener(raw).nextValue() as? String).orEmpty()
} catch (e: Exception) {
    ""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onBack: () -> Unit, vm: BrowserViewModel = koinViewModel()) {
    val priv by vm.privateMode.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    var web by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { vm.onPause(); web?.onPause() }
                Lifecycle.Event.ON_RESUME -> { vm.onResume(); web?.onResume() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.onLeave()
            if (vm.privateMode.value) { CookieManager.getInstance().removeAllCookies(null); web?.clearCache(true); web?.clearHistory() }
        }
    }
    BackHandler(enabled = canGoBack) { web?.goBack() }

    TraceonScaffold(title = "TRACEON Browser", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val active = recording && !priv
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (active) Brand.Mint else Brand.Coral))
                Text(if (priv) "Private: nothing is recorded" else if (recording) "Recording pages you open" else "Recording is off (Settings)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = priv, onClick = {
                    val next = !priv
                    vm.setPrivate(next)
                    if (next) { CookieManager.getInstance().removeAllCookies(null); web?.clearCache(true); web?.clearHistory() }
                }, label = { Text("Private") })
            }
            OutlinedTextField(
                value = address, onValueChange = { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onGo = { if (address.isNotBlank()) { started = true; web?.loadUrl(normalise(address)) } }),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.safeBrowsingEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val scheme = request.url.scheme?.lowercase()
                                    return scheme != "http" && scheme != "https"
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    if (url != null) address = url
                                    canGoBack = view.canGoBack()
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    canGoBack = view.canGoBack()
                                    val title = view.title
                                    view.evaluateJavascript("(function(){var m=document.querySelector('meta[name=description]');return m?m.content:''})()") { raw ->
                                        vm.onPageFinished(url, title, decodeJsString(raw).take(300))
                                    }
                                }
                            }
                            web = this
                        }
                    },
                    onRelease = { it.destroy(); web = null },
                )
                if (!started) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Browse here to build a real, item-level history.", style = MaterialTheme.typography.titleMedium)
                        Text("Pages you open (and searches on supported engines) are saved to your library with time on page, unless Private is on. Other browsers are not visible to TRACEON.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { started = true; address = "https://duckduckgo.com"; web?.loadUrl("https://duckduckgo.com") }) { Text("Open DuckDuckGo") }
                    }
                }
            }
        }
    }
}
