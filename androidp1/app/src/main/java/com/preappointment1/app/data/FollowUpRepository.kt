package com.preappointment1.app.data

import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.local.toEntity
import com.preappointment1.app.data.model.UpdateSubscriptionRequest
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.ui.screens.FollowUpUi
import com.preappointment1.app.ui.screens.toFollowUpUi

suspend fun updateFollowUpSchedule(
    followUpId: String,
    newSchedule: Map<String, List<String>>
): FollowUpUi? {
    val updated = ApiClient.apiService.patchSubscription(
        followUpId,
        UpdateSubscriptionRequest(
            parameters = mapOf("schedule" to newSchedule)
        )
    )
    FollowUpRepository.saveFromRemote(updated)
    val agents = ApiClient.apiService.getAgents().associateBy { it.id }
    return updated.toFollowUpUi(agents)
}
