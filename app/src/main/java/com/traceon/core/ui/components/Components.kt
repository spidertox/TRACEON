@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.traceon.core.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.traceon.core.common.TimeUtils
import com.traceon.core.ui.theme.Brand
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.DataStatus

fun openUrl(context: Context, url: String) {
    val ok = url.startsWith("http://", true) || url.startsWith("https://", true)
    if (!ok) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser installed: nothing sensible to do.
    }
}

@Composable
fun TraceonScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    bottomBarPresent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (bottomBarPresent) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        content = content,
    )
}

@Composable
fun TraceonCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val cs = MaterialTheme.colorScheme
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Brush.verticalGradient(listOf(cs.surfaceVariant.copy(alpha = 0.85f), cs.surface)))
        .border(1.dp, cs.outlineVariant, shape)
    Column(modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(16.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.28f)), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
    }
}

@Composable
fun StatusBadge(status: DataStatus) {
    val color = when (status) {
        DataStatus.OBSERVED -> Brand.Mint
        DataStatus.IMPORTED -> Brand.Cyan
        DataStatus.MANUAL -> Brand.Indigo
        DataStatus.INFERRED -> Brand.Amber
        DataStatus.UNAVAILABLE -> Brand.Coral
        DataStatus.SAMPLE -> Brand.Pink
    }
    InfoChip(if (status == DataStatus.SAMPLE) "DEMO" else status.label, color = color)
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null, secondaryLabel: String? = null, onSecondary: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
        if (secondaryLabel != null && onSecondary != null) OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    TraceonCard(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfidenceBar(value: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { value.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
}

@Composable
fun ContentRowCard(
    item: ContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    whenText: String? = null,
    durationMs: Long? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    TraceonCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            menu?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item.platformName?.let { InfoChip(it, color = Brand.Cyan) }
            InfoChip(item.contentType.label, color = Brand.Indigo)
            InfoChip(item.category ?: "Unclassified", color = if (item.category == null) MaterialTheme.colorScheme.outline else Brand.Mint)
            StatusBadge(item.dataStatus)
            if (whenText != null) InfoChip(whenText)
            if (durationMs != null) InfoChip(TimeUtils.formatDuration(durationMs))
        }
    }
}

/** Chip that opens a menu; value null means "any". */
@Composable
fun <T> DropdownChip(label: String, selectedText: String?, options: List<Pair<String, T?>>, onSelect: (T?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(selected = selectedText != null, onClick = { open = true }, label = { Text(selectedText ?: label, maxLines = 1) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@Composable
fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (text, action) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() }) }
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(0.58f))
    }
}

@Composable
fun BarChartIcon(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = w * 0.17f
        val r = androidx.compose.ui.geometry.CornerRadius(bar / 2f)
        drawRoundRect(tint, androidx.compose.ui.geometry.Offset(w * 0.17f, h * 0.48f), androidx.compose.ui.geometry.Size(bar, h * 0.36f), r)
        drawRoundRect(tint, androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.2f), androidx.compose.ui.geometry.Size(bar, h * 0.64f), r)
        drawRoundRect(tint, androidx.compose.ui.geometry.Offset(w * 0.67f, h * 0.36f), androidx.compose.ui.geometry.Size(bar, h * 0.48f), r)
    }
}
