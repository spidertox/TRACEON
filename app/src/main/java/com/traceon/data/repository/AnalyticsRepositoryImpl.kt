package com.traceon.data.repository

import com.traceon.core.common.Clock
import com.traceon.core.common.TimeUtils
import com.traceon.data.local.dao.AnalyticsDao
import com.traceon.data.local.dao.ContentDao
import com.traceon.domain.model.DayCount
import com.traceon.domain.model.LabeledValue
import com.traceon.domain.repository.AnalyticsRepository
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

class AnalyticsRepositoryImpl(
    private val dao: AnalyticsDao,
    private val contentDao: ContentDao,
    private val clock: Clock,
) : AnalyticsRepository {
    private fun since(days: Int): Long = TimeUtils.startOfDay(TimeUtils.toLocalDate(clock.now()).minusDays(days - 1L))
    private fun offset(): Long = TimeZone.getDefault().getOffset(clock.now()).toLong()

    override fun totalItems() = dao.totalItems()
    override fun itemsByType() = dao.itemsByType()
    override fun eventsByType() = dao.eventsByType()
    override fun viewingMs() = dao.viewingMs()
    override fun readingMs() = dao.readingMs()
    override fun topApps(sinceMs: Long, limit: Int) = dao.topApps(sinceMs, limit)
    override fun topDomains(limit: Int) = dao.topDomains(limit)
    override fun topCategories(limit: Int) = dao.topCategories(limit)
    override fun topTopics(limit: Int) = dao.topTopics(limit)
    override fun platformItems() = dao.platformItems()
    override fun platformEvents() = dao.platformEvents()
    override fun browserActivity() = dao.browserActivity()
    override fun engineActivity() = dao.engineActivity()
    override fun searchCount() = dao.searchCount()
    override fun distinctPages() = dao.distinctPages()
    override fun distinctDomains() = dao.distinctDomains()
    override fun dailyActivity(days: Int): Flow<List<DayCount>> = dao.dailyActivity(since(days), offset())
    override fun dailyBrowsing(days: Int): Flow<List<DayCount>> = dao.dailyBrowsing(since(days), offset())
    override fun dailySearches(days: Int): Flow<List<DayCount>> = dao.dailySearches(since(days), offset())
    override fun repeatedPages(limit: Int): Flow<List<LabeledValue>> = dao.repeatedPages(limit)
    override fun demoCount() = contentDao.observeDemoCount()
}
