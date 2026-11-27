package com.traceon.data.repository

import com.traceon.core.common.Clock
import com.traceon.data.imports.BrowserHistoryParser
import com.traceon.data.imports.GoogleActivityParser
import com.traceon.data.imports.ParseResult
import com.traceon.data.local.TraceonDatabase
import com.traceon.data.local.entity.ImportJobEntity
import com.traceon.data.mapper.toDomain
import com.traceon.domain.model.ImportJob
import com.traceon.domain.model.ImportSource
import com.traceon.domain.model.ImportStatus
import com.traceon.domain.model.ImportSummary
import com.traceon.domain.repository.ContentRepository
import com.traceon.domain.repository.ImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ImportRepositoryImpl(
    db: TraceonDatabase,
    private val content: ContentRepository,
    private val clock: Clock,
) : ImportRepository {
    private val system = db.systemDao()

    override fun observeJobs(): Flow<List<ImportJob>> = system.observeJobs().map { rows -> rows.map { it.toDomain() } }

    override suspend fun importFile(
        source: ImportSource, fileName: String, text: String, browserSlug: String, onProgress: (Int, Int) -> Unit,
    ): ImportSummary = withContext(Dispatchers.Default) {
        val started = clock.now()
        val jobId = system.insertJob(ImportJobEntity(sourceType = source, fileName = fileName, startedAt = started, status = ImportStatus.RUNNING))
        fun job(status: ImportStatus, total: Int, imported: Int, dup: Int, skipped: Int, msg: String?) = ImportJobEntity(
            id = jobId, sourceType = source, fileName = fileName, startedAt = started, finishedAt = clock.now(), status = status,
            totalRecords = total, importedCount = imported, duplicateCount = dup, skippedCount = skipped, message = msg,
        )
        try {
            val parsed: ParseResult = when (source) {
                ImportSource.BROWSER_HISTORY -> BrowserHistoryParser.parse(text, browserSlug, jobId, clock.now())
                ImportSource.GOOGLE_ACTIVITY -> GoogleActivityParser.parse(text, jobId, clock.now())
            }
            val total = parsed.drafts.size + parsed.skipped
            val note = parsed.notes.joinToString(" ").ifEmpty { null }
            if (parsed.drafts.isEmpty()) {
                val msg = note ?: "No importable records found."
                system.updateJob(job(ImportStatus.FAILED, total, 0, 0, parsed.skipped, msg))
                return@withContext ImportSummary(jobId, total, 0, 0, parsed.skipped, msg)
            }
            val results = content.saveAll(parsed.drafts) { done -> onProgress(done, parsed.drafts.size) }
            val duplicates = results.count { it.duplicateEvent }
            val newEvents = results.size - duplicates
            content.rebuildRelations(results.filter { it.created }.map { it.contentId }.take(3000))
            system.updateJob(job(ImportStatus.DONE, total, newEvents, duplicates, parsed.skipped, note))
            ImportSummary(jobId, total, newEvents, duplicates, parsed.skipped, note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            system.updateJob(job(ImportStatus.FAILED, 0, 0, 0, 0, msg))
            ImportSummary(jobId, 0, 0, 0, 0, msg)
        }
    }
}
