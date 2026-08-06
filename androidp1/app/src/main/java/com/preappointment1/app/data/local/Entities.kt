package com.preappointment1.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follow_ups")
data class FollowUpEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val agentId: String,
    val status: String,
    val startsAt: String,
    val expiresAt: String,
    val parametersJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val subscriptionId: String,
    val type: String,
    val dateLabel: String,
    val content: String,
    val createdAt: String,
    val effectiveAt: String?,
    val syncStatus: String = SyncStatus.SYNCED
)

@Entity(tableName = "cached_reports")
data class CachedReportEntity(
    @PrimaryKey val subscriptionId: String,
    val pdfPath: String,
    val generatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_documents")
data class LocalDocumentEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val title: String,
    val mimeType: String,
    val relativePath: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis()
)

object DocumentSource {
    const val REPORT = "report"
    const val PHOTO = "photo"
    const val PDF = "pdf"
    const val OTHER = "other"
}
