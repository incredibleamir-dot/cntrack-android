package com.avancerifs.cntrack.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.R
import com.avancerifs.cntrack.logic.CnTime
import com.avancerifs.cntrack.logic.Completion
import com.avancerifs.cntrack.logic.Eta
import com.avancerifs.cntrack.logic.MapData
import com.avancerifs.cntrack.logic.PdfExporter
import com.avancerifs.cntrack.ui.components.CustomsPill
import com.avancerifs.cntrack.ui.components.DetailKpi
import com.avancerifs.cntrack.ui.components.EmptyState
import com.avancerifs.cntrack.ui.components.Pill
import com.avancerifs.cntrack.ui.components.SectionCard
import com.avancerifs.cntrack.ui.components.StatusPill
import com.avancerifs.cntrack.ui.components.VerdictPill
import com.avancerifs.cntrack.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DetailScreen(vm: MainViewModel, cid: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scopeObj = scope
    val row = remember(cid, vm.tracking, vm.shipmentsTick) { vm.containerRow(cid) }
    val rec = remember(cid, vm.tracking) { vm.record(cid) }
    val analysis = remember(cid, vm.tracking) { Eta.analyzeEta(rec?.eta ?: emptyList()) }
    val closed = rec != null && (Completion.hasOutgate(rec) || Completion.isCompleted(rec))
    val hasData = rec != null && (
        rec.activity.isNotEmpty() || rec.eta.isNotEmpty() ||
            rec.customStatus.isNotEmpty() || rec.storage.isNotEmpty() ||
            rec.summary.lastEvent.isNotBlank() || rec.summary.etaTime.isNotBlank() ||
            rec.summary.waybillStatus.isNotBlank()
        )
    var activityOpen by remember(cid) { mutableStateOf(false) }
    var etaOpen by remember(cid) { mutableStateOf(false) }
    var activityPage by remember(cid) { mutableStateOf(0) }
    var etaPage by remember(cid) { mutableStateOf(0) }

    var mapData by remember(cid) { mutableStateOf<MapData?>(null) }
    LaunchedEffect(cid) { mapData = vm.mapData(cid) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hasData) {
            item {
                SectionCard("Port ETA", AppColors.accentDetails) {
                    DetailKpi("Port ETA", fmtPortEta(row.etaPortDate).ifBlank { "—" })
                    if (row.etaStation.isNotBlank() && row.etaStation != "-") {
                        Spacer(Modifier.height(8.dp))
                        DetailKpi("Destination", row.etaStation)
                    }
                }
            }
            item {
                Surface(
                    color = AppColors.warningBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No CN tracking yet — it may still be on the water, awaiting a move order, or travelling via another rail service (e.g., CP Rail) or by truck.",
                        color = AppColors.warningInk, fontSize = 13.sp, modifier = Modifier.padding(12.dp)
                    )
                }
            }
            item {
                OutlinedButton(onClick = { vm.toggleArchive(cid) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (row.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (row.archived) "Restore" else "Archive")
                }
            }
        } else {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(row.status)
                VerdictPill(row.verdict)
                Spacer(Modifier.weight(1f))
                Text(row.job.ifBlank { "Unassigned" }, color = AppColors.inkMuted, fontSize = 12.sp)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.toggleArchive(cid) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (row.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (row.archived) "Restore" else "Archive")
                }
                Button(
                    onClick = {
                        scopeObj.launch {
                            val r = rec ?: return@launch
                            val file = withContext(Dispatchers.IO) {
                                PdfExporter.exportContainer(context, cid, row.job, r)
                            }
                            val uri = FileProvider.getUriForFile(
                                context, context.packageName + ".fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "CNTrack — $cid")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share PDF")
                }
            }
        }

        item {
            SectionCard("Overview", AppColors.accentDetails) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VerdictPill(row.verdict)
                    CustomsPill(row.customsState)
                    Pill(row.src, AppColors.neutralInk, AppColors.neutralBg)
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailKpi("Location", row.location, Modifier.weight(1f))
                        DetailKpi("Destination", row.etaStation, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailKpi("Last event", row.lastEvent, Modifier.weight(1f))
                        DetailKpi("Event time", row.eventTime, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailKpi("ETA", row.eta, Modifier.weight(1f))
                        DetailKpi("Verdict change", analysis.deltaText, Modifier.weight(1f))
                    }
                    if (!closed) DetailKpi("Last free day", row.lfd)
                    DetailKpi(
                        "Customs",
                        row.customs,
                        valueColor = when (row.customsState) {
                            "cleared" -> AppColors.successInk
                            "hold" -> AppColors.dangerInk
                            "not_released" -> AppColors.dangerInk
                            else -> AppColors.ink
                        }
                    )
                }
            }
        }

        item {
            SectionCard("Route", AppColors.accentDetails) {
                RouteMap(mapData, Modifier.fillMaxWidth().height(300.dp))
                Spacer(Modifier.height(10.dp))
                val p = mapData?.progress
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = AppColors.infoFill,
                        trackColor = AppColors.line
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(mapData?.progressLabel ?: "Loading route…", color = AppColors.ink, fontSize = 13.sp)
                val md = mapData
                if (md != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Origin: ${md.origin.ifBlank { "-" }}", color = AppColors.inkMuted, fontSize = 11.sp)
                    Text("Current: ${md.current.ifBlank { "-" }}", color = AppColors.inkMuted, fontSize = 11.sp)
                    Text("Destination: ${md.destination.ifBlank { "-" }}", color = AppColors.inkMuted, fontSize = 11.sp)
                    if (md.unresolved.isNotEmpty()) {
                        Text("No GPS for: ${md.unresolved.joinToString(", ")}", color = AppColors.warningInk, fontSize = 11.sp)
                    }
                }
            }
        }

        val customsRows = rec?.customStatus
        if (!customsRows.isNullOrEmpty()) {
            item {
                SectionCard("Customs status", AppColors.accentDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customsRows.forEach { c ->
                            val (fg, bg) = when (CnTime.customsStatus(c.description)) {
                                "cleared" -> AppColors.successInk to AppColors.successBg
                                "hold" -> AppColors.dangerInk to AppColors.dangerBg
                                "unknown" -> AppColors.warningInk to AppColors.warningBg
                                else -> AppColors.neutralInk to AppColors.neutralBg
                            }
                            Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(c.description.ifBlank { "-" }, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(CnTime.dashboardTime(c.timestamp, "ET"), color = fg, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        val storageRows = rec?.storage
        if (!closed && !storageRows.isNullOrEmpty()) {
            item {
                SectionCard("Storage / free days", AppColors.warningFill) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        storageRows.forEach { s ->
                            DetailKpi("Last free day", CnTime.dashboardLfd(s.lastFreeDay))
                        }
                    }
                }
            }
        }

        val acts = rec?.activity ?: emptyList()
        item {
            ExpandHeader(
                "Activity (${acts.size})",
                if (activityOpen) "Tap to hide events" else "Tap to show events",
                AppColors.accentContainers,
                activityOpen
            ) { activityOpen = !activityOpen }
        }
        if (acts.isEmpty()) {
            item { EmptyState("No activity yet.") }
        } else if (activityOpen) {
            val pageCount = (acts.size + PAGE_SIZE - 1) / PAGE_SIZE
            val page = activityPage.coerceIn(0, pageCount - 1)
            val start = page * PAGE_SIZE
            val end = minOf(start + PAGE_SIZE, acts.size)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in start until end) {
                        val a = acts[i]
                        val (local, date) = CnTime.displayTime(a.eventTime, "")
                        SubRow {
                            Text(a.eventDescription.ifBlank { "-" }, color = AppColors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(a.eventLocationStation.ifBlank { "-" }, color = AppColors.inkMuted, fontSize = 12.sp)
                            Text("$local  |  $date", color = AppColors.inkMuted, fontSize = 11.sp)
                        }
                    }
                    if (pageCount > 1) {
                        Spacer(Modifier.height(4.dp))
                        Pager(page, pageCount) { activityPage = it }
                    }
                }
            }
        }

        val etaRows = rec?.eta ?: emptyList()
        item {
            ExpandHeader(
                "ETA revisions (${etaRows.size})",
                if (etaOpen) "Tap to hide revisions" else "Tap to show revisions",
                AppColors.accentAnalytics,
                etaOpen
            ) { etaOpen = !etaOpen }
        }
        if (etaRows.isEmpty()) {
            item { EmptyState("No ETA records.") }
        } else if (etaOpen) {
            val deltas = Eta.revisionDeltas(etaRows)
            val pageCount = (etaRows.size + PAGE_SIZE - 1) / PAGE_SIZE
            val page = etaPage.coerceIn(0, pageCount - 1)
            val start = page * PAGE_SIZE
            val end = minOf(start + PAGE_SIZE, etaRows.size)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in start until end) {
                        val e = etaRows[i]
                        val (local, date) = CnTime.displayTime(e.etaTime, "")
                        val delta = deltas.getOrNull(i)
                        SubRow {
                            Text(
                                "${e.etaLocationStation.ifBlank { "-" }} → ${e.destinationStation.ifBlank { "-" }}",
                                color = AppColors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text("$local  |  $date", color = AppColors.inkMuted, fontSize = 11.sp)
                            if (i > 0 && delta != null) {
                                Spacer(Modifier.height(4.dp))
                                Pill(Eta.formatDelta(delta), AppColors.warningInk, AppColors.warningBg)
                            }
                        }
                    }
                    if (pageCount > 1) {
                        Spacer(Modifier.height(4.dp))
                        Pager(page, pageCount) { etaPage = it }
                    }
                }
            }
        }
        }
    }
}

