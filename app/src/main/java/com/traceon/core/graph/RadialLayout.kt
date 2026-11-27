package com.traceon.core.graph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Deterministic radial tree layout. Each node owns an angular wedge proportional to its visible leaf count, and ring radii
 * grow until the smallest wedge on the ring has at least [minSpacing] of arc length, so branches never overlap.
 */
object RadialLayout {
    fun compute(snapshot: GraphSnapshot, ringGap: Float = 170f, minSpacing: Float = 64f): Map<String, Pt> {
        val children = HashMap<String, MutableList<String>>()
        for (e in snapshot.edges) if (e.relation == null) children.getOrPut(e.from) { ArrayList() }.add(e.to)
        if (snapshot.nodes.isEmpty()) return emptyMap()

        val weight = HashMap<String, Int>()
        fun weightOf(id: String): Int = weight.getOrPut(id) {
            val kids = children[id].orEmpty()
            if (kids.isEmpty()) 1 else max(1, kids.sumOf { weightOf(it) })
        }

        val angle = HashMap<String, Float>()
        val width = HashMap<String, Float>()
        val depthOf = HashMap<String, Int>()
        fun place(id: String, start: Float, end: Float, depth: Int) {
            angle[id] = (start + end) / 2f
            width[id] = end - start
            depthOf[id] = depth
            val kids = children[id].orEmpty()
            val total = kids.sumOf { weightOf(it) }.toFloat()
            var cursor = start
            for (k in kids) {
                val span = (end - start) * weightOf(k) / total
                place(k, cursor, cursor + span, depth + 1)
                cursor += span
            }
        }
        place(ROOT_ID, 0f, (2 * PI).toFloat(), 0)

        val maxDepth = depthOf.values.maxOrNull() ?: 0
        val radius = FloatArray(maxDepth + 1)
        for (d in 1..maxDepth) {
            val minWidth = depthOf.filterValues { it == d }.keys.minOf { width.getValue(it) }
            radius[d] = max(radius[d - 1] + ringGap, minSpacing / max(minWidth, 0.0001f))
        }
        val out = HashMap<String, Pt>()
        for (n in snapshot.nodes) {
            val d = depthOf[n.id] ?: continue
            val r = radius[d]
            val a = angle.getValue(n.id)
            out[n.id] = Pt(r * cos(a), r * sin(a))
        }
        return out
    }

    fun bounds(points: Collection<Pt>): FloatArray {
        if (points.isEmpty()) return floatArrayOf(-1f, -1f, 1f, 1f)
        return floatArrayOf(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
}
