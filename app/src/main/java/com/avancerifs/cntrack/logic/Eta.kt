package com.avancerifs.cntrack.logic

import com.avancerifs.cntrack.data.EtaRow
import java.time.Duration
import java.time.ZonedDateTime

data class EtaAnalysis(
    val revisions: List<Pair<String, ZonedDateTime?>> = emptyList(),
    val originalRaw: String = "",
    val originalDt: ZonedDateTime? = null,
    val currentRaw: String = "",
    val currentDt: ZonedDateTime? = null,
    val delta: Duration? = null,
    val deltaText: String = "—",
    val verdict: String = "Unknown",
    val destination: String = "-"
)

object Eta {

    fun formatDelta(td: Duration?): String {
        if (td == null) return "—"
        var totalMin = Math.round(td.seconds / 60.0).toInt()
        if (totalMin == 0) return "±0m"
        val sign = if (totalMin > 0) "+" else "-"
        totalMin = kotlin.math.abs(totalMin)
        val days = totalMin / 1440
        val rem = totalMin % 1440
        val hours = rem / 60
        val mins = rem % 60
        val parts = mutableListOf<String>()
        if (days != 0) parts.add("${days}d")
        if (hours != 0 || days != 0) parts.add("${hours}h")
        parts.add(if (hours != 0 || days != 0) "%02dm".format(mins) else "${mins}m")
        return sign + parts.joinToString(" ")
    }

    private fun parseRevisions(etaRows: List<EtaRow>): List<Pair<String, ZonedDateTime?>> =
        etaRows.map { it.etaTime to CnTime.parseCnRail(it.etaTime) }

    fun analyzeEta(etaRows: List<EtaRow>): EtaAnalysis {
        val revs = parseRevisions(etaRows)
        val parsed = revs.filter { it.second != null }
        var dest = "-"
        var found = false
        for (e in etaRows.reversed()) {
            val d = e.destinationStation.trim()
            if (d.isNotEmpty()) {
                dest = d
                found = true
                break
            }
        }
        if (!found) {
            for (e in etaRows.reversed()) {
                val d = e.etaLocationStation.trim()
                if (d.isNotEmpty()) {
                    dest = d
                    break
                }
            }
        }
        if (parsed.size < 2) {
            val one = parsed.firstOrNull() ?: ("" to null)
            return EtaAnalysis(
                revisions = revs, originalRaw = one.first, originalDt = one.second,
                currentRaw = one.first, currentDt = one.second,
                delta = null, deltaText = "—", verdict = "Unknown", destination = dest
            )
        }
        val (originalRaw, originalDt) = parsed.first()
        val (currentRaw, currentDt) = parsed.last()
        val delta = Duration.between(originalDt, currentDt)
        val verdict = when {
            delta.isZero -> "On Time"
            delta.isNegative -> "Early"
            else -> "Delayed"
        }
        return EtaAnalysis(
            revisions = revs, originalRaw = originalRaw, originalDt = originalDt,
            currentRaw = currentRaw, currentDt = currentDt,
            delta = delta, deltaText = formatDelta(delta), verdict = verdict, destination = dest
        )
    }

    fun revisionDeltas(etaRows: List<EtaRow>): List<Duration?> {
        val revs = parseRevisions(etaRows)
        val out = mutableListOf<Duration?>()
        var prev: ZonedDateTime? = null
        for ((_raw, dt) in revs) {
            out.add(if (prev == null || dt == null) null else Duration.between(prev, dt))
            if (dt != null) prev = dt
        }
        if (out.isNotEmpty()) out[0] = null
        return out
    }

    fun verdictFor(etaRows: List<EtaRow>): String = analyzeEta(etaRows).verdict
}
