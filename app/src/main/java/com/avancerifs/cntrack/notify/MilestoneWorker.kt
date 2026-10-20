package com.avancerifs.cntrack.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.avancerifs.cntrack.data.ContainerRecord
import com.avancerifs.cntrack.data.Repository
import com.avancerifs.cntrack.logic.CnTime
import com.avancerifs.cntrack.logic.Milestones

class MilestoneWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Notifier.ensureChannel(context)
        val repo = Repository(context)
        val ids = repo.allContainerIds()
        if (ids.isEmpty()) return Result.success()

        val res = runCatching { repo.fetchIds(ids, force = true) }.getOrNull() ?: return Result.retry()

        for (cid in ids) {
            val rec = res.results[cid] ?: continue
            handle(context, repo, cid, rec)
        }
        return Result.success()
    }

    private fun handle(context: Context, repo: Repository, cid: String, rec: ContainerRecord) {
        val present = Milestones.detect(rec)
        if (present.isEmpty()) return
        val db = repo.db
        val seen = db.isNotifySeen(cid)
        val recorded = db.recordedMilestones(cid)
        val job = repo.jobOf(cid)

        for ((milestone, hit) in present) {
            if (milestone in recorded) continue
            db.recordMilestone(cid, milestone, hit.time)
            if (seen) {
                val (local, date) = CnTime.displayTime(hit.time, "")
                val whenText = listOf(local, date).filter { it.isNotBlank() && it != "-" }.joinToString("  |  ")
                Notifier.notify(context, cid, milestone, job, hit.location, whenText.ifBlank { hit.time })
            }
        }
        if (!seen) db.markNotifySeen(cid)
    }
}
