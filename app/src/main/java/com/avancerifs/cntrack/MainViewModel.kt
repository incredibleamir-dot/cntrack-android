package com.avancerifs.cntrack

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avancerifs.cntrack.data.CnApi
import com.avancerifs.cntrack.data.ContainerRecord
import com.avancerifs.cntrack.data.Db
import com.avancerifs.cntrack.data.Repository
import com.avancerifs.cntrack.data.Shipment
import com.avancerifs.cntrack.logic.Alert
import com.avancerifs.cntrack.logic.Alerts
import com.avancerifs.cntrack.logic.CnTime
import com.avancerifs.cntrack.logic.Completion
import com.avancerifs.cntrack.logic.Eta
import com.avancerifs.cntrack.logic.GeoPoint
import com.avancerifs.cntrack.logic.MapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ContainerRow(
    val cid: String = "",
    val job: String = "",
    val status: String = "no data yet",
    val lastEvent: String = "-",
    val location: String = "-",
    val eventTime: String = "-",
    val etaStation: String = "-",
    val eta: String = "-",
    val etaPortDate: String = "",
    val lfd: String = "-",
    val lfdDays: Int? = null,
    val customs: String = "-",
    val customsState: String = "none",
    val verdict: String = "Unknown",
    val deltaText: String = "—",
    val src: String = "CACHED",
    val archived: Boolean = false
)

data class ShipmentView(
    val shipment: Shipment,
    val total: Int,
    val done: Int
) {
    val closed: Boolean get() = total > 0 && done == total
}

data class ContainerGroup(
    val title: String = "",
    val customer: String = "",
    val etaDate: String = "",
    val pending: Boolean = false,
    val rows: List<ContainerRow> = emptyList()
)

enum class GroupMode(val key: String, val label: String) {
    SHIPMENT("shipment", "Shipment"),
    CUSTOMER("customer", "Customer"),
    MBL("mbl", "MBL"),
    HBL("hbl", "HBL")
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository(app)

    var tracking by mutableStateOf<Map<String, ContainerRecord>>(emptyMap())
        private set
    var meta by mutableStateOf<Map<String, Repository.Meta>>(emptyMap())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastRefresh by mutableStateOf("—")
        private set
    var shipmentsTick by mutableStateOf(0)
        private set
    var syncing by mutableStateOf(false)
        private set

    var apiBaseUrl by mutableStateOf(repo.trackingUrl())
        private set
    var sheetSyncUrl by mutableStateOf(repo.sheetSyncUrl())
        private set