private val PORT_ETA_IN: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val PORT_ETA_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

private fun fmtPortEta(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    return runCatching { LocalDate.parse(s.take(10), PORT_ETA_IN).format(PORT_ETA_OUT) }.getOrDefault(s)
}

private const val PAGE_SIZE = 20

@Composable
private fun Pager(page: Int, pageCount: Int, onPage: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text("‹ Prev") }
        Text("${page + 1} / $pageCount", color = AppColors.inkMuted, fontSize = 12.sp)
        OutlinedButton(onClick = { onPage(page + 1) }, enabled = page < pageCount - 1) { Text("Next ›") }
    }
}

@Composable
private fun ExpandHeader(
    title: String,
    subtitle: String,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = AppColors.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 18.dp)
                    .background(accent, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AppColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = AppColors.inkMuted, fontSize = 11.sp)
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = AppColors.inkMuted
            )
        }
    }
}

@Composable
private fun SubRow(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = AppColors.surface2,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun RouteMap(data: MapData?, modifier: Modifier = Modifier) {
    val painted = remember { arrayOfNulls<MapData>(1) }
    val latest = remember { arrayOfNulls<MapData>(1) }
    latest[0] = data
    Surface(color = AppColors.surface2, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        AndroidView(
            factory = { c ->
                MapView(c).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(4.0)
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        val mv = v as MapView
                        if (mv.width > 0 && mv.height > 0 && painted[0] !== latest[0]) {
                            mv.post { paintRoute(mv, latest[0], painted) }
                        }
                    }
                }
            },
            onRelease = { it.onDetach() },
            modifier = Modifier.fillMaxSize()
        ) { mv ->
            mv.post { if (mv.width > 0 && mv.height > 0) paintRoute(mv, latest[0], painted) }
        }
    }
}

