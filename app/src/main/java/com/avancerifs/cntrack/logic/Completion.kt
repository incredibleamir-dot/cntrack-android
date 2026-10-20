package com.avancerifs.cntrack.logic

import com.avancerifs.cntrack.data.ContainerRecord

object Completion {

    const val OUTGATE = "OUT-GATE"

    fun destinationOf(rec: ContainerRecord?): String {
        val sm = rec?.summary ?: return ""
        for (key in listOf(sm.destination, sm.etaStation)) {
            val v = key.trim()
            if (v.isNotEmpty()) return v.uppercase()
        }
        return ""
    }

    private fun isOutgate(desc: String): Boolean =
        desc.trim().uppercase() == OUTGATE

    fun hasOutgate(rec: ContainerRecord?): Boolean =
        rec?.activity?.any { isOutgate(it.eventDescription) } ?: false

    fun truncateAtOutgate(rec: ContainerRecord?): ContainerRecord {
        if (rec == null) return ContainerRecord()
        val acts = rec.activity
        if (acts.isEmpty()) return rec
        val dest = destinationOf(rec)
        val destMatches = mutableListOf<Pair<Int, java.time.ZonedDateTime?>>()
        val anyMatches = mutableListOf<Pair<Int, java.time.ZonedDateTime?>>()
        acts.forEachIndexed { idx, act ->
            if (isOutgate(act.eventDescription)) {
                val entry = idx to CnTime.parseCnRail(act.eventTime)
                anyMatches.add(entry)
                if (dest.isNotEmpty() && act.eventLocationStation.trim().uppercase() == dest) {
                    destMatches.add(entry)
                }
            }
        }
        val matches = if (destMatches.isNotEmpty()) destMatches else anyMatches
        if (matches.isEmpty()) return rec
        val timed = matches.mapNotNull { it.second }
        val cutoff = if (timed.isEmpty()) null else timed.min()
        val firstIdx = matches.minOf { it.first }
        val kept = mutableListOf<com.avancerifs.cntrack.data.Activity>()
        acts.forEachIndexed { idx, act ->
            val dt = CnTime.parseCnRail(act.eventTime)
            if (dt != null && cutoff != null) {
                if (!dt.isAfter(cutoff)) kept.add(act)
            } else if (idx <= firstIdx) {
                kept.add(act)
            }
        }
        val anchor = acts.firstOrNull {
            isOutgate(it.eventDescription) &&
                (destMatches.isEmpty() || it.eventLocationStation.trim().uppercase() == dest)
        }
        var sm = rec.summary
        if (anchor != null) {
            sm = sm.copy(
                lastEvent = anchor.eventDescription.ifEmpty { sm.lastEvent },
                lastEventLocation = anchor.eventLocationStation.ifEmpty { sm.lastEventLocation },
                lastEventTime = anchor.eventTime.ifEmpty { sm.lastEventTime }
            )
        }
        return rec.copy(activity = kept, summary = sm, completed = true)
    }

    fun isCompleted(rec: ContainerRecord?): Boolean = rec?.completed == true
}
