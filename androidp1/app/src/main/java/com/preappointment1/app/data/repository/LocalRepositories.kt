package com.preappointment1.app.data.repository

import android.content.Context
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.local.AppDatabase
import com.preappointment1.app.data.local.SyncStatus
import com.preappointment1.app.data.local.toEntity
import com.preappointment1.app.data.local.toFollowUpUi as entityToFollowUpUi
import com.preappointment1.app.data.local.toPendingEntity
import com.preappointment1.app.data.local.toResponse
import com.preappointment1.app.ui.screens.toFollowUpUi
import com.preappointment1.app.data.model.AgentResponse
import com.preappointment1.app.data.model.SubscriptionResponse
import com.preappointment1.app.data.model.TimelineEventRequest
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.data.sync.SyncManager
import com.preappointment1.app.ui.screens.FollowUpUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object FollowUpRepository {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val db get() = AppDatabase.get(appContext)
    private val followUpDao get() = db.followUpDao()

    fun observeFollowUps(): Flow<List<FollowUpUi>> {
        return followUpDao.observeAll().map { entities ->
            entities.map { it.entityToFollowUpUi() }
        }
    }

    suspend fun getLocalFollowUps(): List<FollowUpUi> {
        return followUpDao.getAll().map { it.entityToFollowUpUi() }
    }

    suspend fun saveFromRemote(subscription: SubscriptionResponse) {
        followUpDao.upsert(subscription.toEntity())
    }

    suspend fun saveAllFromRemote(subscriptions: List<SubscriptionResponse>) {
        followUpDao.upsertAll(subscriptions.map { it.toEntity() })
    }

    suspend fun deleteLocal(id: String) {
        followUpDao.deleteById(id)
        db.timelineEventDao().deleteAllForSubscription(id)
        db.cachedReportDao().deleteForSubscription(id)
        db.localDocumentDao().deleteForFollowUp(id)
        val docsDir = java.io.File(appContext.filesDir, "documents/$id")
        if (docsDir.exists()) docsDir.deleteRecursively()
    }

    /**
     * Load local data first, then refresh from API when possible.
     * Returns local list on network failure.
     */
    suspend fun loadFollowUpsWithSync(): Pair<List<FollowUpUi>, Boolean> {
        var synced = false
        try {
            val subscriptions = ApiClient.apiService.getSubscriptions()
            saveAllFromRemote(subscriptions)
            val agents = ApiClient.apiService.getAgents().associateBy { it.id }
            synced = true
            SyncManager.scheduleSync(appContext)
            return subscriptions.map { sub -> sub.toFollowUpUi(agents) } to synced
        } catch (_: Exception) {
            return getLocalFollowUps() to synced
        }
    }

    suspend fun getOrCreateActiveFollowUp(): FollowUpUi {
        val existing = getLocalFollowUps().firstOrNull { it.daysRemaining > 0 } ?: getLocalFollowUps().firstOrNull()
        if (existing != null) return existing

        val defaultId = "local_${System.currentTimeMillis()}"
        val entity = com.preappointment1.app.data.local.FollowUpEntity(
            id = defaultId,
            profileId = "local_profile",
            agentId = "default_agent",
            status = "active",
            startsAt = java.time.LocalDate.now().toString(),
            expiresAt = java.time.LocalDate.now().plusDays(14).toString(),
            parametersJson = "{\"title\":\"Consultation File\",\"rules\":{\"pain\":true,\"photos\":true,\"temperature\":true},\"schedule\":{\"08:00\":[\"pain\",\"temperature\"],\"20:00\":[\"pain\"]}}"
        )
        followUpDao.upsert(entity)
        return entity.entityToFollowUpUi()
    }

    suspend fun getAgentsOrEmpty(): Map<String, AgentResponse> {
        return try {
            ApiClient.apiService.getAgents().associateBy { it.id }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

object TimelineRepository {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val db get() = AppDatabase.get(appContext)
    private val eventDao get() = db.timelineEventDao()

    fun observeEvents(subscriptionId: String): Flow<List<TimelineEventResponse>> {
        return eventDao.observeForSubscription(subscriptionId).map { list ->
            list.map { it.toResponse() }
        }
    }

    fun observePendingCount(): Flow<Int> {
        return eventDao.observePendingCount(SyncStatus.PENDING)
    }

    suspend fun getEvents(subscriptionId: String): List<TimelineEventResponse> {
        return eventDao.getForSubscription(subscriptionId).map { it.toResponse() }
    }

    suspend fun addEvent(subscriptionId: String, request: TimelineEventRequest): TimelineEventResponse {
        val entity = request.toPendingEntity(subscriptionId)
        eventDao.upsert(entity)
        SyncManager.scheduleSync(appContext)
        return entity.toResponse()
    }

    suspend fun deleteEvent(subscriptionId: String, eventId: String) {
        val events = eventDao.getForSubscription(subscriptionId)
        val target = events.find { it.remoteId == eventId || it.localId == eventId } ?: return
        if (target.syncStatus == SyncStatus.PENDING) {
            eventDao.deleteByLocalId(target.localId)
            return
        }
        val remoteId = target.remoteId ?: return
        try {
            ApiClient.apiService.deleteTimelineEvent(subscriptionId, remoteId)
        } catch (_: Exception) {
            // Keep local copy if offline delete fails
            return
        }
        eventDao.deleteByRemoteId(remoteId)
        refreshFromRemote(subscriptionId)
    }

    suspend fun refreshFromRemote(subscriptionId: String): Boolean {
        return try {
            val remote = ApiClient.apiService.getTimeline(subscriptionId)
            mergeRemoteEvents(subscriptionId, remote)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun mergeRemoteEvents(subscriptionId: String, remote: List<TimelineEventResponse>) {
        eventDao.deleteForSubscriptionByStatus(subscriptionId, SyncStatus.SYNCED)
        eventDao.upsertAll(remote.map { it.toEntity(SyncStatus.SYNCED) })
    }

    suspend fun pushPendingForSubscription(subscriptionId: String): Int {
        val pending = eventDao.getForSubscription(subscriptionId)
            .filter { it.syncStatus == SyncStatus.PENDING && it.type == "user" }
        var pushed = 0
        for (event in pending) {
            try {
                ApiClient.apiService.postTimelineEvent(
                    subscriptionId,
                    TimelineEventRequest(
                        content = event.content,
                        date_label = event.dateLabel,
                        effective_date = event.effectiveAt?.take(10)
                    )
                )
                eventDao.deleteByLocalId(event.localId)
                pushed++
            } catch (_: Exception) {
                // Remain pending
            }
        }
        if (pushed > 0) {
            refreshFromRemote(subscriptionId)
        }
        return pushed
    }

    suspend fun pushAllPending(): Int {
        val pending = eventDao.getBySyncStatus(SyncStatus.PENDING)
        val subscriptionIds = pending.map { it.subscriptionId }.distinct()
        var total = 0
        for (subId in subscriptionIds) {
            total += pushPendingForSubscription(subId)
        }
        return total
    }
}

object ReportRepository {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val db get() = AppDatabase.get(appContext)

    suspend fun getCachedReportPath(subscriptionId: String): String? {
        return db.cachedReportDao().getForSubscription(subscriptionId)?.pdfPath
    }

    suspend fun cacheReport(subscriptionId: String, pdfPath: String) {
        db.cachedReportDao().upsert(
            com.preappointment1.app.data.local.CachedReportEntity(
                subscriptionId = subscriptionId,
                pdfPath = pdfPath
            )
        )
    }

    fun reportsDir(): java.io.File {
        val dir = java.io.File(appContext.filesDir, "reports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
