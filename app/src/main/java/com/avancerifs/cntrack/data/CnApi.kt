package com.avancerifs.cntrack.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CnApi {

    const val API_BASE = ""
    const val CACHE_TTL_HOURS = 24.0

    const val SHEET_SYNC_URL = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun fetchTrackingLive(
        containerIds: List<String>,
        activityLimit: Int = 0,
        baseUrl: String = API_BASE
    ): Map<String, ContainerRecord> {
        if (baseUrl.isBlank()) throw RuntimeException("Tracking URL is not set. Open ⋮ → Settings.")
        val ids = containerIds.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return emptyMap()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("containers", ids.joinToString(","))
            .apply { if (activityLimit > 0) addQueryParameter("activityLimit", activityLimit.toString()) }
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw RuntimeException("Empty response")
            val data = JSONObject(body)
            if (!data.optBoolean("success", false)) {
                throw RuntimeException(data.optString("error", "API error"))
            }
            val results = data.optJSONObject("results") ?: return emptyMap()
            val out = LinkedHashMap<String, ContainerRecord>()
            for (key in results.keys()) {
                val obj = results.optJSONObject(key) ?: continue
                out[key] = ContainerRecord.from(obj)
            }
            return out
        }
    }

    fun fetchShipmentsFromSheet(url: String = SHEET_SYNC_URL): List<Shipment> {
        if (url.isBlank()) return emptyList()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw RuntimeException("Empty response")
            val data = JSONObject(body)
            if (!data.optBoolean("success", false)) {
                throw RuntimeException(data.optString("error", "Sheet sync error"))
            }
            val arr = data.optJSONArray("shipments") ?: return emptyList()
            val out = mutableListOf<Shipment>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val job = o.optString("job").trim()
                val mbl = o.optString("mbl").trim()
                if (job.isEmpty() && mbl.isEmpty()) continue
                val contArr = o.optJSONArray("containers")
                val conts = if (contArr != null) {
                    (0 until contArr.length())
                        .map { contArr.optString(it).trim() }
                        .filter { it.isNotEmpty() }
                } else emptyList()
                out.add(
                    Shipment(
                        jobNumber = job,
                        mbl = mbl,
                        hbl = normBlank(o.optString("hbl")),
                        poNumber = normBlank(o.optString("po")),
                        portOfDischarge = o.optString("portOfDischarge").trim().uppercase(),
                        etaDate = o.optString("etaDate").trim(),
                        customer = o.optString("customer").trim(),
                        containers = conts.joinToString(", ")
                    )
                )
            }
            return out
        }
    }

    private val HBL_SPLIT = Regex("[\\r\\n/,;]+")

    private fun normBlank(raw: String): String {
        val parts = HBL_SPLIT.split(raw)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val v = parts.joinToString(" / ")
        return when (v.uppercase()) {
            "NA", "N/A", "-", "NIL", "NULL", "" -> ""
            else -> v
        }
    }
}
