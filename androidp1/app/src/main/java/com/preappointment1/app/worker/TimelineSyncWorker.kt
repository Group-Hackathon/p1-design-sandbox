package com.preappointment1.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.preappointment1.app.data.repository.FollowUpRepository
import com.preappointment1.app.data.repository.TimelineRepository

class TimelineSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            TimelineRepository.pushAllPending()
            val followUps = FollowUpRepository.getLocalFollowUps()
            for (followUp in followUps) {
                TimelineRepository.refreshFromRemote(followUp.id)
            }
            try {
                val remote = com.preappointment1.app.data.api.ApiClient.apiService.getSubscriptions()
                FollowUpRepository.saveAllFromRemote(remote)
            } catch (_: Exception) {
                // Follow-ups stay local
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
