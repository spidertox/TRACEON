package com.traceon.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = CURRENT,
    val createdAt: Long,
    val items: List<BackupItem>,
    val appSessions: List<BackupAppSession> = emptyList(),
) {
    companion object {
        const val FORMAT = "traceon-backup"
        const val CURRENT = 1
    }
}

@Serializable
data class BackupItem(
    val dedupKey: String,
    val platformSlug: String? = null,
    val contentType: String,
    val title: String,
    val description: String = "",
    val visibleText: String = "",
    val url: String? = null,
    val creator: String? = null,
    val thumbnailUrl: String? = null,
    val category: String? = null,
    val categoryLocked: Boolean = false,
    val collectionMethod: String,
    val dataStatus: String,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val topics: List<BackupTopic> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
)

@Serializable
data class BackupTopic(val name: String, val confidence: Float, val userSet: Boolean)

@Serializable
data class BackupEvent(val kind: String, val time: Long, val durationMs: Long? = null, val method: String, val source: String = "")

@Serializable
data class BackupAppSession(val packageName: String, val label: String, val start: Long, val end: Long? = null, val durationMs: Long? = null)
