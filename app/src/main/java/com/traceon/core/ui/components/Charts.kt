package com.traceon.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.traceon.core.ui.theme.Brand
import com.traceon.domain.model.LabeledValue

/** Vertical bars. Labels may be blank for bars that should not be annotated. All heights derive from real values. */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, modifier: Modifier = Modifier, chartHeight: Dp = 130.dp) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.height(chartHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height(if (v == 0L) 2.dp else (chartHeight * (v.toFloat() / max)).coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (v == 0L) MaterialTheme.colorScheme.outlineVariant else Brand.Cyan),
                    )
                }
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Ranked horizontal bars with the exact value printed, relative to the largest entry. */
@Composable
fun HBarList(items: List<LabeledValue>, modifier: Modifier = Modifier, format: (Long) -> String = { it.toString() }, maxRows: Int = 8) {
    val rows = items.take(maxRows)
    val max = (rows.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, row ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(format(row.value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((row.value.toFloat() / max).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(Brand.Blue, Brand.Chart[i % Brand.Chart.size]))),
                    )
                }
            }
        }
    }
}

@Composable
fun DonutChart(items: List<LabeledValue>, modifier: Modifier = Modifier, size: Dp = 130.dp) {
    val total = items.sumOf { it.value }.coerceAtLeast(1L).toFloat()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = this.size.minDimension * 0.18f, cap = StrokeCap.Butt)
            val inset = stroke.width / 2f
            var start = -90f
            items.forEachIndexed { i, it ->
                val sweep = 360f * (it.value / total)
                drawArc(
                    color = Brand.Chart[i % Brand.Chart.size], startAngle = start, sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(this.size.width - stroke.width, this.size.height - stroke.width), style = stroke,
                )
                start += sweep
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.take(6).forEachIndexed { i, it ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Brand.Chart[i % Brand.Chart.size]))
                    Spacer(Modifier.width(8.dp))
                    Text("${it.label} (${it.value})", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
