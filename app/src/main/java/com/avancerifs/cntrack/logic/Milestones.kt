package com.avancerifs.cntrack.logic

import com.avancerifs.cntrack.data.ContainerRecord

object Milestones {

    data class Hit(val time: String, val location: String, val event: String)

    private val GROUPS: List<Pair<String, Set<String>>> = listOf(
        "RAMPED" to setOf("RAMPED"),
        "DERAMPED" to setOf("DERAMPED"),
        "IN-GATE" to setOf("IN-GATE"),
        "OUT-GATE" to setOf("OUT-GATE")
    )

    fun detect(rec: ContainerRecord?): Map<String, Hit> {
        val out = LinkedHashMap<String, Hit>()
        if (rec == null) return out
        for (a in rec.activity) {
            val d = a.eventDescription.trim().uppercase()
            for ((key, names) in GROUPS) {
                if (d in names) {
                    val prev = out[key]
                    if (prev == null || a.eventTime >= prev.time) {
                        out[key] = Hit(a.eventTime, a.eventLocationStation.trim(), a.eventDescription.trim())
                    }
                }
            }
        }
        return out
    }
}
