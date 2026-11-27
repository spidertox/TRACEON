package com.traceon.core.graph

import com.traceon.domain.model.ContentType
import com.traceon.domain.model.GraphItem
import com.traceon.domain.model.RelationEdge
import com.traceon.domain.model.RelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

private fun item(id: Long, title: String, platform: String?, type: ContentType, category: String?, sessions: Int = 1) =
    GraphItem(id, title, platform, type, category, null, sessions, 10_000L - id)

private val ITEMS = listOf(
    item(1, "A1", "youtube", ContentType.VIDEO, "Android"),
    item(2, "A2", "youtube", ContentType.VIDEO, "Android", sessions = 3),
    item(3, "P1", "youtube", ContentType.VIDEO, "Programming"),
    item(4, "S1", "youtube", ContentType.SHORT, null),
    item(5, "R1", "instagram", ContentType.REEL, "Fitness"),
    item(6, "W1", "web", ContentType.WEBSITE, "Software"),
    item(7, "Q1", "google_search", ContentType.SEARCH_QUERY, "Programming"),
)

class GraphBuilderTest {
    private val builder = GraphBuilder(pageSize = 12)

    @Test fun collapsedGraphShowsOnlyRootAndPlatformGroups() {
        val g = builder.build(ITEMS, emptyList(), GraphState())
        assertEquals(setOf("MY DIGITAL UNIVERSE", "YouTube", "Instagram", "Browsers & Web", "Search Engines"), g.nodes.map { it.label }.toSet())
        assertEquals(7, g.byId.getValue(ROOT_ID).count)
        assertEquals(4, g.byId.getValue("g:youtube").count)
        assertEquals(4, g.edges.size)
        assertTrue(g.nodes.all { it.depth <= 1 })
    }

    @Test fun expandingABranchRevealsTypesTopicsAndContentOnly_forThatBranch() {
        val state = GraphState(setOf(ROOT_ID, "g:youtube", "t:youtube:VIDEO", "k:youtube:VIDEO:Android"))
        val g = builder.build(ITEMS, emptyList(), state)
        val ids = g.nodes.map { it.id }
        assertTrue(ids.containsAll(listOf("t:youtube:VIDEO", "t:youtube:SHORT", "k:youtube:VIDEO:Android", "k:youtube:VIDEO:Programming", "c:1", "c:2")))
        assertFalse("c:3" in ids) // Programming topic is still collapsed
        assertFalse(ids.any { it.startsWith("t:instagram") })
        assertTrue(ids.indexOf("t:youtube:VIDEO") < ids.indexOf("t:youtube:SHORT")) // larger type first
        assertTrue(g.byId.getValue("c:2").repeated)
        assertFalse(g.byId.getValue("c:1").repeated)
    }

    @Test fun unclassifiedItemsGetTheirOwnTopicInsteadOfBeingGuessed() {
        val g = builder.build(ITEMS, emptyList(), GraphState(setOf(ROOT_ID, "g:youtube", "t:youtube:SHORT")))
        assertNotNull(g.byId["k:youtube:SHORT:Unclassified"])
    }

    @Test fun topicsPaginateWithAMoreNode() {
        val small = GraphBuilder(pageSize = 2)
        val many = (10L..14L).map { item(it, "V$it", "youtube", ContentType.VIDEO, "Android") }
        val topic = "k:youtube:VIDEO:Android"
        val base = setOf(ROOT_ID, "g:youtube", "t:youtube:VIDEO", topic)
        fun contentCount(g: GraphSnapshot) = g.nodes.count { it.kind == NodeKind.CONTENT }

        val p0 = small.build(many, emptyList(), GraphState(base))
        assertEquals(2, contentCount(p0))
        assertEquals("+3 more", p0.nodes.single { it.kind == NodeKind.MORE }.label)

        val p1 = small.build(many, emptyList(), GraphState(base, mapOf(topic to 1)))
        assertEquals(4, contentCount(p1))
        assertEquals("+1 more", p1.nodes.single { it.kind == NodeKind.MORE }.label)

        val p2 = small.build(many, emptyList(), GraphState(base, mapOf(topic to 2)))
        assertEquals(5, contentCount(p2))
        assertTrue(p2.nodes.none { it.kind == NodeKind.MORE })
    }