    private val fmt = DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.ENGLISH)

    init {
        refresh(false)
        maybeAutoSync()
    }

    private fun nowLabel(): String = LocalDateTime.now().format(fmt)

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            loading = true
            val ids = repo.allContainerIds()
            if (ids.isEmpty()) {
                tracking = emptyMap()
                message = "No shipments yet. Add one on the Shipments page."
                loading = false
                return@launch
            }
            tracking = tracking + repo.cachedMap(ids)
            if (repo.trackingUrl().isBlank()) {
                message = "Set the tracking URL in ⋮ → Settings."
                loading = false
                return@launch
            }
            val res = repo.fetchIds(ids, force)
            tracking = tracking + res.results
            meta = meta + res.meta
            error = res.error.ifBlank { null }
            lastRefresh = nowLabel()
            message = "Updated $lastRefresh • CN Rail API • SQLite cache"
            syncArchiveState()
            shipmentsTick++
            loading = false
        }
    }

    fun clearError() { error = null }

    // ---------- sheet sync ----------

    fun syncFromSheet() {
        if (syncing) return
        if (sheetSyncUrl.isBlank()) {
            error = "Sheet sync URL is not set. Open ⋮ → Settings."
            return
        }
        viewModelScope.launch {
            syncing = true
            try {
                val shipments = repo.fetchSheetShipments()
                val n = repo.db.syncSheetShipments(shipments)
                repo.db.setSetting("sheet_sync_date", LocalDate.now().toString())
                shipmentsTick++
                message = "Synced $n shipments from sheet"
                refresh(true)
            } catch (ex: Exception) {
                error = "Sheet sync failed: ${ex.message}"
            } finally {
                syncing = false
            }
        }
    }

    private fun maybeAutoSync() {
        if (sheetSyncUrl.isBlank()) return
        if (repo.db.getSetting("sheet_sync_date") == LocalDate.now().toString()) return
        syncFromSheet()
    }

    fun saveEndpoints(trackingUrl: String, sheetUrl: String) {
        val t = trackingUrl.trim()
        val s = sheetUrl.trim()
        repo.db.setSetting("api_base_url", t)
        repo.db.setSetting("sheet_sync_url", s)
        apiBaseUrl = t
        sheetSyncUrl = s
        message = "Settings saved"
        if (t.isNotBlank()) refresh(true)
    }

    fun resetEndpoints() {
        repo.db.setSetting("api_base_url", CnApi.API_BASE)
        repo.db.setSetting("sheet_sync_url", CnApi.SHEET_SYNC_URL)
        apiBaseUrl = CnApi.API_BASE
        sheetSyncUrl = CnApi.SHEET_SYNC_URL
        message = "Settings reset to defaults"
    }

    // ---------- shipments ----------

    fun shipments(): List<Shipment> = repo.db.listShipments()

    fun shipmentsView(search: String = ""): Pair<List<ShipmentView>, List<ShipmentView>> {
        shipmentsTick
        val active = mutableListOf<ShipmentView>()
        val archived = mutableListOf<ShipmentView>()
        val ordered = repo.db.listShipments(search).sortedWith(
            compareBy({ it.etaDate.isBlank() }, { it.etaDate }, { it.jobNumber })
        )
        for (s in ordered) {
            val cids = Db.parseContainers(s.containers)
            val done = cids.count { isClosedCached(it) }
            val view = ShipmentView(s, cids.size, done)
            if (view.closed) archived.add(view) else active.add(view)
        }
        return active to archived
    }

    private fun isClosedCached(cid: String, rec: ContainerRecord? = null): Boolean {
        if (repo.db.isManuallyArchived(cid)) return true
        val r = rec ?: tracking[cid] ?: repo.cachedRecord(cid)
        return r != null && (Completion.isCompleted(r) || Completion.hasOutgate(r))
    }

    private fun isDoneCached(cid: String, rec: ContainerRecord? = null): Boolean {
        if (repo.db.isArchived(cid)) return true
        val r = rec ?: tracking[cid]
        return r != null && (Completion.isCompleted(r) || Completion.hasOutgate(r))
    }

    private fun syncArchiveState() {
        var changed = false
        for ((cid, rec) in tracking) {
            if (!repo.db.isArchived(cid) && (Completion.isCompleted(rec) || Completion.hasOutgate(rec))) {
                repo.db.archiveContainer(cid)
                changed = true
            }
        }
        if (changed) shipmentsTick++
    }

    fun saveShipment(data: Shipment, oldJob: String?) {
        repo.db.upsertShipment(data, oldJob)
        shipmentsTick++
        refresh(true)
    }

    fun deleteShipment(job: String) {
        repo.db.deleteShipment(job)
        shipmentsTick++
        tracking = tracking - repo.containersForJob(job).toSet()
    }

    fun customers(): List<String> = repo.db.getCustomers()

    // ---------- persisted UI state ----------

    var groupMode by mutableStateOf(
        GroupMode.entries.firstOrNull { it.key == repo.db.getSetting("ui_group_mode") }
            ?: if (repo.db.getSetting("ui_group_customer") == "1") GroupMode.CUSTOMER else GroupMode.SHIPMENT
    )
        private set

    fun updateGroupMode(m: GroupMode) {
        if (m == groupMode) return
        groupMode = m
        repo.db.setSetting("ui_group_mode", m.key)
    }

    var showArchivedContainers by mutableStateOf(repo.db.getSetting("ui_show_archived") == "1")
        private set

    fun updateShowArchivedContainers(v: Boolean) {
        showArchivedContainers = v
        repo.db.setSetting("ui_show_archived", if (v) "1" else "0")
    }

    fun parseContainers(text: String): List<String> = Db.parseContainers(text)

    fun archiveJob(job: String) {
        for (cid in repo.containersForJob(job)) repo.db.archiveContainer(cid, manual = true)
        shipmentsTick++
        refresh(false)
    }

    fun restoreJob(job: String) {
        for (cid in repo.containersForJob(job)) repo.db.unarchiveContainer(cid)
        shipmentsTick++
        refresh(false)
    }

    fun toggleArchive(cid: String) {
        if (repo.db.isArchived(cid)) repo.db.unarchiveContainer(cid) else repo.db.archiveContainer(cid, manual = true)
        shipmentsTick++
        refresh(false)
    }

    fun archivedList(): List<String> = repo.db.listArchived()

    // ---------- rows ----------

    private fun displayStatus(rec: ContainerRecord?): String {
        if (rec != null && Completion.hasOutgate(rec)) return "Closed"
        val status = rec?.summary?.waybillStatus?.trim() ?: ""
        if (status.isNotEmpty()) return status
        if ((rec?.activity?.isNotEmpty() == true)) return "Planned"
        return "no data yet"
    }

    private fun row(cid: String, job: String, rec: ContainerRecord?, src: String): ContainerRow {
        val sm = rec?.summary
        val etaRows = rec?.eta ?: emptyList()
        val analysis = Eta.analyzeEta(etaRows)
        val closed = rec != null && (Completion.hasOutgate(rec) || Completion.isCompleted(rec))
        val lfdRaw = sm?.lastFreeDay ?: ""
        val lfdDt = CnTime.parseCnRail(lfdRaw)
        val rawLfdDays = lfdDt?.let {
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), it.toLocalDate()).toInt()
        }
        val lfdDays = if (closed) null else rawLfdDays
        val wb = sm?.waybillStatus?.trim().orEmpty()
        return ContainerRow(
            cid = cid, job = job,
            status = displayStatus(rec),
            lastEvent = sm?.lastEvent?.ifBlank { "-" } ?: "-",
            location = sm?.lastEventLocation?.ifBlank { "-" } ?: "-",
            eventTime = CnTime.dashboardTime(sm?.lastEventTime, ""),
            etaStation = sm?.etaStation?.ifBlank { null } ?: sm?.destination?.ifBlank { "-" } ?: "-",
            eta = CnTime.dashboardTime(sm?.etaTime, ""),
            etaPortDate = repo.etaDateOf(cid),
            lfd = if (closed) "—" else CnTime.dashboardLfd(lfdRaw),
            lfdDays = lfdDays,
            customs = sm?.customsDescription?.trim().orEmpty()
                .ifEmpty { "Under CBSA Customs Hold" }.take(40),
            customsState = CnTime.customsStatus(sm?.customsDescription),
            verdict = if (wb.contains("plan", ignoreCase = true)) "Planned" else analysis.verdict,
            deltaText = analysis.deltaText,
            src = src,
            archived = isDoneCached(cid, rec)
        )
    }

    fun watchlistRows(search: String = "", job: String = "", showArchived: Boolean = false): List<ContainerRow> {
        tracking; meta; shipmentsTick
        val q = search.uppercase()
        val rows = mutableListOf<ContainerRow>()
        for (s in repo.db.listShipments()) {
            val jobNo = s.jobNumber
            if (job.isNotEmpty() && jobNo != job) continue
            val hay = (jobNo + " " + s.mbl + " " + s.hbl + " " + s.poNumber + " " + s.customer).uppercase()
            for (cid in Db.parseContainers(s.containers)) {
                if (q.isNotEmpty() && !cid.contains(q) && !hay.contains(q)) continue
                if (isDoneCached(cid) && !showArchived) continue
                val rec = tracking[cid] ?: repo.cachedRecord(cid)
                val src = meta[cid]?.source ?: "CACHED"
                rows.add(row(cid, jobNo, rec, src))
            }
        }
        return rows
    }

    fun groupShipments(list: List<ShipmentView>): List<Pair<String, List<ShipmentView>>> {
        if (groupMode == GroupMode.SHIPMENT) return listOf("" to list)
        return list.groupBy { shipmentGroupKey(it.shipment) }
            .toList()
            .sortedBy { it.first.lowercase(Locale.ENGLISH) }
    }

    private fun shipmentGroupKey(s: Shipment): String = when (groupMode) {
        GroupMode.SHIPMENT -> Db.groupLabel(s.jobNumber, s.mbl)
        GroupMode.CUSTOMER -> s.customer.ifBlank { "Unassigned" }
        GroupMode.MBL -> s.mbl.ifBlank { "No MBL" }
        GroupMode.HBL -> s.hbl.ifBlank { "No HBL" }
    }

    fun watchlistGrouped(search: String = "", job: String = "", showArchived: Boolean = false): List<ContainerGroup> {
        val shipByJob = repo.db.listShipments().associateBy { it.jobNumber }
        val rows = watchlistRows(search, job, showArchived)
        val order = LinkedHashMap<String, MutableList<ContainerRow>>()
        val customer = HashMap<String, String>()
        val eta = HashMap<String, String>()
        val pending = HashMap<String, Boolean>()
        for (r in rows) {
            val s = shipByJob[r.job]
            val key = when (groupMode) {
                GroupMode.SHIPMENT -> Db.groupLabel(r.job, s?.mbl ?: "")
                GroupMode.CUSTOMER -> s?.customer?.ifBlank { "Unassigned" } ?: "Unassigned"
                GroupMode.MBL -> s?.mbl?.ifBlank { "No MBL" } ?: "No MBL"
                GroupMode.HBL -> s?.hbl?.ifBlank { "No HBL" } ?: "No HBL"
            }
            order.getOrPut(key) { mutableListOf() }.add(r)
            if (s != null) {
                if (customer[key].isNullOrEmpty()) customer[key] = s.customer
                val e = s.etaDate
                if (e.isNotBlank() && (eta[key].isNullOrEmpty() || e < eta[key]!!)) eta[key] = e
                pending[key] = pending[key] == true || Db.isPendingJob(s.jobNumber, s.mbl)
            }
        }
        return order.map { (key, list) ->
            ContainerGroup(
                title = key,
                customer = customer[key] ?: "",
                etaDate = eta[key] ?: "",
                pending = groupMode == GroupMode.SHIPMENT && pending[key] == true,
                rows = list
            )
        }.sortedWith(compareBy({ it.etaDate.isBlank() }, { it.etaDate }, { it.title.lowercase(Locale.ENGLISH) }))
    }

    fun containerRow(cid: String): ContainerRow {
        val job = repo.jobOf(cid)
        val rec = tracking[cid] ?: repo.cachedRecord(cid)
        return row(cid, job, rec, meta[cid]?.source ?: "CACHED")
    }

    fun record(cid: String): ContainerRecord? = tracking[cid] ?: repo.cachedRecord(cid)

    // ---------- alerts ----------

    fun alerts(): List<Alert> {
        tracking; shipmentsTick
        val jobMap = repo.jobMap()
        val archived = repo.db.listArchived().toSet()
        return Alerts.collectAlerts(tracking, jobMap, archived)
    }

    fun alertSummary(): String = Alerts.alertSummary(alerts())

    // ---------- quick track ----------

    fun quickTrack(cidRaw: String, onResult: (String) -> Unit) {
        val cid = cidRaw.trim().uppercase()
        if (cid.length < 10) {
            onResult("Enter 4 letters + 7 digits (e.g. CNRU1234567).")
            return
        }
        viewModelScope.launch {
            loading = true
            val res = repo.fetchIds(listOf(cid), true)
            tracking = tracking + res.results
            meta = meta + res.meta
            loading = false
            onResult(
                when {
                    res.results.containsKey(cid) -> "OK:${cid}"
                    else -> "No data for $cid${if (res.error.isNotBlank()) " (${res.error})" else ""}."
                }
            )
        }
    }

    // ---------- map ----------

    suspend fun mapData(cid: String): MapData = withContext(Dispatchers.Default) {
        val rec = record(cid) ?: ContainerRecord()
        repo.gps.containerMapData(cid, rec)
    }

    suspend fun railFeatures(): Triple<List<List<GeoPoint>>, List<List<GeoPoint>>, List<GeoPoint>> =
        withContext(Dispatchers.Default) { repo.gps.railFeatures() }
}
