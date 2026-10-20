package com.avancerifs.cntrack.data

import android.content.Context
import com.avancerifs.cntrack.logic.Completion
import com.avancerifs.cntrack.logic.GpsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(context: Context) {

    data class Meta(val source: String, val fetchedAt: String = "", val ageH: Double = 0.0)

    data class FetchResult(
        val results: Map<String, ContainerRecord>,
        val meta: Map<String, Meta>,
        val error: String = ""
    )

    val db = Db(context)
    val gps = GpsRoute(db, context.assets)

    fun cachedRecord(cid: String): ContainerRecord? {
        val row = db.getCache(cid) ?: return null
        val rec = runCatching { ContainerRecord.from(row.payload) }.getOrNull() ?: return null
        return Completion.truncateAtOutgate(rec)
    }

    fun allContainerIds(): List<String> {
        val out = LinkedHashSet<String>()
        for (s in db.listShipments()) out.addAll(Db.parseContainers(s.containers))
        return out.toList()
    }

    fun jobOf(cid: String): String {
        val all = db.listShipments()
        for (s in all) if (Db.parseContainers(s.containers).contains(cid)) return s.jobNumber
        return ""
    }

    fun etaDateOf(cid: String): String {
        for (s in db.listShipments()) {
            if (Db.parseContainers(s.containers).contains(cid)) return s.etaDate
        }
        return ""
    }

    fun jobMap(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (s in db.listShipments()) {
            for (cid in Db.parseContainers(s.containers)) map[cid] = s.jobNumber
        }
        return map
    }

    fun containersForJob(job: String): List<String> {
        val s = db.getShipment(job) ?: return emptyList()
        return Db.parseContainers(s.containers)
    }

    fun trackingUrl(): String =
        db.getSetting("api_base_url")?.trim().orEmpty().ifEmpty { CnApi.API_BASE }

    fun sheetSyncUrl(): String =
        db.getSetting("sheet_sync_url")?.trim().orEmpty().ifEmpty { CnApi.SHEET_SYNC_URL }

    suspend fun fetchSheetShipments(): List<Shipment> =
        withContext(Dispatchers.IO) { CnApi.fetchShipmentsFromSheet(sheetSyncUrl()) }

    fun cachedMap(ids: List<String>): Map<String, ContainerRecord> {
        val out = LinkedHashMap<String, ContainerRecord>()
        for (cid in ids) cachedRecord(cid)?.let { out[cid] = it }
        return out
    }

    fun isDone(cid: String, rec: ContainerRecord? = null): Boolean {
        if (db.isArchived(cid)) return true
        val r = rec ?: cachedRecord(cid)
        return r != null && (Completion.isCompleted(r) || Completion.hasOutgate(r))
    }

    suspend fun fetchIds(ids: List<String>, force: Boolean = false, activityLimit: Int = 0): FetchResult =
        withContext(Dispatchers.IO) {
            val clean = ids.filter { it.isNotEmpty() }
            val results = LinkedHashMap<String, ContainerRecord>()
            val meta = LinkedHashMap<String, Meta>()
            try {
                val cached = HashMap<String, Triple<ContainerRecord, String, Double>>()
                val toFetch = ArrayList<String>()
                for (cid in clean) {
                    val row = db.getCache(cid)
                    val rec = row?.let {
                        runCatching { ContainerRecord.from(it.payload) }.getOrNull()
                    }?.let { Completion.truncateAtOutgate(it) }
                    val age = if (row != null) db.cacheAgeHours(row.fetchedAt) else 9999.0
                    if (rec != null && row != null && age < CnApi.CACHE_TTL_HOURS && !force) {
                        results[cid] = rec
                        meta[cid] = Meta("CACHED", row.fetchedAt, round1(age))
                    } else {
                        if (rec != null && row != null) cached[cid] = Triple(rec, row.fetchedAt, age)
                        toFetch.add(cid)
                    }
                }
                if (toFetch.isNotEmpty()) {
                    val live = CnApi.fetchTrackingLive(toFetch, activityLimit, trackingUrl())
                    for (cid in toFetch) {
                        val rec = live[cid]
                        if (rec != null) {
                            val truncated = Completion.truncateAtOutgate(rec)
                            results[cid] = truncated
                            db.setCache(cid, truncated.toJson())
                            meta[cid] = Meta("LIVE", "", 0.0)
                        } else {
                            cached[cid]?.let {
                                results[cid] = it.first
                                meta[cid] = Meta("CACHED-STALE", it.second, round1(it.third))
                            }
                        }
                    }
                }
                FetchResult(results, meta, "")
            } catch (ex: Exception) {
                for (cid in clean) {
                    if (!results.containsKey(cid)) {
                        db.getCache(cid)?.let { row ->
                            runCatching { ContainerRecord.from(row.payload) }.getOrNull()?.let {
                                results[cid] = it
                                meta[cid] = Meta("CACHED-FALLBACK", row.fetchedAt, 0.0)
                            }
                        }
                    }
                }
                val err = if (results.isEmpty()) (ex.message ?: "error") else "${ex.message} (showing cache)"
                FetchResult(results, meta, err)
            }
        }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
