package com.traceon.domain.classification

import com.traceon.core.common.TextUtils
import com.traceon.domain.model.RelationType

data class Candidate(
    val id: Long, val title: String, val keywords: Set<String>,
    val creatorId: Long?, val platformId: Long?, val domain: String?,
)

data class ProposedRelation(val otherId: Long, val type: RelationType, val score: Float)

object SequenceDetector {
    private val MARKERS = Regex(
        """\b(part|episode|ep|lesson|chapter|day|lecture|video|tutorial|session|class|module)\s*#?\d+\b|#\d+|\b\d+\s*/\s*\d+\b|\b\d+\b""",
        RegexOption.IGNORE_CASE,
    )
    private val DIGITS = Regex("""\d+""")

    fun stem(title: String): String = TextUtils.normalize(MARKERS.replace(title, " "))
    private fun number(title: String): Int? = DIGITS.find(title)?.value?.toIntOrNull()

    /** Two different titles that only differ by numbering ("Kotlin Basics Part 1" / "Part 2"). */
    fun sameSeries(a: String, b: String): Boolean {
        val na = number(a)
        val nb = number(b)
        val sa = stem(a)
        return sa.length >= 8 && sa == stem(b) && na != null && nb != null && na != nb
    }
}

object SimilarityEngine {
    const val SIMILAR_THRESHOLD = 0.2
    const val CROSS_PLATFORM_THRESHOLD = 0.3

    /** Proposes at most [limit] relations for [me] against [candidates]. */
    fun propose(me: Candidate, candidates: List<Candidate>, limit: Int = 6): List<ProposedRelation> {
        val out = ArrayList<ProposedRelation>()
        for (c in candidates) {
            if (c.id == me.id) continue
            val sim = TextUtils.jaccard(me.keywords, c.keywords)
            when {
                SequenceDetector.sameSeries(me.title, c.title) &&
                    ((me.creatorId != null && me.creatorId == c.creatorId) || (me.domain != null && me.domain == c.domain)) ->
                    out += ProposedRelation(c.id, RelationType.LEARNING_SEQUENCE, 0.9f)
                me.creatorId != null && me.creatorId == c.creatorId && me.platformId == c.platformId ->
                    out += ProposedRelation(c.id, RelationType.SAME_CREATOR, (0.5 + sim / 2).toFloat())
                sim >= CROSS_PLATFORM_THRESHOLD && me.platformId != c.platformId ->
                    out += ProposedRelation(c.id, RelationType.CROSS_PLATFORM, sim.toFloat())
                sim >= SIMILAR_THRESHOLD && me.platformId == c.platformId ->
                    out += ProposedRelation(c.id, RelationType.SIMILAR, sim.toFloat())
            }
        }
        return out.sortedByDescending { it.score }.take(limit)
    }
}
