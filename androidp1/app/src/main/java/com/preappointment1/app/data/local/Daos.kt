package com.preappointment1.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowUpDao {
    @Query("SELECT * FROM follow_ups ORDER BY startsAt DESC")
    fun observeAll(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups ORDER BY startsAt DESC")
    suspend fun getAll(): List<FollowUpEntity>

    @Query("SELECT * FROM follow_ups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FollowUpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FollowUpEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FollowUpEntity>)

    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TimelineEventDao {
    @Query(
        """
        SELECT * FROM timeline_events
        WHERE subscriptionId = :subscriptionId
        ORDER BY COALESCE(effectiveAt, createdAt) ASC
        """
    )
    fun observeForSubscription(subscriptionId: String): Flow<List<TimelineEventEntity>>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE subscriptionId = :subscriptionId
        ORDER BY COALESCE(effectiveAt, createdAt) ASC
        """
    )
    suspend fun getForSubscription(subscriptionId: String): List<TimelineEventEntity>

    @Query("SELECT * FROM timeline_events WHERE syncStatus = :status")
    suspend fun getBySyncStatus(status: String): List<TimelineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimelineEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TimelineEventEntity>)

    @Query("DELETE FROM timeline_events WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM timeline_events WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)

    @Query("DELETE FROM timeline_events WHERE subscriptionId = :subscriptionId AND syncStatus = :status")
    suspend fun deleteForSubscriptionByStatus(subscriptionId: String, status: String)

    @Query("DELETE FROM timeline_events WHERE subscriptionId = :subscriptionId")
    suspend fun deleteAllForSubscription(subscriptionId: String)

    @Query("SELECT COUNT(*) FROM timeline_events WHERE syncStatus = :status")
    fun observePendingCount(status: String = SyncStatus.PENDING): Flow<Int>
}

@Dao
interface CachedReportDao {
    @Query("SELECT * FROM cached_reports WHERE subscriptionId = :subscriptionId LIMIT 1")
    suspend fun getForSubscription(subscriptionId: String): CachedReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedReportEntity)

    @Query("DELETE FROM cached_reports WHERE subscriptionId = :subscriptionId")
    suspend fun deleteForSubscription(subscriptionId: String)
}

@Dao
interface LocalDocumentDao {
    @Query(
        """
        SELECT * FROM local_documents
        WHERE followUpId = :followUpId
        ORDER BY createdAt DESC
        """
    )
    fun observeForFollowUp(followUpId: String): Flow<List<LocalDocumentEntity>>

    @Query(
        """
        SELECT * FROM local_documents
        WHERE followUpId = :followUpId
        ORDER BY createdAt DESC
        """
    )
    suspend fun getForFollowUp(followUpId: String): List<LocalDocumentEntity>

    @Query("SELECT * FROM local_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalDocumentEntity)

    @Query("DELETE FROM local_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_documents WHERE followUpId = :followUpId")
    suspend fun deleteForFollowUp(followUpId: String)
}
