package com.traceon.core.common

import java.net.URLDecoder

data class ParsedUrl(val host: String, val path: String, val query: List<Pair<String, String>>) {
    fun param(name: String): String? = query.firstOrNull { it.first == name }?.second?.let { UrlUtils.decode(it) }
}

object UrlUtils {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val URL_PARTS = Regex("""^https?://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#.*)?$""", RegexOption.IGNORE_CASE)
    private val TRACKING = setOf("fbclid", "gclid", "igshid", "igsh", "si", "feature", "mc_cid", "mc_eid", "_ga", "yclid", "msclkid", "ref_src")

    fun extractFirstUrl(text: String): String? =
        URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

    /** Lenient http(s) parser. Returns null for anything that is not a plain web URL. */
    fun parse(url: String): ParsedUrl? {
        val m = URL_PARTS.matchEntire(url.trim()) ?: return null
        val host = m.groupValues[1].substringAfterLast('@').substringBefore(':').lowercase().removePrefix("www.")
        if (host.isEmpty() || (!host.contains('.') && host != "localhost")) return null
        val query = m.groupValues[3].split('&').filter { it.isNotEmpty() }.map {
            val i = it.indexOf('=')
            if (i < 0) it to "" else it.substring(0, i) to it.substring(i + 1)
        }
        return ParsedUrl(host, m.groupValues[2], query)
    }

    fun domainOf(url: String): String? = parse(url)?.host

    fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    fun canonicalize(url: String): String? {
        val p = parse(url) ?: return null
        val kept = p.query
            .filter { (k, _) -> val lk = k.lowercase(); !lk.startsWith("utm_") && lk !in TRACKING }
            .sortedBy { it.first }
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&") { if (it.second.isEmpty()) it.first else "${it.first}=${it.second}" }
        return "https://${p.host}${p.path.trimEnd('/')}$query"
    }
}
