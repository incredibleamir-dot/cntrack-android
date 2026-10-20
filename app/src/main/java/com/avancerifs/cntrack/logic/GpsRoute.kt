package com.avancerifs.cntrack.logic

import android.content.res.AssetManager
import com.avancerifs.cntrack.data.ContainerRecord
import com.avancerifs.cntrack.data.Db
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

data class ResolvedPoint(
    val station: String,
    val latitude: Double?,
    val longitude: Double?,
    val matched: String,
    val carrier: String,
    val province: String
)

data class MapData(
    val equipmentId: String = "",
    val origin: String = "",
    val current: String = "",
    val destination: String = "",
    val travel: List<ResolvedPoint> = emptyList(),
    val remaining: List<ResolvedPoint> = emptyList(),
    val travelSegments: List<List<GeoPoint>> = emptyList(),
    val remainingSegments: List<List<GeoPoint>> = emptyList(),
    val unresolved: List<String> = emptyList(),
    val job: String = "",
    val pod: String = "",
    val podCoords: GeoPoint? = null,
    val progress: Int? = null,
    val travelKm: Int = 0,
    val remainKm: Int = 0,
    val rough: Boolean = false,
    val progressLabel: String = "Journey progress unknown"
)

class GpsRoute(private val db: Db, private val assets: AssetManager) {

    private class RailFeat(val name: String, val coords: List<GeoPoint>)

    private val railLock = Any()
    private var railInitialised = false
    private var railCnMain: List<RailFeat> = emptyList()
    private var railCnOther: List<RailFeat> = emptyList()
    private var railOther: List<RailFeat> = emptyList()
    private var railStations: List<Pair<String, GeoPoint>> = emptyList()

    private var graphBuilt = false
    private var adj: HashMap<GeoPoint, MutableList<Pair<GeoPoint, Double>>> = HashMap()
    private var ecoords: HashMap<Long, Pair<Double, List<GeoPoint>>> = HashMap()
    private var nodeGrid: HashMap<Long, MutableList<GeoPoint>> = HashMap()

    private fun key3(p: GeoPoint): GeoPoint = GeoPoint(round(p.lat * 1000) / 1000.0, round(p.lon * 1000) / 1000.0)

    private fun less(a: GeoPoint, b: GeoPoint): Boolean =
        if (a.lat != b.lat) a.lat < b.lat else a.lon <= b.lon

