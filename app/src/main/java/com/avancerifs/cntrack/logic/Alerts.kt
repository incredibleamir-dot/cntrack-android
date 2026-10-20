package com.avancerifs.cntrack.logic

import com.avancerifs.cntrack.data.ContainerRecord
import java.time.LocalDate

data class Alert(
    val cid: String = "",
    val job: String = "",
    val kind: String = "",
    val title: String = "",
    val detail: String = "",
    val rank: Int = 0
)

object Alerts {

    const val LFD_SOON_DAYS = 3L

    private val TITLES = mapOf(
        "hold" to "Customs HOLD",
        "lfd_expired" to "Free days expired",
        "delayed" to "ETA delayed",
        "lfd_soon" to "Free days expiring"
    )

    fun collectAlerts(
        tracking: Map<String, ContainerRecord>,
        jobOf: Map<String, String>,
        archived: Set<String>,
        today: LocalDate = LocalDate.now()
    ): List<Alert> {
        val out = mutableListOf<Alert>()
        for ((cid, rec) in tracking) {
            val job = jobOf[cid] ?: continue
            if (archived.contains(cid) || Completion.isCompleted(rec) || Completion.hasOutgate(rec)) continue
            val sm = rec.summary
            if (CnTime.customsStatus(sm.customsDescription) == "hold") {
                out.add(
                    Alert(
                        cid, job, "hold", TITLES["hold"]!!,
                        sm.customsDescription.trim().ifEmpty { "In bond" }, 0
                    )
                )
            }
            val dt = CnTime.parseCnRail(sm.lastFreeDay)
            if (dt != null) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, dt.toLocalDate())
                if (days < 0) {
                    out.add(
                        Alert(cid, job, "lfd_expired", TITLES["lfd_expired"]!!,
                            "Free days ended ${-days}d ago", 1)
                    )
                } else if (days <= LFD_SOON_DAYS) {
                    out.add(
                        Alert(cid, job, "lfd_soon", TITLES["lfd_soon"]!!,
                            if (days == 0L) "Due today" else "Due in ${days}d", 3)
                    )
                }
            }
            val analysis = Eta.analyzeEta(rec.eta)
            if (analysis.verdict == "Delayed") {
                out.add(Alert(cid, job, "delayed", TITLES["delayed"]!!, analysis.deltaText, 2))
            }
        }
        return out.sortedWith(compareBy({ it.rank }, { it.cid }))
    }

    fun alertSummary(alerts: List<Alert>): String {
        if (alerts.isEmpty()) return "No alerts"
        val counts = linkedMapOf<String, Int>()
        for (a in alerts) counts[a.title] = (counts[a.title] ?: 0) + 1
        return "${alerts.size} alert(s): " +
            counts.entries.sortedBy { it.key }.joinToString(", ") { "${it.value} ${it.key}" }
    }
}
