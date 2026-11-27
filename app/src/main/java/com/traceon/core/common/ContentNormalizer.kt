package com.traceon.core.common

import com.traceon.domain.model.ContentDraft
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import java.util.UUID

data class NormalizedContent(
    val platformSlug: String,
    val contentType: ContentType,
    val canonicalUrl: String?,
    val domain: String?,
    val platformContentId: String?,
    val dedupKey: String,
    val metadataConfidence: Float,
    val status: DataStatus,
)

/**
 * Decides identity (dedup key), platform, type and provenance for a draft.
 * Identity order: platform content id, canonical URL, platform+type+exact normalized title.
 * Manual entries without a URL never merge automatically (the user confirms merges in the UI).
 */
object ContentNormalizer {
    fun normalize(d: ContentDraft, newId: () -> String = { UUID.randomUUID().toString() }): NormalizedContent {
        val titleKnown = d.title.isNotBlank() && d.title.trim() != d.url?.trim()
        val query = d.searchQuery
        if (query != null) {
            val engine = d.engineSlug ?: "google_search"
            return NormalizedContent(
                platformSlug = engine,
                contentType = ContentType.SEARCH_QUERY,
                canonicalUrl = null,
                domain = d.url?.let { UrlUtils.domainOf(it) },
                platformContentId = null,
                dedupKey = d.dedupKeyOverride ?: "search:$engine:${TextUtils.normalize(query)}",
                metadataConfidence = 0.9f,
                status = statusFor(d.origin, true, false),
            )
        }
        val url = d.url?.trim()?.takeIf { it.isNotEmpty() }
        val parsed = url?.let { UrlUtils.parse(it) }
        if (url != null && parsed != null) {
            val match = PlatformDetector.detect(url)
            val canonical = match?.canonicalUrl ?: UrlUtils.canonicalize(url)
            val explicit = d.contentType
            val type: ContentType
            val typeConf: Float
            val platform: String
            if (explicit != null) {
                type = explicit; typeConf = 0.9f; platform = match?.slug ?: d.platformSlug ?: "web"
            } else if (match != null) {
                type = match.type; typeConf = match.confidence; platform = match.slug
            } else {
                val (t, c) = WebHeuristics.classify(parsed.host, parsed.path)
                type = t; typeConf = c; platform = d.platformSlug ?: "web"
            }
            val inferred = explicit == null && typeConf < 0.85f
            val key = d.dedupKeyOverride
                ?: match?.contentId?.let { "${match.slug}:$it" }
                ?: "url:$canonical"
            return NormalizedContent(
                platformSlug = platform,
                contentType = type,
                canonicalUrl = canonical,
                domain = parsed.host,
                platformContentId = match?.contentId,
                dedupKey = key,
                metadataConfidence = confidence(typeConf, titleKnown, d),
                status = statusFor(d.origin, titleKnown, inferred),
            )
        }
        val platform = d.platformSlug ?: "other"
        val type = d.contentType ?: ContentType.OTHER
        val key = d.dedupKeyOverride ?: if (d.origin == DataStatus.MANUAL) {
            "manual:${newId()}"
        } else {
            "dt:$platform|${type.name}|${TextUtils.normalize(d.title)}"
        }
        return NormalizedContent(
            platformSlug = platform, contentType = type, canonicalUrl = null, domain = null, platformContentId = null,
            dedupKey = key,
            metadataConfidence = confidence(if (d.contentType != null) 0.8f else 0.3f, titleKnown, d),
            status = statusFor(d.origin, titleKnown, d.contentType == null),
        )
    }

    /** Completeness of stored metadata (type certainty, title present, creator/description present). Not a truth score. */
    private fun confidence(typeConf: Float, titleKnown: Boolean, d: ContentDraft): Float =
        (typeConf * 0.5f + (if (titleKnown) 0.3f else 0f) + (if (!d.creator.isNullOrBlank() || d.description.isNotBlank()) 0.2f else 0f))
            .coerceIn(0f, 1f)

    private fun statusFor(origin: DataStatus, titleKnown: Boolean, typeInferred: Boolean): DataStatus = when {
        origin == DataStatus.SAMPLE -> DataStatus.SAMPLE
        origin == DataStatus.MANUAL -> DataStatus.MANUAL
        !titleKnown -> DataStatus.UNAVAILABLE
        typeInferred -> DataStatus.INFERRED
        else -> origin
    }
}

data class SharedContent(val url: String?, val title: String, val note: String)

object SharedContentParser {
    /** Splits an ACTION_SEND payload into a URL and a title. Title stays empty when the sender gave none. */
    fun parse(text: String?, subject: String?): SharedContent {
        val body = text.orEmpty().trim()
        val url = UrlUtils.extractFirstUrl(body)
        val rest = if (url != null) body.replace(url, " ").trim() else body
        val firstLine = rest.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val title = subject?.trim()?.takeIf { it.isNotEmpty() && it != url } ?: firstLine
        val note = if (title == firstLine) rest.removePrefix(firstLine).trim() else rest
        return SharedContent(url, title, note)
    }
}