private fun paintRoute(mv: MapView, data: MapData?, painted: Array<MapData?>) {
    if (painted[0] === data) return
    painted[0] = data
    mv.overlays.clear()
    val pts = mutableListOf<GeoPoint>()
    if (data != null) {
        data.travelSegments.forEach { seg ->
            if (seg.size > 1) {
                val pl = Polyline(mv)
                pl.setPoints(seg.map { GeoPoint(it.lat, it.lon) })
                pl.outlinePaint.color = android.graphics.Color.rgb(214, 39, 40)
                pl.outlinePaint.strokeWidth = 8f
                mv.overlays.add(pl)
                pts.addAll(seg.map { GeoPoint(it.lat, it.lon) })
            }
        }
        data.remainingSegments.forEach { seg ->
            if (seg.size > 1) {
                val pl = Polyline(mv)
                pl.setPoints(seg.map { GeoPoint(it.lat, it.lon) })
                pl.outlinePaint.color = android.graphics.Color.rgb(44, 160, 44)
                pl.outlinePaint.strokeWidth = 8f
                mv.overlays.add(pl)
                pts.addAll(seg.map { GeoPoint(it.lat, it.lon) })
            }
        }
        data.travel.lastOrNull { it.latitude != null && it.longitude != null }?.let {
            addMarker(mv, GeoPoint(it.latitude!!, it.longitude!!), "Current: ${data.current}", R.drawable.ic_map_current).also { m -> pts.add(m) }
        }
        data.travel.firstOrNull { it.latitude != null && it.longitude != null }?.let {
            addMarker(mv, GeoPoint(it.latitude!!, it.longitude!!), "Origin: ${data.origin}", R.drawable.ic_map_origin).also { m -> pts.add(m) }
        }
        data.remaining.lastOrNull { it.latitude != null && it.longitude != null }?.let {
            addMarker(mv, GeoPoint(it.latitude!!, it.longitude!!), "Destination: ${data.destination}", R.drawable.ic_map_dest).also { m -> pts.add(m) }
        }
        data.podCoords?.let {
            addMarker(mv, GeoPoint(it.lat, it.lon), "POD: ${data.pod}", R.drawable.ic_map_pod).also { m -> pts.add(m) }
        }
    }
    if (pts.isNotEmpty()) {
        runCatching {
            val box = BoundingBox.fromGeoPoints(pts).increaseByScale(1.25f)
            val flat = box.latNorth - box.latSouth < 1e-6 || box.lonEast - box.lonWest < 1e-6
            if (flat) {
                mv.controller.setCenter(GeoPoint(box.centerLatitude, box.centerLongitude))
                mv.controller.setZoom(9.0)
            } else {
                mv.zoomToBoundingBox(box, false)
            }
        }.onFailure {
            mv.controller.setCenter(pts.first())
            mv.controller.setZoom(5.0)
        }
    }
    mv.invalidate()
}

private fun addMarker(mv: MapView, point: GeoPoint, title: String, iconRes: Int): GeoPoint {
    val marker = Marker(mv).apply {
        position = point
        this.title = title
        icon = ContextCompat.getDrawable(mv.context, iconRes)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }
    mv.overlays.add(marker)
    return point
}
