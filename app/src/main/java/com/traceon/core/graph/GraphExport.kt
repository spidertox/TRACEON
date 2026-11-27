package com.traceon.core.graph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object GraphExport {
    /** Nodes (with layout positions) and edges as JSON, for use in other tools. */
    fun toJson(snapshot: GraphSnapshot, positions: Map<String, Pt>): String {
        val nodes = snapshot.nodes.map { n ->
            buildJsonObject {
                put("id", n.id); put("label", n.label); put("kind", n.kind.name); put("depth", n.depth); put("count", n.count)
                put("contentId", n.contentId?.let { JsonPrimitive(it) } ?: JsonNull)
                positions[n.id]?.let { put("x", it.x); put("y", it.y) }
            }
        }
        val edges = snapshot.edges.map { e ->
            buildJsonObject { put("from", e.from); put("to", e.to); put("relation", e.relation?.name ?: "HIERARCHY") }
        }
        return JsonObject(mapOf("format" to JsonPrimitive("traceon-graph"), "nodes" to JsonArray(nodes), "edges" to JsonArray(edges))).toString()
    }

    /** Renders the visible graph to a bitmap, fitted to [width] x [height]. */
    fun toBitmap(snapshot: GraphSnapshot, positions: Map<String, Pt>, width: Int = 2048, height: Int = 2048): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF020817.toInt())
        if (positions.isEmpty()) return bmp
        val b = RadialLayout.bounds(positions.values)
        val pad = 140f
        val scale = minOf((width - 2 * pad) / maxOf(b[2] - b[0], 1f), (height - 2 * pad) / maxOf(b[3] - b[1], 1f))
        fun sx(p: Pt) = width / 2f + (p.x - (b[0] + b[2]) / 2f) * scale
        fun sy(p: Pt) = height / 2f + (p.y - (b[1] + b[3]) / 2f) * scale
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
        for (e in snapshot.edges) {
            val a = positions[e.from] ?: continue
            val c = positions[e.to] ?: continue
            line.color = if (e.relation == null) 0x6629B6FF else 0xCCFFB84D.toInt()
            canvas.drawLine(sx(a), sy(a), sx(c), sy(c), line)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6EEFF.toInt(); textSize = 26f }
        for (n in snapshot.nodes) {
            val p = positions[n.id] ?: continue
            dot.color = GraphColors.of(n.kind)
            val r = when (n.kind) { NodeKind.ROOT -> 26f; NodeKind.PLATFORM -> 20f; NodeKind.TYPE -> 16f; NodeKind.TOPIC -> 13f; else -> 9f }
            canvas.drawCircle(sx(p), sy(p), r, dot)
            if (n.kind != NodeKind.CONTENT || snapshot.nodes.size < 60) canvas.drawText(n.label.take(28), sx(p) + r + 6f, sy(p) + 9f, text)
        }
        return bmp
    }
}
