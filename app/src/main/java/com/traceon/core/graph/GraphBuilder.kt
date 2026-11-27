package com.traceon.core.graph

import com.traceon.core.common.PlatformCatalog
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.GraphItem
import com.traceon.domain.model.RelationEdge

/**
 * Root -> platform group -> content type -> topic -> content. Only the opened branches are materialised and a topic shows
 * [pageSize] items per page, so the visible graph stays small however large the library is.
 */
class GraphBuilder(private val pageSize: Int = 12, private val maxRelationEdges: Int = 400) {

    private class Index(val items: List<GraphItem>) {
        val groups = LinkedHashMap<String, Pair<String, LinkedHashMap<ContentType, LinkedHashMap<String, MutableList<GraphItem>>>>>()
    }

    private fun index(items: List<GraphItem>): Index {
        val idx = Index(items)
        for (item in items) {
            val (key, label) = PlatformCatalog.groupOf(item.platformSlug)
            val g = idx.groups.getOrPut(key) { label to LinkedHashMap() }
            g.second.getOrPut(item.contentType) { LinkedHashMap() }.getOrPut(topicOf(item)) { ArrayList() }.add(item)
        }
        return idx
    }

    private fun topicOf(i: GraphItem) = i.category ?: UNCLASSIFIED
    private fun gid(key: String) = "g:$key"
    private fun tid(key: String, t: ContentType) = "t:$key:${t.name}"
    private fun kid(key: String, t: ContentType, topic: String) = "k:$key:${t.name}:$topic"
    private fun cid(id: Long) = "c:$id"

    fun build(items: List<GraphItem>, relations: List<RelationEdge>, state: GraphState): GraphSnapshot {
        val idx = index(items)
        val nodes = ArrayList<GraphNode>()
        val edges = ArrayList<GraphEdge>()
        val rootOpen = ROOT_ID in state.expanded
        nodes += GraphNode(ROOT_ID, "MY DIGITAL UNIVERSE", NodeKind.ROOT, 0, items.size, expandable = items.isNotEmpty(), expanded = rootOpen)
        if (rootOpen) {
            for ((key, g) in idx.groups.entries.sortedByDescending { e -> e.value.second.values.sumOf { t -> t.values.sumOf { l -> l.size } } }) {
                val groupId = gid(key)
                val groupCount = g.second.values.sumOf { t -> t.values.sumOf { l -> l.size } }
                val groupOpen = groupId in state.expanded
                nodes += GraphNode(groupId, g.first, NodeKind.PLATFORM, 1, groupCount, ROOT_ID, expandable = true, expanded = groupOpen)
                edges += GraphEdge(ROOT_ID, groupId)
                if (!groupOpen) continue
                for ((type, topics) in g.second.entries.sortedByDescending { e -> e.value.values.sumOf { l -> l.size } }) {
                    val typeId = tid(key, type)
                    val typeOpen = typeId in state.expanded
                    nodes += GraphNode(typeId, type.plural, NodeKind.TYPE, 2, topics.values.sumOf { l -> l.size }, groupId, expandable = true, expanded = typeOpen)
                    edges += GraphEdge(groupId, typeId)
                    if (!typeOpen) continue
                    for ((topic, list) in topics.entries.sortedByDescending { e -> e.value.size }) {
                        val topicId = kid(key, type, topic)
                        val topicOpen = topicId in state.expanded
                        nodes += GraphNode(topicId, topic, NodeKind.TOPIC, 3, list.size, typeId, expandable = true, expanded = topicOpen)
                        edges += GraphEdge(typeId, topicId)
                        if (!topicOpen) continue
                        val shown = pageSize * (1 + (state.extraPages[topicId] ?: 0))
                        for (item in list.take(shown)) {
                            nodes += GraphNode(cid(item.id), item.title, NodeKind.CONTENT, 4, 1, topicId, item.id, repeated = item.sessionCount > 1)
                            edges += GraphEdge(topicId, cid(item.id))
                        }
                        if (list.size > shown) {
                            val moreId = "m:$topicId"
                            nodes += GraphNode(moreId, "+${list.size - shown} more", NodeKind.MORE, 4, list.size - shown, topicId)
                            edges += GraphEdge(topicId, moreId)
                        }
                    }
                }
            }
        }
        val visible = nodes.filter { it.kind == NodeKind.CONTENT }.mapNotNull { it.contentId }.toHashSet()
        var added = 0
        for (r in relations) {
            if (added >= maxRelationEdges) break
            if (r.fromId in visible && r.toId in visible) {
                edges += GraphEdge(cid(r.fromId), cid(r.toId), r.type)
                added++
            }
        }
        return GraphSnapshot(nodes, edges)
    }

    /** Opens every branch (and enough pages) needed for [contentId] to appear in the graph. */
    fun reveal(items: List<GraphItem>, contentId: Long, state: GraphState): GraphState {
        val item = items.firstOrNull { it.id == contentId } ?: return state
        val (key, _) = PlatformCatalog.groupOf(item.platformSlug)
        val topicId = kid(key, item.contentType, topicOf(item))
        val position = items.filter {
            PlatformCatalog.groupOf(it.platformSlug).first == key && it.contentType == item.contentType && topicOf(it) == topicOf(item)
        }.indexOfFirst { it.id == contentId }
        val pagesNeeded = position / pageSize
        return state.copy(
            expanded = state.expanded + setOf(ROOT_ID, gid(key), tid(key, item.contentType), topicId),
            extraPages = state.extraPages + (topicId to maxOf(pagesNeeded, state.extraPages[topicId] ?: 0)),
        )
    }

    fun nodeIdForContent(id: Long): String = cid(id)

    companion object {
        const val UNCLASSIFIED = "Unclassified"
    }
}