    @Test fun relationEdgesAppearOnlyBetweenVisibleContent() {
        val rels = listOf(RelationEdge(1, 2, RelationType.SIMILAR, 0.5f), RelationEdge(1, 3, RelationType.SIMILAR, 0.4f))
        val g = builder.build(ITEMS, rels, GraphState(setOf(ROOT_ID, "g:youtube", "t:youtube:VIDEO", "k:youtube:VIDEO:Android")))
        val relationEdges = g.edges.filter { it.relation != null }
        assertEquals(1, relationEdges.size)
        assertEquals("c:1", relationEdges[0].from)
        assertEquals("c:2", relationEdges[0].to)
    }

    @Test fun revealOpensEveryAncestorAndEnoughPages() {
        val s = builder.reveal(ITEMS, 3, GraphState())
        assertTrue(s.expanded.containsAll(listOf(ROOT_ID, "g:youtube", "t:youtube:VIDEO", "k:youtube:VIDEO:Programming")))
        assertTrue(builder.build(ITEMS, emptyList(), s).byId.containsKey("c:3"))

        val small = GraphBuilder(pageSize = 2)
        val many = (10L..14L).map { item(it, "V$it", "youtube", ContentType.VIDEO, "Android") }
        assertTrue(small.build(many, emptyList(), small.reveal(many, 14, GraphState())).byId.containsKey("c:14"))
    }

    @Test fun emptyLibraryStillHasARoot() {
        val g = builder.build(emptyList(), emptyList(), GraphState())
        assertEquals(listOf(ROOT_ID), g.nodes.map { it.id })
        assertFalse(g.nodes.single().expandable)
    }
}

class RadialLayoutTest {
    private fun dist(p: Pt) = hypot(p.x, p.y)

    @Test fun rootSitsAtTheOrigin() {
        val g = GraphBuilder().build(ITEMS, emptyList(), GraphState())
        val pos = RadialLayout.compute(g)
        assertEquals(0f, pos.getValue(ROOT_ID).x, 1e-3f)
        assertEquals(0f, pos.getValue(ROOT_ID).y, 1e-3f)
    }

    @Test fun childrenAreFartherFromTheCentreThanParents() {
        val all = setOf(ROOT_ID, "g:youtube", "t:youtube:VIDEO", "k:youtube:VIDEO:Android")
        val g = GraphBuilder().build(ITEMS, emptyList(), GraphState(all))
        val pos = RadialLayout.compute(g)
        for (e in g.edges.filter { it.relation == null }) assertTrue("${e.from}->${e.to}", dist(pos.getValue(e.to)) > dist(pos.getValue(e.from)) - 1e-3f)
    }

    @Test fun nodesNeverOverlapEvenWithManyLeaves() {
        val nodes = mutableListOf(GraphNode(ROOT_ID, "root", NodeKind.ROOT, 0, 200))
        val edges = mutableListOf<GraphEdge>()
        repeat(200) { i ->
            nodes += GraphNode("n$i", "n$i", NodeKind.PLATFORM, 1, 1, ROOT_ID)
            edges += GraphEdge(ROOT_ID, "n$i")
        }
        val pos = RadialLayout.compute(GraphSnapshot(nodes, edges), minSpacing = 64f)
        val pts = (0 until 200).map { pos.getValue("n$it") }
        var min = Float.MAX_VALUE
        for (i in pts.indices) for (j in i + 1 until pts.size) min = minOf(min, hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y))
        assertTrue("closest pair was $min", min >= 60f)
    }
}
