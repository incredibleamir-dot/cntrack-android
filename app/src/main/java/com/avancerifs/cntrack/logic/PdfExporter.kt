package com.avancerifs.cntrack.logic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.avancerifs.cntrack.data.ContainerRecord
import com.avancerifs.cntrack.logic.CnTime
import com.avancerifs.cntrack.logic.Eta
import java.io.File

object PdfExporter {

    private const val PW = 595
    private const val PH = 842

    fun exportContainer(context: Context, cid: String, job: String, rec: ContainerRecord): File {
        val doc = PdfDocument()
        val title = Paint().apply { color = Color.rgb(29, 51, 80); textSize = 18f; isFakeBoldText = true }
        val head = Paint().apply { color = Color.rgb(14, 124, 123); textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { color = Color.rgb(55, 48, 74); textSize = 10f }
        val small = Paint().apply { color = Color.rgb(110, 100, 134); textSize = 9f }
        val line = Paint().apply { color = Color.rgb(229, 213, 190); strokeWidth = 1f }

        val sm = rec.summary
        val analysis = Eta.analyzeEta(rec.eta)
        val state = CnTime.customsStatus(sm.customsDescription)

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pageNo).create())
        var canvas = page.canvas
        var y = 56f

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pageNo).create())
            canvas = page.canvas
            y = 56f
        }

        fun ensure(space: Float) { if (y + space > PH - 48) newPage() }

        canvas.drawText("CNTrack — Container $cid", 40f, y, title)
        y += 22f
        canvas.drawText("Shipment: ${job.ifBlank { "-" }}", 40f, y, small); y += 14f
        canvas.drawText("Generated: ${java.time.LocalDateTime.now()}", 40f, y, small); y += 20f

        canvas.drawText("STATUS", 40f, y, head); y += 16f
        listOf(
            "Waybill status" to rec.summary.waybillStatus.ifBlank { "-" },
            "Last event" to sm.lastEvent.ifBlank { "-" },
            "Location" to sm.lastEventLocation.ifBlank { "-" },
            "Event time" to CnTime.dashboardTime(sm.lastEventTime, ""),
            "Destination" to (sm.destination.ifBlank { sm.etaStation }).ifBlank { "-" },
            "ETA" to CnTime.dashboardTime(sm.etaTime, ""),
            "ETA verdict" to "${analysis.verdict} (${analysis.deltaText})",
            "Last free day" to CnTime.dashboardLfd(sm.lastFreeDay),
            "Customs" to sm.customsDescription.ifBlank { "-" },
            "Customs state" to state
        ).forEach { (k, v) ->
            ensure(16f)
            canvas.drawText("$k:", 40f, y, body)
            canvas.drawText(v, 170f, y, body)
            y += 15f
        }
        y += 8f

        ensure(30f)
        canvas.drawText("ACTIVITY (${rec.activity.size})", 40f, y, head); y += 16f
        if (rec.activity.isEmpty()) {
            canvas.drawText("No activity yet.", 40f, y, small); y += 16f
        } else {
            for (a in rec.activity) {
                ensure(28f)
                val (local, date) = CnTime.displayTime(a.eventTime, "")
                canvas.drawText("${a.eventDescription}  @  ${a.eventLocationStation}", 40f, y, body); y += 13f
                canvas.drawText("$local  |  $date", 48f, y, small); y += 15f
                canvas.drawLine(40f, y - 4f, PW - 40f, y - 4f, line)
                y += 4f
            }
        }
        y += 8f

        ensure(30f)
        canvas.drawText("ETA REVISIONS (${rec.eta.size})", 40f, y, head); y += 16f
        val deltas = Eta.revisionDeltas(rec.eta)
        if (rec.eta.isEmpty()) {
            canvas.drawText("No ETA records.", 40f, y, small); y += 16f
        } else {
            rec.eta.forEachIndexed { i, e ->
                ensure(28f)
                val (local, date) = CnTime.displayTime(e.etaTime, "")
                val delta = deltas.getOrNull(i)
                val deltaText = if (i == 0 || delta == null) "" else Eta.formatDelta(delta)
                canvas.drawText("${e.etaLocationStation}  →  ${e.destinationStation.ifBlank { "-" }}", 40f, y, body); y += 13f
                canvas.drawText("$local  |  $date  $deltaText", 48f, y, small); y += 15f
            }
        }

        if (rec.customStatus.isNotEmpty()) {
            y += 8f
            ensure(30f)
            canvas.drawText("CUSTOMS HOLDS", 40f, y, head); y += 16f
            for (c in rec.customStatus) {
                ensure(26f)
                canvas.drawText(c.description.ifBlank { "-" }, 40f, y, body); y += 13f
                canvas.drawText(CnTime.dashboardTime(c.timestamp, "ET"), 48f, y, small); y += 15f
            }
        }

        doc.finishPage(page)

        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, "CNTrack_${cid}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
