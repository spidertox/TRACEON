package com.traceon.data.mapper

import com.traceon.data.local.dao.ContentRow
import com.traceon.data.local.dao.TimelineRow
import com.traceon.data.local.entity.ImportJobEntity
import com.traceon.domain.model.ContentItem
import com.traceon.domain.model.ImportJob
import com.traceon.domain.model.TimelineEntry

fun ContentRow.toDomain(): ContentItem = ContentItem(
    id = content.id,
    title = content.title,
    description = content.description,
    visibleText = content.visibleText,
    url = content.url,
    canonicalUrl = content.canonicalUrl,
    domain = content.domain,
    platformSlug = platformSlug,
    platformName = platformName,
    contentType = content.contentType,
    creator = creatorName,
    thumbnailUrl = content.thumbnailUrl,
    category = categoryName,
    categoryConfidence = content.categoryConfidence,
    categoryLocked = content.categoryLocked,
    keywords = content.keywords.split(",").filter { it.isNotBlank() },
    firstSeen = content.firstSeen,
    lastSeen = content.lastSeen,
    sessionCount = content.sessionCount,
    viewingDurationMs = content.viewingDurationMs,
    readingDurationMs = content.readingDurationMs,
    collectionMethod = content.collectionMethod,
    metadataConfidence = content.metadataConfidence,
    dataStatus = content.dataStatus,
    notes = content.userNotes,
    isDemo = content.isDemo,
)

fun TimelineRow.toDomain(): TimelineEntry = TimelineEntry(
    item = ContentRow(content, platformSlug, platformName, creatorName, categoryName).toDomain(),
    eventTime = eventTime,
    durationMs = eventDurationMs,
    method = eventMethod,
    source = eventSource?.takeIf { it.isNotEmpty() },
)

fun ImportJobEntity.toDomain(): ImportJob = ImportJob(
    id, sourceType, fileName, startedAt, finishedAt, status, totalRecords, importedCount, duplicateCount, skippedCount, message,
)
