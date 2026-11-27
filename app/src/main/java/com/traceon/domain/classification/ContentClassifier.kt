package com.traceon.domain.classification

import com.traceon.core.common.TextUtils

data class ClassificationInput(
    val title: String,
    val description: String = "",
    val visibleText: String = "",
    val tags: List<String> = emptyList(),
    val domain: String? = null,
    val creator: String? = null,
)

data class ScoredTopic(val name: String, val group: String, val confidence: Float)

data class ClassificationResult(
    val category: String?,
    val group: String?,
    /** Heuristic score in 0..0.95. It is a ranking signal, not a probability. */
    val confidence: Float,
    val topics: List<ScoredTopic>,
    val keywords: List<String>,
)

/**
 * Offline keyword classifier. It only reads metadata it is given (title, description, tags,
 * authorized text, domain). It never claims to understand media from a URL alone: with no
 * evidence it returns no category and zero confidence.
 */
class ContentClassifier(private val defs: List<CategoryDef> = Taxonomy.categories) {

    fun classify(input: ClassificationInput): ClassificationResult {
        val fields = listOf(
            pad(input.title) to 3.0,
            pad(input.tags.joinToString(" ")) to 2.5,
            pad(input.description) to 1.5,
            pad(input.visibleText.take(4000)) to 1.0,
            pad(input.creator.orEmpty()) to 0.5,
        )
        val scores = HashMap<String, Double>()
        for (def in defs) {
            var s = 0.0
            for ((padded, mult) in fields) {
                if (padded.length <= 2) continue
                for ((kw, w) in def.keywords) {
                    val c = count(padded, " $kw ")
                    if (c > 0) s += w * mult * (1 + 0.3 * (minOf(c, 3) - 1))
                }
            }
            if (s > 0) scores[def.name] = s
        }
        val domain = input.domain
        if (domain != null) {
            for ((suffix, cat, w) in Taxonomy.domainHints) {
                if (domain == suffix || domain.endsWith(".$suffix")) scores[cat] = (scores[cat] ?: 0.0) + w
            }
        }
        val keywords = TextUtils.keywords(
            listOf(input.title to 3, input.tags.joinToString(" ") to 3, input.description to 1), 8,
        )
        val ranked = scores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        val top = ranked.firstOrNull()
        if (top == null || top.value < MIN_SCORE) return ClassificationResult(null, null, 0f, emptyList(), keywords)

        val second = ranked.getOrNull(1)?.value ?: 0.0
        val confidence = ((top.value - 0.5 * second) / (top.value + 4.0)).coerceIn(0.0, 0.95).toFloat()
        val cutoff = maxOf(MIN_SCORE, 0.4 * top.value)
        val topics = ranked.filter { it.value >= cutoff }.take(3).map {
            ScoredTopic(it.key, Taxonomy.groupOf(it.key) ?: "Other", (it.value / (it.value + 4.0)).toFloat().coerceAtMost(0.95f))
        }
        return ClassificationResult(top.key, Taxonomy.groupOf(top.key), confidence, topics, keywords)
    }

    private fun pad(text: String): String = " " + TextUtils.normalize(text) + " "

    private fun count(haystack: String, needle: String): Int {
        var c = 0
        var i = haystack.indexOf(needle)
        while (i >= 0) {
            c++
            i = haystack.indexOf(needle, i + needle.length - 1)
        }
        return c
    }

    companion object {
        const val MIN_SCORE = 4.0
    }
}
