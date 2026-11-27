package com.traceon.data.repository

import com.traceon.core.common.PlatformCatalog
import com.traceon.data.local.dao.TaxonomyDao
import com.traceon.data.local.entity.PlatformEntity
import com.traceon.domain.model.PlatformKind

/** Creates the platform row on first use (names/kinds come from the catalog). */
internal suspend fun ensurePlatform(taxonomy: TaxonomyDao, slug: String): Long {
    taxonomy.platformId(slug)?.let { return it }
    val def = PlatformCatalog.bySlug(slug)
    taxonomy.insertPlatform(PlatformEntity(slug = slug, name = def?.name ?: slug, kind = def?.kind ?: PlatformKind.OTHER))
    return checkNotNull(taxonomy.platformId(slug))
}
