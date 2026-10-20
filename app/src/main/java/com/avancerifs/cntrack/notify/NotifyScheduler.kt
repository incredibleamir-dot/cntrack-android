package com.avancerifs.cntrack.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotifyScheduler {

    private const val PERIODIC = "cn_milestones_periodic"

    private fun constraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<MilestoneWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<MilestoneWorker>()
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
