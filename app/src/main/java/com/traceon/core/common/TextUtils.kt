package com.traceon.core.common

object TextUtils {
    private val SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    val STOPWORDS: Set<String> = (
        "a an and are as at be but by for from has have how i if in into is it its me my of on or our so than that the their them then there these they this to up us was we were what when where which who why will with you your " +
            "about after all also any can do does did get got just more most new not now one only other out over own same she he her his him should some such too very via vs whats " +
            "video videos watch watched watching official full hd channel part episode ep live shorts short reel reels youtube instagram facebook snapchat com www http https"
        ).split(" ").toSet()

    fun tokens(text: String): List<String> = text.lowercase().split(SPLIT).filter { it.isNotEmpty() }

    fun normalize(text: String): String = tokens(text).joinToString(" ")

    /** Weighted term-frequency keywords; stopwords, numbers and very short tokens are dropped. */
    fun keywords(weighted: List<Pair<String, Int>>, max: Int = 8): List<String> {
        val score = HashMap<String, Int>()
        for ((text, weight) in weighted) {
            for (t in tokens(text)) {
                if (t.length < 3 || t in STOPWORDS || t.all { it.isDigit() }) continue
                score[t] = (score[t] ?: 0) + weight
            }
        }
        return score.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max).map { it.key }
    }

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    fun likeEscape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
