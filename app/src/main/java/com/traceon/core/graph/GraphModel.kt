package com.traceon.core.graph

import com.traceon.domain.model.RelationType

const val ROOT_ID = "root"

enum class NodeKind { ROOT, PLATFORM, TYPE, TOPIC, CONTENT, MORE }

data class GraphNode(
    val id: String,
    val label: String,
    val kind: NodeKind,
    val depth: Int,
    val count: Int = 0,
    val parentId: String? = null,
    val contentId: Long? = null,
    val repeated: Boolean = false,
    val expandable: Boolean = false,
    val expanded: Boolean = false,
)

/** relation == null is a hierarchy edge; otherwise a content-to-content relationship. */
data class GraphEdge(val from: String, val to: String, val relation: RelationType? = null)

data class GraphSnapshot(val nodes: List<GraphNode>, val edges: List<GraphEdge>) {
    val byId: Map<String, GraphNode> by lazy { nodes.associateBy { it.id } }
}

/** What the user has opened. The visible graph is always derived from this plus the data, so it stays small. */
data class GraphState(
    val expanded: Set<String> = setOf(ROOT_ID),
    val extraPages: Map<String, Int> = emptyMap(),
)

data class Pt(val x: Float, val y: Float)

object GraphColors {
    const val ROOT = 0xFFFFFFFF.toInt()
    const val PLATFORM = 0xFF29B6FF.toInt()
    const val TYPE = 0xFF7C8CFF.toInt()
    const val TOPIC = 0xFF2DE2C4.toInt()
    const val CONTENT = 0xFFFFB84D.toInt()
    const val MORE = 0xFF8A97B8.toInt()
    fun of(kind: NodeKind): Int = when (kind) {
        NodeKind.ROOT -> ROOT; NodeKind.PLATFORM -> PLATFORM; NodeKind.TYPE -> TYPE
        NodeKind.TOPIC -> TOPIC; NodeKind.CONTENT -> CONTENT; NodeKind.MORE -> MORE
    }
}
