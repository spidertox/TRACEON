package com.traceon.feature.contentmap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.traceon.core.graph.GraphColors
import com.traceon.core.graph.GraphNode
import com.traceon.core.graph.GraphSnapshot
import com.traceon.core.graph.NodeKind
import com.traceon.core.graph.Pt
import com.traceon.core.graph.RadialLayout
import kotlin.math.hypot
import kotlin.math.ln

private fun nodeRadius(n: GraphNode): Float {
    val base = when (n.kind) {
        NodeKind.ROOT -> 26f; NodeKind.PLATFORM -> 20f; NodeKind.TYPE -> 16f
        NodeKind.TOPIC -> 13f; NodeKind.MORE -> 10f; NodeKind.CONTENT -> 8f
    }
    val bump = if (n.kind == NodeKind.CONTENT || n.kind == NodeKind.MORE) 0f else (ln(n.count.coerceAtLeast(1).toFloat()) * 1.4f).coerceAtMost(8f)
    return base + bump
}

private fun hitTest(pos: Offset, snapshot: GraphSnapshot, shown: Map<String, Pt>, size: IntSize, offset: Offset, scale: Float): String? {
    val cx = size.width / 2f
    val cy = size.height / 2f
    var best: String? = null
    var bestDistance = Float.MAX_VALUE
    for (n in snapshot.nodes) {
        val p = shown[n.id] ?: continue
        val d = hypot(pos.x - (cx + offset.x + p.x * scale), pos.y - (cy + offset.y + p.y * scale))
        val reach = nodeRadius(n) * scale.coerceIn(0.6f, 2f) + 14f
        if (d <= reach && d < bestDistance) { best = n.id; bestDistance = d }
    }
    return best
}

/**
 * Pan / zoom / tap / long-press canvas. Positions animate between layouts; only the visible (opened) nodes are drawn.
 * Labels of leaf content are drawn only when zoomed in, keeping dense graphs readable.
 */
@Composable
fun GraphCanvas(
    snapshot: GraphSnapshot,
    positions: Map<String, Pt>,
    selected: Set<String>,
    highlight: Set<String>,
    fitKey: Int,
    focusId: String?,
    onFocusConsumed: () -> Unit,
    onTap: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var from by remember { mutableStateOf<Map<String, Pt>>(emptyMap()) }
    var to by remember { mutableStateOf(positions) }
    val progress = remember { Animatable(1f) }
    val lastShown = remember { arrayOf<Map<String, Pt>>(emptyMap()) }

    val t = progress.value
    val shown = remember(to, from, t) {
        to.mapValues { (id, b) ->
            val a = from[id] ?: b
            Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }
    SideEffect { lastShown[0] = shown }
    LaunchedEffect(positions) {
        from = lastShown[0]
        to = positions
        progress.snapTo(0f)
        progress.animateTo(1f, tween(320))
    }

    LaunchedEffect(fitKey, size, snapshot.nodes.isEmpty()) {
        if (size.width > 0 && positions.isNotEmpty()) {
            val b = RadialLayout.bounds(positions.values)
            val w = (b[2] - b[0]).coerceAtLeast(1f)
            val h = (b[3] - b[1]).coerceAtLeast(1f)
            val s = minOf((size.width - 90f) / w, (size.height - 90f) / h).coerceIn(0.05f, 1.6f)
            scale = s
            offset = Offset(-(b[0] + b[2]) / 2f * s, -(b[1] + b[3]) / 2f * s)
        }
    }
    LaunchedEffect(focusId, positions) {
        val p = focusId?.let { positions[it] }
        if (p != null && size.width > 0) {
            scale = maxOf(scale, 0.9f)
            offset = Offset(-p.x * scale, -p.y * scale)
            onFocusConsumed()
        }
    }

    val currentSnapshot by rememberUpdatedState(snapshot)
    val currentShown by rememberUpdatedState(shown)
    val tapCallback by rememberUpdatedState(onTap)
    val longCallback by rememberUpdatedState(onLongPress)
    val backgroundCallback by rememberUpdatedState(onBackgroundTap)

    val density = LocalDensity.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val glyphPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val newScale = (scale * zoom).coerceIn(0.05f, 6f)
                    val z = newScale / scale
                    offset = offset * z + (centroid - center) * (1f - z) + pan
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val id = hitTest(pos, currentSnapshot, currentShown, size, offset, scale)
                        if (id != null) tapCallback(id) else backgroundCallback()
                    },
                    onLongPress = { pos -> hitTest(pos, currentSnapshot, currentShown, size, offset, scale)?.let { longCallback(it) } },
                )
            },
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val dimmed = highlight.isNotEmpty()
        val visualScale = scale.coerceIn(0.6f, 2f)
        for (e in snapshot.edges) {
            val a = shown[e.from] ?: continue
            val b = shown[e.to] ?: continue
            val lit = !dimmed || (e.from in highlight && e.to in highlight)
            val hierarchy = e.relation == null
            val color = if (hierarchy) Color(GraphColors.PLATFORM).copy(alpha = if (lit) 0.35f else 0.07f) else Color(GraphColors.CONTENT).copy(alpha = if (lit) 0.95f else 0.15f)
            drawLine(color, Offset(cx + offset.x + a.x * scale, cy + offset.y + a.y * scale), Offset(cx + offset.x + b.x * scale, cy + offset.y + b.y * scale), strokeWidth = if (hierarchy) 1.5f else 3f)
        }
        paint.textSize = with(density) { 11.sp.toPx() }
        glyphPaint.textSize = with(density) { 14.sp.toPx() }
        for (n in snapshot.nodes) {
            val p = shown[n.id] ?: continue
            val lit = !dimmed || n.id in highlight
            val r = nodeRadius(n) * visualScale
            val c = Offset(cx + offset.x + p.x * scale, cy + offset.y + p.y * scale)
            drawCircle(Color(GraphColors.of(n.kind)).copy(alpha = if (lit) 1f else 0.25f), r, c)
            if (n.repeated) drawCircle(Color(GraphColors.CONTENT), r + 3f, c, style = Stroke(1.5f))
            if (n.id in selected) drawCircle(Color.White, r + 6f, c, style = Stroke(3f))
            if (n.expandable && r > 11f) {
                glyphPaint.color = 0xFF020817.toInt()
                drawContext.canvas.nativeCanvas.drawText(if (n.expanded) "-" else "+", c.x, c.y + glyphPaint.textSize / 3f, glyphPaint)
            }
            val showLabel = n.id in selected || (lit && (n.kind != NodeKind.CONTENT || scale >= 0.9f))
            if (showLabel) {
                paint.color = textColor
                paint.alpha = if (lit) 255 else 70
                val text = if (n.kind == NodeKind.CONTENT || n.kind == NodeKind.MORE) n.label.take(34) else "${n.label} (${n.count})"
                drawContext.canvas.nativeCanvas.drawText(text, c.x + r + 6f, c.y + paint.textSize / 3f, paint)
            }
        }
    }
}