    private fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)

    private fun loadRail() {
        synchronized(railLock) {
            if (railInitialised) return
            railInitialised = true
            try {
                val text = assets.open("cntrack/maps/cache_v1.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val data = if (root.has("data")) root.optJSONObject("data") ?: root else root
                railCnMain = parseFeats(data.optJSONArray("cnMain"))
                railCnOther = parseFeats(data.optJSONArray("cnOther"))
                railOther = parseFeats(data.optJSONArray("other"))
                val stns = mutableListOf<Pair<String, GeoPoint>>()
                val sarr = data.optJSONArray("stations")
                if (sarr != null) {
                    for (i in 0 until sarr.length()) {
                        val o = sarr.optJSONObject(i) ?: continue
                        val at = o.optJSONArray("at") ?: continue
                        if (at.length() < 2) continue
                        stns.add((o.optString("name")) to GeoPoint(at.getDouble(0), at.getDouble(1)))
                    }
                }
                railStations = stns
            } catch (e: Exception) {
                railCnMain = emptyList(); railCnOther = emptyList()
                railOther = emptyList(); railStations = emptyList()
            }
        }
    }

    private fun parseFeats(arr: JSONArray?): List<RailFeat> {
        if (arr == null) return emptyList()
        val out = ArrayList<RailFeat>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val c = o.optJSONArray("coords") ?: continue
            val pts = ArrayList<GeoPoint>(c.length())
            for (j in 0 until c.length()) {
                val p = c.optJSONArray(j) ?: continue
                if (p.length() < 2) continue
                pts.add(GeoPoint(p.getDouble(0), p.getDouble(1)))
            }
            out.add(RailFeat(o.optString("name"), pts))
        }
        return out
    }

    fun railFeatures(): Triple<List<List<GeoPoint>>, List<List<GeoPoint>>, List<GeoPoint>> {
        loadRail()
        return Triple(
            (railCnMain + railCnOther).map { it.coords },
            railOther.map { it.coords },
            railStations.map { it.second }
        )
    }

    private fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dla = Math.toRadians(b.lat - a.lat)
        val dlo = Math.toRadians(b.lon - a.lon)
        val h = sin(dla / 2) * sin(dla / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dlo / 2) * sin(dlo / 2)
        return 2 * r * asin(sqrt(maxOf(0.0, min(1.0, h))))
    }

    private fun buildGraph() {
        synchronized(railLock) {
            if (graphBuilt) return
            graphBuilt = true
            loadRail()
            val all = railCnMain + railCnOther + railOther
            if (all.isEmpty()) return
            val chains = ArrayList<List<Array<Any>>>()
            for (feat in all) {
                val pts = feat.coords
                if (pts.size < 2) continue
                val idx = ArrayList<Int>()
                idx.add(0)
                var i = 2
                while (i < pts.size) { idx.add(i); i += 2 }
                if (idx.last() != pts.size - 1) idx.add(pts.size - 1)
                val hops = ArrayList<Array<Any>>()
                for (k in 0 until idx.size - 1) {
                    val a = key3(pts[idx[k]])
                    val b = key3(pts[idx[k + 1]])
                    if (a != b) {
                        hops.add(arrayOf(a, b, pts.subList(idx[k], idx[k + 1] + 1)))
                    }
                }
                chains.add(hops)
            }
            val parent = HashMap<GeoPoint, GeoPoint>()
            for (chain in chains) for (h in chain) {
                parent.putIfAbsent(h[0] as GeoPoint, h[0] as GeoPoint)
                parent.putIfAbsent(h[1] as GeoPoint, h[1] as GeoPoint)
            }
            fun find(x0: GeoPoint): GeoPoint {
                var x = x0
                while (parent[x] != x) {
                    val p = parent[x]!!
                    parent[x] = parent[p] ?: p
                    x = parent[x]!!
                }
                return x
            }
            val buckets = HashMap<Long, MutableList<GeoPoint>>()
            for (v in parent.keys) {
                val k = cellKey((v.lat * 300).toInt(), (v.lon * 300).toInt())
                buckets.getOrPut(k) { ArrayList() }.add(v)
            }
            for ((k, members) in buckets) {
                val cx = (k shr 32).toInt()
                val cy = (k and 0xffffffffL).toInt()
                val near = ArrayList<GeoPoint>()
                for (dx in -1..1) for (dy in -1..1) {
                    buckets[cellKey(cx + dx, cy + dy)]?.let { near.addAll(it) }
                }
                for (ai in members.indices) {
                    val a = members[ai]
                    for (b in near) {
                        if (!less(a, b)) continue
                        if (haversineKm(a, b) <= RAIL_MERGE_KM) {
                            val ra = find(a); val rb = find(b)
                            if (ra != rb) {
                                if (less(ra, rb)) parent[rb] = ra else parent[ra] = rb
                            }
                        }
                    }
                }
            }
            fun rep(v: GeoPoint): GeoPoint = find(v)
            for (chain in chains) for (h in chain) {
                val v = h[0] as GeoPoint
                val w = h[1] as GeoPoint
                @Suppress("UNCHECKED_CAST")
                val seq = h[2] as List<GeoPoint>
                val a = rep(v); val b = rep(w)
                if (a == b) continue
                var length = 0.0
                for (t in 0 until seq.size - 1) length += haversineKm(seq[t], seq[t + 1])
                adj.getOrPut(a) { ArrayList() }.add(b to length)
                adj.getOrPut(b) { ArrayList() }.add(a to length)
                val ordered = if (less(a, b)) a to b else b to a
                val ek = edgeKey(ordered.first, ordered.second)
                val oriented = if (ordered.first == a && ordered.second == b) seq else seq.reversed()
                val prev = ecoords[ek]
                if (prev == null || length < prev.first) ecoords[ek] = length to oriented
            }
            for (n in adj.keys) {
                val k = cellKey((n.lat * 4).toInt(), (n.lon * 4).toInt())
                nodeGrid.getOrPut(k) { ArrayList() }.add(n)
            }
        }
    }

    private fun edgeKey(a: GeoPoint, b: GeoPoint): Long {
        val ha = (a.lat * 1e6).toLong()
        val la = (a.lon * 1e6).toLong()
        val hb = (b.lat * 1e6).toLong()
        val lb = (b.lon * 1e6).toLong()
        return ha * 73856093L xor la * 19349663L xor hb * 83492791L xor lb * 2971215073L
    }

    private fun nearestNode(lat: Double, lon: Double): GeoPoint? {
        val cx = (lat * 4).toInt()
        val cy = (lon * 4).toInt()
        var best: GeoPoint? = null
        var bestD = Double.MAX_VALUE
        val seen = HashSet<Long>()
        for (r in 0..2) {
            for (dx in -r..r) for (dy in -r..r) {
                if (r != 0 && maxOf(abs(dx), abs(dy)) != r) continue
                val cell = cellKey(cx + dx, cy + dy)
                if (!seen.add(cell)) continue
                for (n in nodeGrid[cell] ?: emptyList()) {
                    val d = haversineKm(GeoPoint(lat, lon), n)
                    if (d < bestD) { best = n; bestD = d }
                }
            }
        }
        return if (best != null && bestD <= RAIL_SNAP_KM) best else null
    }

    private fun aStar(start: GeoPoint, goal: GeoPoint): List<GeoPoint>? {
        if (start == goal) return listOf(start)
        val gScore = HashMap<GeoPoint, Double>()
        val parent = HashMap<GeoPoint, GeoPoint?>()
        val closed = HashSet<GeoPoint>()
        gScore[start] = 0.0
        parent[start] = null
        val open = java.util.PriorityQueue<Triple<Double, Double, GeoPoint>>(compareBy({ it.first }, { it.second }))
        open.add(Triple(0.0, 0.0, start))
        while (open.isNotEmpty()) {
            val head = open.poll() ?: break
            val g = head.second
            val node = head.third
            if (!closed.add(node)) continue
            if (node == goal) {
                val path = ArrayList<GeoPoint>()
                var cur: GeoPoint? = node
                while (cur != null) { path.add(cur); cur = parent[cur] }
                return path.reversed()
            }
            for ((nb, w) in adj[node] ?: emptyList()) {
                val ng = g + w
                if (ng < (gScore[nb] ?: Double.MAX_VALUE)) {
                    gScore[nb] = ng
                    parent[nb] = node
                    open.add(Triple(ng + haversineKm(nb, goal), ng, nb))
                }
            }
        }
        return null
    }

    private fun pathCoords(nodes: List<GeoPoint>): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        for (i in 0 until nodes.size - 1) {
            val a = nodes[i]; val b = nodes[i + 1]
            val ordered = if (less(a, b)) a to b else b to a
            val hit = ecoords[edgeKey(ordered.first, ordered.second)]
            val seg = if (hit == null) listOf(a, b)
            else if (a == ordered.first && b == ordered.second) hit.second
            else hit.second.reversed()
            if (out.isEmpty()) out.addAll(seg) else out.addAll(seg.drop(1))
        }
        return out
    }

    private fun snapToRail(points: List<GeoPoint>): Pair<List<List<GeoPoint>>, Boolean>? {
        buildGraph()
        if (adj.isEmpty()) return null
        if (points.size < 2) return null
        val segs = ArrayList<List<GeoPoint>>()
        var railed = true
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val straight = haversineKm(a, b)
            var leg: List<GeoPoint>? = null
            val na = nearestNode(a.lat, a.lon)
            val nb = nearestNode(b.lat, b.lon)
            if (na != null && nb != null) {
                val path = aStar(na, nb)
                if (path != null && path.size > 1) {
                    val cand = ArrayList<GeoPoint>()
                    cand.add(a)
                    for (p in pathCoords(path)) if (p != a && p != b) cand.add(p)
                    cand.add(b)
                    var candKm = 0.0
                    for (t in 0 until cand.size - 1) candKm += haversineKm(cand[t], cand[t + 1])
                    if (straight <= RAIL_RATIO_MIN_KM || candKm <= RAIL_MAX_RATIO * straight) leg = cand
                }
            }
            if (leg == null) { leg = listOf(a, b); railed = false }
            segs.add(leg)
        }
        return segs to railed
    }

    private fun orderedStations(rec: ContainerRecord): List<String> {
        val acts = rec.activity
        val indexed = acts.withIndex().sortedWith(compareBy(
            { val dt = CnTime.parseCnRail(it.value.eventTime); if (dt == null) 1 else 0 },
            { val dt = CnTime.parseCnRail(it.value.eventTime); dt?.toInstant()?.toEpochMilli() ?: 0L },
            { it.index }
        ))
        val stations = mutableListOf<String>()
        for (item in indexed) {
            val name = item.value.eventLocationStation.trim()
            if (name.isNotEmpty() && (stations.isEmpty() || stations.last() != name)) stations.add(name)
        }
        return stations
    }

    private fun nearest(options: List<Db.StationCoord>, ref: GeoPoint): Db.StationCoord {
        var best = options[0]
        var bestD = Double.MAX_VALUE
        for (opt in options) {
            val d = Math.hypot(
                (opt.latitude - ref.lat) * 111.0,
                (opt.longitude - ref.lon) * 111.0 * cos(Math.toRadians(ref.lat))
            )
            if (d < bestD) { best = opt; bestD = d }
        }
        return best
    }

    private fun resolvePoints(names: List<String>, startRef: GeoPoint? = null): List<ResolvedPoint> {
        val points = ArrayList<ResolvedPoint>()
        var ref = startRef
        for (name in names) {
            val opts = db.stationCoords(name)
            val choice = if (opts.isEmpty()) null
            else if (opts.size == 1 || ref == null) opts[0]
            else nearest(opts, ref)
            if (choice != null) ref = GeoPoint(choice.latitude, choice.longitude)
            points.add(
                ResolvedPoint(
                    station = name,
                    latitude = choice?.latitude,
                    longitude = choice?.longitude,
                    matched = choice?.matchedName ?: "",
                    carrier = choice?.carrier ?: "",
                    province = choice?.province ?: ""
                )
            )
        }
        return points
    }

    private fun railedSegments(points: List<ResolvedPoint>): List<List<GeoPoint>> {
        val out = ArrayList<List<GeoPoint>>()
        for (run in segmentsOf(points)) {
            if (run.size < 2) { out.add(run); continue }
            val snapped = runCatching { snapToRail(run) }.getOrNull()
            if (snapped != null && snapped.first.isNotEmpty()) out.addAll(snapped.first) else out.add(run)
        }
        return out
    }

    private fun segmentsOf(points: List<ResolvedPoint>): List<List<GeoPoint>> {
        val segments = ArrayList<List<GeoPoint>>()
        var current = ArrayList<GeoPoint>()
        for (p in points) {
            if (p.latitude == null || p.longitude == null) {
                if (current.isNotEmpty()) { segments.add(current); current = ArrayList() }
            } else {
                current.add(GeoPoint(p.latitude, p.longitude))
            }
        }
        if (current.isNotEmpty()) segments.add(current)
        return segments.filter { it.isNotEmpty() }
    }

    private fun peerTail(current: String, destination: String, ownNames: List<String>, exclude: Set<String>): List<String>? {
        val ownSet = ownNames.toSet()
        var best: List<String>? = null
        var bestKey: Pair<Int, Int>? = null
        for (pid in db.peersContaining(current)) {
            if (exclude.contains(pid)) continue
            val seq = db.travelSequence(pid).map { it.first }
            if (!seq.contains(current) || !seq.contains(destination)) continue
            run {
                val i = seq.indexOf(current)
                val j = (i until seq.size).firstOrNull { seq[it] == destination }
                if (j != null) {
                    val tail = seq.subList(i, j + 1)
                    val overlap = seq.subList(0, i + 1).toSet().intersect(ownSet).size
                    val key = -overlap to tail.size
                    if (best == null || betterKey(key, bestKey!!)) { best = tail; bestKey = key }
                }
            }
            run {
                val i = seq.indexOf(destination)
                val j = (i until seq.size).firstOrNull { seq[it] == current }
                if (j != null) {
                    val tail = seq.subList(i, j + 1).reversed()
                    val overlap = seq.subList(j, seq.size).toSet().intersect(ownSet).size
                    val key = -overlap to tail.size
                    if (best == null || betterKey(key, bestKey!!)) { best = tail; bestKey = key }
                }
            }
        }
        return best
    }

    private fun betterKey(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
        a.first < b.first || (a.first == b.first && a.second < b.second)

    private fun shipmentForContainer(cid: String): Pair<String, String> {
        return try {
            for (s in db.listShipments()) {
                if (Db.parseContainers(s.containers).contains(cid)) {
                    return s.jobNumber to s.portOfDischarge.trim()
                }
            }
            "" to ""
        } catch (e: Exception) { "" to "" }
    }

    private fun coordsForStation(name: String): GeoPoint? {
        for (opt in db.stationCoords(name)) {
            return GeoPoint(opt.latitude, opt.longitude)
        }
        if (name.trim().isNotEmpty()) {
            val hit = db.stationCoordsLike(name)
            if (hit != null) return GeoPoint(hit.first, hit.second)
        }
        return null
    }

    private fun polylineKm(segments: List<List<GeoPoint>>): Double {
        var total = 0.0
        for (seg in segments) {
            for (i in 0 until seg.size - 1) total += haversineKm(seg[i], seg[i + 1])
        }
        return total
    }

    private fun outgatedAtDestination(rec: ContainerRecord, destination: String): Boolean {
        val dest = destination.trim().uppercase()
        if (dest.isEmpty()) return false
        for (act in rec.activity) {
            if (act.eventDescription.trim().uppercase() == "OUT-GATE" &&
                act.eventLocationStation.trim().uppercase() == dest
            ) return true
        }
        return false
    }

    fun progressLabel(travelKm: Double, remainKm: Double, progress: Int?, rough: Boolean): String {
        if (progress == null) return "Journey progress unknown"
        val mark = if (rough) "~ " else ""
        return "$mark${travelKm.toInt()} / ${remainKm.toInt()} km ($progress%)"
    }

    fun containerMapData(cid: String, rec: ContainerRecord): MapData {
        val sm = rec.summary
        val travelNames = orderedStations(rec)
        val origin = travelNames.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: sm.lastEventLocation.trim()
        val current = sm.lastEventLocation.trim().ifEmpty { travelNames.lastOrNull() ?: "" }
        val destination = sm.destination.trim().ifEmpty { sm.etaStation.trim() }
            .ifEmpty { current }
        val travel = resolvePoints(travelNames)
        val travelSegments = railedSegments(travel)
        val travelLast = travel.lastOrNull { it.latitude != null }
        var remainingNames: List<String> = emptyList()
        if (origin.isNotEmpty() && destination.isNotEmpty() && destination != current) {
            remainingNames = peerTail(current, destination, travelNames, setOf(cid)) ?: emptyList()
        }
        if (remainingNames.isEmpty() && origin.isNotEmpty() && destination.isNotEmpty()) {
            val route = db.routeCoords(origin, destination)
            if (route.isNotEmpty()) {
                val idx = route.indices.filter { route[it].stationName == current }
                if (idx.isNotEmpty()) {
                    remainingNames = route.subList(idx.last(), route.size).map { it.stationName }.filter { it.isNotEmpty() }
                }
            }
            if (remainingNames.isEmpty()) {
                val rev = db.routeCoords(destination, origin)
                if (rev.isNotEmpty()) {
                    val idx = rev.indices.filter { rev[it].stationName == current }
                    if (idx.isNotEmpty()) {
                        remainingNames = rev.subList(0, idx.last() + 1).map { it.stationName }.filter { it.isNotEmpty() }.reversed()
                    }
                }
            }
            if (remainingNames.isEmpty() && destination != current) {
                remainingNames = if (current.isNotEmpty()) listOf(current, destination) else listOf(destination)
            } else if (destination == current) {
                remainingNames = if (current.isNotEmpty()) listOf(current) else emptyList()
            }
        }
        var remaining = resolvePoints(remainingNames, travelLast?.let { GeoPoint(it.latitude!!, it.longitude!!) })
        if (remaining.isNotEmpty() && travelLast != null &&
            remaining[0].station == current && remaining[0].latitude == null
        ) {
            remaining = remaining.toMutableList().also {
                it[0] = ResolvedPoint(current, travelLast.latitude, travelLast.longitude, travelLast.matched, travelLast.carrier, travelLast.province)
            }
        }
        val remainingSegments = railedSegments(remaining)
        val unresolved = mutableListOf<String>()
        for (p in travel + remaining) {
            if ((p.latitude == null || p.longitude == null) && !unresolved.contains(p.station)) unresolved.add(p.station)
        }
        val (job, pod) = shipmentForContainer(cid)
        val travelKm = Math.round(polylineKm(travelSegments)).toInt()
        val remainKm = Math.round(polylineKm(remainingSegments)).toInt()
        val totalKm = travelKm + remainKm
        var rough = true
        var progress: Int? = when {
            totalKm <= 0 -> null
            remainKm <= 0 -> 100
            else -> maxOf(0, min(100, Math.round(100.0 * travelKm / totalKm).toInt()))
        }
        if (outgatedAtDestination(rec, destination)) progress = 100
        if (progress == 100) rough = false
        return MapData(
            equipmentId = cid, origin = origin, current = current, destination = destination,
            travel = travel, remaining = remaining,
            travelSegments = travelSegments, remainingSegments = remainingSegments,
            unresolved = unresolved, job = job, pod = pod, podCoords = coordsForStation(pod),
            progress = progress, travelKm = travelKm, remainKm = remainKm, rough = rough,
            progressLabel = progressLabel(travelKm.toDouble(), remainKm.toDouble(), progress, rough)
        )
    }

    companion object {
        private const val RAIL_MERGE_KM = 0.3
        private const val RAIL_SNAP_KM = 30.0
        private const val RAIL_MAX_RATIO = 2.5
        private const val RAIL_RATIO_MIN_KM = 3.0
    }
}
