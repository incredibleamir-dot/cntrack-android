package com.avancerifs.cntrack.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.avancerifs.cntrack.logic.Completion
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Db(private val context: Context) {

    data class CacheRow(val payload: JSONObject, val fetchedAt: String)

    private val file: File by lazy {
        val dest = File(context.filesDir, "shipments.db")
        if (!dest.exists() || dest.length() == 0L) {
            context.assets.open("cntrack/shipments.db").use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        }
        dest
    }

    val db: SQLiteDatabase by lazy {
        val d = SQLiteDatabase.openOrCreateDatabase(file, null)
        init(d)
        d
    }

    private fun init(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE IF NOT EXISTS customers (name TEXT PRIMARY KEY)")
        d.execSQL(
            "CREATE TABLE IF NOT EXISTS shipments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, job_number TEXT UNIQUE NOT NULL," +
                "mbl TEXT DEFAULT '', hbl TEXT DEFAULT '', po_number TEXT DEFAULT ''," +
                "port_of_discharge TEXT DEFAULT '', customer TEXT DEFAULT ''," +
                "containers TEXT DEFAULT '', eta_date TEXT DEFAULT ''," +
                "created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')"
        )
        addColumnIfMissing(d, "shipments", "eta_date", "TEXT DEFAULT ''")
        d.execSQL("CREATE TABLE IF NOT EXISTS container_cache (equipment_id TEXT PRIMARY KEY, payload TEXT NOT NULL, fetched_at TEXT NOT NULL)")
        d.execSQL("CREATE TABLE IF NOT EXISTS archived_containers (equipment_id TEXT PRIMARY KEY, archived_at TEXT NOT NULL, manual INTEGER DEFAULT 0)")
        addColumnIfMissing(d, "archived_containers", "manual", "INTEGER DEFAULT 0")
        d.execSQL("CREATE TABLE IF NOT EXISTS deliveries (equipment_id TEXT PRIMARY KEY, delivered_at TEXT NOT NULL)")
        d.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        d.execSQL("CREATE TABLE IF NOT EXISTS notify_seen (equipment_id TEXT PRIMARY KEY, baselined_at TEXT NOT NULL)")
        d.execSQL(
            "CREATE TABLE IF NOT EXISTS notified_milestones (" +
                "equipment_id TEXT NOT NULL, milestone TEXT NOT NULL," +
                "event_time TEXT DEFAULT '', notified_at TEXT NOT NULL," +
                "PRIMARY KEY(equipment_id, milestone))"
        )
        migrateCompletedToArchive(d)
    }

    private fun addColumnIfMissing(d: SQLiteDatabase, table: String, column: String, def: String) {
        var exists = false
        d.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameIdx).equals(column, ignoreCase = true)) { exists = true; break }
            }
        }
        if (!exists) d.execSQL("ALTER TABLE $table ADD COLUMN $column $def")
    }

    private fun migrateCompletedToArchive(d: SQLiteDatabase) {
        d.rawQuery("SELECT value FROM app_settings WHERE key=?", arrayOf("archive_migrated")).use { c ->
            if (c.moveToFirst()) return
        }
        val cids = mutableListOf<String>()
        d.rawQuery("SELECT equipment_id, payload FROM container_cache", null).use { c ->
            while (c.moveToNext()) {
                val eid = c.getString(0) ?: continue
                val payload = c.getString(1) ?: continue
                val rec = runCatching { ContainerRecord.from(payload) }.getOrNull() ?: continue
                if (rec.completed || Completion.hasOutgate(rec)) cids.add(eid)
            }
        }
        val now = Instant.now().toString()
        for (cid in cids) {
            d.execSQL(
                "INSERT OR REPLACE INTO archived_containers (equipment_id, archived_at) VALUES (?,?)",
                arrayOf(cid, now)
            )
        }
        d.execSQL("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?,?)", arrayOf("archive_migrated", "1"))
    }

    // ---- shipments ----

    private fun Cursor.toShipment(): Shipment = Shipment(
        id = getLong(getColumnIndexOrThrow("id")),
        jobNumber = getString(getColumnIndexOrThrow("job_number")) ?: "",
        mbl = getString(getColumnIndexOrThrow("mbl")) ?: "",
        hbl = getString(getColumnIndexOrThrow("hbl")) ?: "",
        poNumber = getString(getColumnIndexOrThrow("po_number")) ?: "",
        portOfDischarge = getString(getColumnIndexOrThrow("port_of_discharge")) ?: "",
        etaDate = getString(getColumnIndexOrThrow("eta_date")) ?: "",
        customer = getString(getColumnIndexOrThrow("customer")) ?: "",
        containers = getString(getColumnIndexOrThrow("containers")) ?: "",
        createdAt = getString(getColumnIndexOrThrow("created_at")) ?: "",
        updatedAt = getString(getColumnIndexOrThrow("updated_at")) ?: ""
    )

    fun listShipments(search: String = ""): List<Shipment> {
        val list = mutableListOf<Shipment>()
        val cur = if (search.isNotBlank()) {
            val like = "%$search%"
            db.rawQuery(
                "SELECT * FROM shipments WHERE job_number LIKE ? OR mbl LIKE ? OR hbl LIKE ? OR po_number LIKE ? OR customer LIKE ? OR containers LIKE ? ORDER BY updated_at DESC",
                arrayOf(like, like, like, like, like, like)
            )
        } else {
            db.rawQuery("SELECT * FROM shipments ORDER BY updated_at DESC", null)
        }
        cur.use { while (it.moveToNext()) list.add(it.toShipment()) }
        return list
    }

    fun getShipment(jobNumber: String): Shipment? {
        db.rawQuery("SELECT * FROM shipments WHERE job_number=?", arrayOf(jobNumber)).use {
            return if (it.moveToFirst()) it.toShipment() else null
        }
    }

    fun upsertShipment(data: Shipment, oldJob: String? = null) {
        val now = Instant.now().toString()
        if (oldJob != null && oldJob != data.jobNumber) {
            db.execSQL(
                "UPDATE shipments SET job_number=?, mbl=?, hbl=?, po_number=?, port_of_discharge=?, customer=?, containers=?, updated_at=? WHERE job_number=?",
                arrayOf(data.jobNumber, data.mbl, data.hbl, data.poNumber, data.portOfDischarge, data.customer, data.containers, now, oldJob)
            )
        } else {
            val exists = db.rawQuery("SELECT id FROM shipments WHERE job_number=?", arrayOf(data.jobNumber)).use { it.moveToFirst() }
            if (exists) {
                db.execSQL(
                    "UPDATE shipments SET mbl=?, hbl=?, po_number=?, port_of_discharge=?, customer=?, containers=?, updated_at=? WHERE job_number=?",
                    arrayOf(data.mbl, data.hbl, data.poNumber, data.portOfDischarge, data.customer, data.containers, now, data.jobNumber)
                )
            } else {
                db.execSQL(
                    "INSERT INTO shipments (job_number,mbl,hbl,po_number,port_of_discharge,customer,containers,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    arrayOf(data.jobNumber, data.mbl, data.hbl, data.poNumber, data.portOfDischarge, data.customer, data.containers, now, now)
                )
            }
        }
        saveCustomer(data.customer)
    }

    fun syncSheetShipments(list: List<Shipment>): Int {
        var n = 0
        for (s in list) {
            val job = s.jobNumber.trim()
            val mbl = s.mbl.trim()
            if (job.isEmpty() && mbl.isEmpty()) continue
            val key = computeKey(job, mbl)
            if (key.isEmpty()) continue
            upsertByKey(key, s)
            saveCustomer(s.customer)
            n++
        }
        return n
    }

    fun computeKey(job: String, mbl: String): String {
        val j = job.trim()
        if (j.isEmpty() || j.equals(PENDING_JOB, ignoreCase = true)) return mbl.trim()
        return j
    }

    private fun upsertByKey(key: String, data: Shipment) {
        val now = Instant.now().toString()
        var id = -1L
        val mbl = data.mbl.trim()
        if (mbl.isNotEmpty()) {
            db.rawQuery("SELECT id FROM shipments WHERE mbl=? LIMIT 1", arrayOf(mbl)).use {
                if (it.moveToFirst()) id = it.getLong(0)
            }
        }
        if (id < 0 && data.containers.isNotBlank()) {
            for (c in Db.parseContainers(data.containers)) {
                var found = false
                db.rawQuery("SELECT id FROM shipments WHERE containers LIKE ? LIMIT 1", arrayOf("%$c%")).use {
                    if (it.moveToFirst()) { id = it.getLong(0); found = true }
                }
                if (found) break
            }
        }
        if (id >= 0) {
            db.rawQuery("SELECT id FROM shipments WHERE job_number=? AND id<>?", arrayOf(key, id.toString())).use {
                if (it.moveToFirst()) db.execSQL("DELETE FROM shipments WHERE id=?", arrayOf(it.getLong(0)))
            }
            db.execSQL(
                "UPDATE shipments SET job_number=?, mbl=?, hbl=?, po_number=?, port_of_discharge=?, eta_date=?, customer=?, containers=?, updated_at=? WHERE id=?",
                arrayOf(key, data.mbl, data.hbl, data.poNumber, data.portOfDischarge, data.etaDate, data.customer, data.containers, now, id)
            )
        } else {
            db.execSQL(
                "INSERT OR REPLACE INTO shipments (job_number,mbl,hbl,po_number,port_of_discharge,eta_date,customer,containers,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                arrayOf(key, data.mbl, data.hbl, data.poNumber, data.portOfDischarge, data.etaDate, data.customer, data.containers, now, now)
            )
        }
    }

    fun deleteShipment(jobNumber: String) {
        db.execSQL("DELETE FROM shipments WHERE job_number=?", arrayOf(jobNumber))
    }

    // ---- milestone notifications ----

    fun isNotifySeen(cid: String): Boolean =
        db.rawQuery("SELECT 1 FROM notify_seen WHERE equipment_id=?", arrayOf(cid)).use { it.moveToFirst() }

    fun markNotifySeen(cid: String) {
        db.execSQL(
            "INSERT OR IGNORE INTO notify_seen (equipment_id, baselined_at) VALUES (?,?)",
            arrayOf(cid, Instant.now().toString())
        )
    }

    fun recordedMilestones(cid: String): Set<String> {
        val out = mutableSetOf<String>()
        db.rawQuery("SELECT milestone FROM notified_milestones WHERE equipment_id=?", arrayOf(cid)).use {
            while (it.moveToNext()) out.add(it.getString(0))
        }
        return out
    }

    fun recordMilestone(cid: String, milestone: String, eventTime: String) {
        db.execSQL(
            "INSERT OR REPLACE INTO notified_milestones (equipment_id, milestone, event_time, notified_at) VALUES (?,?,?,?)",
            arrayOf(cid, milestone, eventTime, Instant.now().toString())
        )
    }

    fun getCustomers(): List<String> {
        val out = mutableListOf<String>()
        db.rawQuery("SELECT name FROM customers ORDER BY name", null).use {
            while (it.moveToNext()) out.add(it.getString(0))
        }
        return out
    }

    fun saveCustomer(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        db.execSQL("INSERT OR IGNORE INTO customers (name) VALUES (?)", arrayOf(n))
    }

    // ---- cache ----

    fun getCache(equipmentId: String): CacheRow? {
        db.rawQuery("SELECT payload, fetched_at FROM container_cache WHERE equipment_id=?", arrayOf(equipmentId)).use {
            if (!it.moveToFirst()) return null
            val payload = it.getString(0) ?: return null
            return CacheRow(JSONObject(payload), it.getString(1) ?: "")
        }
    }

    fun setCache(equipmentId: String, payload: JSONObject) {
        db.execSQL(
            "INSERT OR REPLACE INTO container_cache (equipment_id, payload, fetched_at) VALUES (?,?,?)",
            arrayOf(equipmentId, payload.toString(), Instant.now().toString())
        )
    }

    fun cacheAgeHours(fetchedAt: String?): Double {
        if (fetchedAt.isNullOrBlank()) return 9999.0
        return try {
            val dt = OffsetDateTime.parse(fetchedAt).toInstant()
            Duration.between(dt, Instant.now()).seconds / 3600.0
        } catch (e: Exception) {
            try {
                val dt = Instant.parse(fetchedAt)
                Duration.between(dt, Instant.now()).seconds / 3600.0
            } catch (e2: Exception) {
                9999.0
            }
        }
    }

    // ---- settings ----

    fun getSetting(key: String): String? =
        db.rawQuery("SELECT value FROM app_settings WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun setSetting(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?,?)", arrayOf(key, value))
    }

    // ---- archive ----

    fun archiveContainer(equipmentId: String, manual: Boolean = false) {
        db.execSQL(
            "INSERT OR REPLACE INTO archived_containers (equipment_id, archived_at, manual) VALUES (?,?,?)",
            arrayOf(equipmentId, Instant.now().toString(), if (manual) 1 else 0)
        )
    }

    fun unarchiveContainer(equipmentId: String) {
        db.execSQL("DELETE FROM archived_containers WHERE equipment_id=?", arrayOf(equipmentId))
    }

    fun isArchived(equipmentId: String): Boolean =
        db.rawQuery("SELECT 1 FROM archived_containers WHERE equipment_id=?", arrayOf(equipmentId)).use { it.moveToFirst() }

    fun isManuallyArchived(equipmentId: String): Boolean =
        db.rawQuery("SELECT 1 FROM archived_containers WHERE equipment_id=? AND manual=1", arrayOf(equipmentId))
            .use { it.moveToFirst() }

    fun listArchived(): List<String> {
        val out = mutableListOf<String>()
        db.rawQuery("SELECT equipment_id FROM archived_containers ORDER BY equipment_id", null).use {
            while (it.moveToNext()) out.add(it.getString(0))
        }
        return out
    }

    // ---- gps helpers ----

    fun stationCoords(stationName: String): List<StationCoord> {
        val out = mutableListOf<StationCoord>()
        db.rawQuery(
            "SELECT station_name, latitude, longitude, status, matched_name, carrier, province, source, route_occurrences, od_ids FROM station_gps WHERE station_name=? ORDER BY route_occurrences DESC, latitude, longitude",
            arrayOf(stationName)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    StationCoord(
                        stationName = c.getString(0) ?: "",
                        latitude = c.getDouble(1),
                        longitude = c.getDouble(2),
                        status = c.getString(3) ?: "",
                        matchedName = c.getString(4) ?: "",
                        carrier = c.getString(5) ?: "",
                        province = c.getString(6) ?: "",
                        source = c.getString(7) ?: "",
                        routeOccurrences = c.getInt(8),
                        odIds = c.getString(9) ?: ""
                    )
                )
            }
        }
        return out
    }

    fun stationCoordsLike(name: String): Pair<Double, Double>? {
        db.rawQuery(
            "SELECT latitude, longitude FROM station_gps WHERE station_name LIKE ? AND latitude IS NOT NULL AND longitude IS NOT NULL LIMIT 1",
            arrayOf("%${name.trim()}%")
        ).use { c ->
            if (c.moveToFirst()) return c.getDouble(0) to c.getDouble(1)
        }
        return null
    }

    fun routeCoords(origin: String, destination: String): List<RoutePoint> {
        val out = mutableListOf<RoutePoint>()
        db.rawQuery(
            "SELECT od_id, origin, destination, position, station_name, latitude, longitude FROM route_gps WHERE origin=? AND destination=? ORDER BY od_id, position",
            arrayOf(origin, destination)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    RoutePoint(
                        odId = c.getInt(0),
                        origin = c.getString(1) ?: "",
                        destination = c.getString(2) ?: "",
                        position = c.getInt(3),
                        stationName = c.getString(4) ?: "",
                        latitude = if (c.isNull(5)) null else c.getDouble(5),
                        longitude = if (c.isNull(6)) null else c.getDouble(6)
                    )
                )
            }
        }
        return out
    }

    fun peersContaining(stationName: String): List<String> {
        val out = mutableListOf<String>()
        db.rawQuery("SELECT DISTINCT equipment_id FROM container_travel WHERE station_name=?", arrayOf(stationName)).use {
            while (it.moveToNext()) out.add(it.getString(0))
        }
        return out
    }

    fun travelSequence(equipmentId: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        db.rawQuery("SELECT station_name, event_time FROM container_travel WHERE equipment_id=? ORDER BY seq", arrayOf(equipmentId)).use {
            while (it.moveToNext()) out.add((it.getString(0) ?: "") to (it.getString(1) ?: ""))
        }
        return out
    }

    data class StationCoord(
        val stationName: String, val latitude: Double, val longitude: Double,
        val status: String, val matchedName: String, val carrier: String,
        val province: String, val source: String, val routeOccurrences: Int, val odIds: String
    )

    data class RoutePoint(
        val odId: Int, val origin: String, val destination: String, val position: Int,
        val stationName: String, val latitude: Double?, val longitude: Double?
    )

    companion object {
        const val PENDING_JOB = "TBA"

        private val SPLIT = Regex("[,;\\s\\n\\r|]+")

        fun parseContainers(text: String?): List<String> =
            SPLIT.split((text ?: "").uppercase()).filter { it.isNotEmpty() }

        fun isPendingJob(job: String, mbl: String): Boolean {
            val j = job.trim()
            return j.isEmpty() || j.equals(PENDING_JOB, ignoreCase = true) || j.equals(mbl.trim(), ignoreCase = true)
        }

        fun groupLabel(job: String, mbl: String): String {
            val j = job.trim()
            if (j.isEmpty() || j.equals(PENDING_JOB, ignoreCase = true) || j.equals(mbl.trim(), ignoreCase = true)) {
                return mbl.trim().ifEmpty { PENDING_JOB }
            }
            return j
        }
    }
}
