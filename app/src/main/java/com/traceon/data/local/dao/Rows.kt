package com.traceon.data.local.dao

import androidx.room.Embedded
import com.traceon.data.local.entity.ContentEntity
import com.traceon.domain.model.CollectionMethod

data class ContentRow(
    @Embedded val content: ContentEntity,
    val platformSlug: String?,
    val platformName: String?,
    val creatorName: String?,
    val categoryName: String?,
)

data class TimelineRow(
    @Embedded val content: ContentEntity,
    val platformSlug: String?,
    val platformName: String?,
    val creatorName: String?,
    val categoryName: String?,
    val eventTime: Long,
    val eventDurationMs: Long?,
    val eventMethod: CollectionMethod,
    val eventSource: String?,
)

data class CandidateRow(val id: Long, val title: String, val keywords: String, val creatorId: Long?, val platformId: Long?, val domain: String?)
data class EventRow(val time: Long, val durationMs: Long?, val method: CollectionMethod, val kind: String, val source: String?)
data class PlatformOption(val slug: String, val name: String)
data class TopicRow(val name: String, val confidence: Float, val userSet: Boolean)
data class ContentNameRow(val contentId: Long, val name: String)
data class TopicLinkRow(val contentId: Long, val name: String, val confidence: Float, val userSet: Boolean)
data class AppSessionRow(val packageName: String, val label: String, val startTime: Long, val endTime: Long?, val durationMs: Long?)
