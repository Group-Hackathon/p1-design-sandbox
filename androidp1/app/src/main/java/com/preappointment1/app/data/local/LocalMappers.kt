package com.preappointment1.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.preappointment1.app.data.model.SubscriptionResponse
import com.preappointment1.app.data.model.TimelineEventRequest
import com.preappointment1.app.data.model.TimelineEventResponse
import com.preappointment1.app.ui.screens.FollowUpUi
import com.preappointment1.app.ui.screens.toFollowUpUi
import java.time.Instant
import java.util.UUID

private val gson = Gson()

fun SubscriptionResponse.toEntity(): FollowUpEntity {
    return FollowUpEntity(
        id = id,
        profileId = profile_id,
        agentId = agent_id,
        status = status,
        startsAt = starts_at,
        expiresAt = expires_at,
        parametersJson = gson.toJson(parameters ?: emptyMap<String, Any>())
    )
}

fun FollowUpEntity.toSubscriptionResponse(): SubscriptionResponse {
    val paramsType = object : TypeToken<Map<String, Any>>() {}.type
    val parameters: Map<String, Any>? = gson.fromJson(parametersJson, paramsType)
    return SubscriptionResponse(
        id = id,
        profile_id = profileId,
        agent_id = agentId,
        status = status,
        private_backend_url = "",
        starts_at = startsAt,
        expires_at = expiresAt,
        parameters = parameters
    )
}

fun FollowUpEntity.toFollowUpUi(agents: Map<String, com.preappointment1.app.data.model.AgentResponse> = emptyMap()): FollowUpUi {
    return toSubscriptionResponse().toFollowUpUi(agents)
}

fun TimelineEventResponse.toEntity(syncStatus: String = SyncStatus.SYNCED): TimelineEventEntity {
    return TimelineEventEntity(
        localId = id,
        remoteId = id,
        subscriptionId = subscription_id,
        type = type,
        dateLabel = date_label,
        content = content,
        createdAt = created_at,
        effectiveAt = effective_at,
        syncStatus = syncStatus
    )
}

fun TimelineEventEntity.toResponse(): TimelineEventResponse {
    return TimelineEventResponse(
        id = remoteId ?: localId,
        subscription_id = subscriptionId,
        type = type,
        date_label = dateLabel,
        content = content,
        created_at = createdAt,
        effective_at = effectiveAt
    )
}

fun TimelineEventRequest.toPendingEntity(
    subscriptionId: String,
    localId: String = UUID.randomUUID().toString(),
    createdAt: String = Instant.now().toString()
): TimelineEventEntity {
    val effectiveAt = effective_date?.let { date ->
        runCatching { Instant.parse("${date}T12:00:00Z").toString() }.getOrNull()
            ?: runCatching { Instant.parse(date).toString() }.getOrNull()
    }
    return TimelineEventEntity(
        localId = localId,
        remoteId = null,
        subscriptionId = subscriptionId,
        type = "user",
        dateLabel = date_label,
        content = content,
        createdAt = createdAt,
        effectiveAt = effectiveAt,
        syncStatus = SyncStatus.PENDING
    )
}
