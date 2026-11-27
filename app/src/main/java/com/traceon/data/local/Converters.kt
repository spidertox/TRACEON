package com.traceon.data.local

import androidx.room.TypeConverter
import com.traceon.domain.model.CollectionMethod
import com.traceon.domain.model.ContentType
import com.traceon.domain.model.DataStatus
import com.traceon.domain.model.ImportSource
import com.traceon.domain.model.ImportStatus
import com.traceon.domain.model.PlatformKind
import com.traceon.domain.model.RelationType

/** Enums are stored by name; unknown names (from a newer backup/schema) fall back to a safe default. */
class Converters {
    @TypeConverter fun fromContentType(v: ContentType): String = v.name
    @TypeConverter fun toContentType(s: String): ContentType = runCatching { ContentType.valueOf(s) }.getOrDefault(ContentType.OTHER)

    @TypeConverter fun fromCollectionMethod(v: CollectionMethod): String = v.name
    @TypeConverter fun toCollectionMethod(s: String): CollectionMethod = runCatching { CollectionMethod.valueOf(s) }.getOrDefault(CollectionMethod.MANUAL)

    @TypeConverter fun fromDataStatus(v: DataStatus): String = v.name
    @TypeConverter fun toDataStatus(s: String): DataStatus = runCatching { DataStatus.valueOf(s) }.getOrDefault(DataStatus.UNAVAILABLE)

    @TypeConverter fun fromRelationType(v: RelationType): String = v.name
    @TypeConverter fun toRelationType(s: String): RelationType = runCatching { RelationType.valueOf(s) }.getOrDefault(RelationType.SIMILAR)

    @TypeConverter fun fromImportSource(v: ImportSource): String = v.name
    @TypeConverter fun toImportSource(s: String): ImportSource = runCatching { ImportSource.valueOf(s) }.getOrDefault(ImportSource.BROWSER_HISTORY)

    @TypeConverter fun fromImportStatus(v: ImportStatus): String = v.name
    @TypeConverter fun toImportStatus(s: String): ImportStatus = runCatching { ImportStatus.valueOf(s) }.getOrDefault(ImportStatus.FAILED)

    @TypeConverter fun fromPlatformKind(v: PlatformKind): String = v.name
    @TypeConverter fun toPlatformKind(s: String): PlatformKind = runCatching { PlatformKind.valueOf(s) }.getOrDefault(PlatformKind.OTHER)
}
