package com.preappointment1.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.preappointment1.app.worker.TimelineSyncWorker

object SyncManager {
    private const val SYNC_WORK = "p1_timeline_sync"

    fun scheduleSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<TimelineSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
