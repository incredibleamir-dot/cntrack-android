package com.avancerifs.cntrack.data

import org.json.JSONArray
import org.json.JSONObject

data class Shipment(
    val id: Long = 0,
    val jobNumber: String = "",
    val mbl: String = "",
    val hbl: String = "",
    val poNumber: String = "",
    val portOfDischarge: String = "",
    val etaDate: String = "",
    val customer: String = "",
    val containers: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class Summary(
    val equipmentId: String = "",
    val lastEvent: String = "",
    val lastEventTime: String = "",
    val lastEventLocation: String = "",
    val customsDescription: String = "",
    val customsTimestamp: String = "",
    val lastFreeDay: String = "",
    val waybillStatus: String = "",
    val destination: String = "",
    val etaTime: String = "",
    val etaDescription: String = "",
    val etaStation: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("equipmentId", equipmentId)
        put("lastEvent", lastEvent)
        put("lastEventTime", lastEventTime)
        put("lastEventLocation", lastEventLocation)
        put("customsDescription", customsDescription)
        put("customsTimestamp", customsTimestamp)
        put("lastFreeDay", lastFreeDay)
        put("waybillStatus", waybillStatus)
        put("destination", destination)
        put("etaTime", etaTime)
        put("etaDescription", etaDescription)
        put("etaStation", etaStation)
    }

    companion object {
        fun from(o: JSONObject?): Summary {
            if (o == null) return Summary()
            return Summary(
                equipmentId = o.optString("equipmentId"),
                lastEvent = o.optString("lastEvent"),
                lastEventTime = o.optString("lastEventTime"),
                lastEventLocation = o.optString("lastEventLocation"),
                customsDescription = o.optString("customsDescription"),
                customsTimestamp = o.optString("customsTimestamp"),
                lastFreeDay = o.optString("lastFreeDay"),
                waybillStatus = o.optString("waybillStatus"),
                destination = o.optString("destination"),
                etaTime = o.optString("etaTime"),
                etaDescription = o.optString("etaDescription"),
                etaStation = o.optString("etaStation")
            )
        }
    }
}

data class Activity(
    val equipmentId: String = "",
    val eventTime: String = "",
    val eventDescription: String = "",
    val eventLocationStation: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("EquipmentId", equipmentId)
        put("Event_Time", eventTime)
        put("Event_Description", eventDescription)
        put("Event_Location_Station", eventLocationStation)
    }

    companion object {
        fun from(o: JSONObject): Activity = Activity(
            equipmentId = o.optString("EquipmentId"),
            eventTime = o.optString("Event_Time"),
            eventDescription = o.optString("Event_Description"),
            eventLocationStation = o.optString("Event_Location_Station")
        )
    }
}

data class EtaRow(
    val equipmentId: String = "",
    val waybillStatus: String = "",
    val destinationStation: String = "",
    val etaTime: String = "",
    val etaDescription: String = "",
    val etaLocationStation: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("EquipmentId", equipmentId)
        put("Way_Bill_Status", waybillStatus)
        put("Destination_Station", destinationStation)
        put("ETA_Time", etaTime)
        put("ETA_Description", etaDescription)
        put("ETA_Location_Station", etaLocationStation)
    }

    companion object {
        fun from(o: JSONObject): EtaRow = EtaRow(
            equipmentId = o.optString("EquipmentId"),
            waybillStatus = o.optString("Way_Bill_Status"),
            destinationStation = o.optStringOrNull("Destination_Station"),
            etaTime = o.optString("ETA_Time"),
            etaDescription = o.optString("ETA_Description"),
            etaLocationStation = o.optString("ETA_Location_Station")
        )
    }
}

data class CustomsRow(
    val equipmentId: String = "",
    val description: String = "",
    val timestamp: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("EquipmentId", equipmentId)
        put("CustomsHold_Description", description)
        put("CustomsHold_Timestamp", timestamp)
    }

    companion object {
        fun from(o: JSONObject): CustomsRow = CustomsRow(
            equipmentId = o.optString("EquipmentId"),
            description = o.optString("CustomsHold_Description"),
            timestamp = o.optString("CustomsHold_Timestamp")
        )
    }
}

data class StorageRow(
    val equipmentId: String = "",
    val lastFreeDay: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("EquipmentId", equipmentId)
        put("LastFreeDay", lastFreeDay)
    }

    companion object {
        fun from(o: JSONObject): StorageRow = StorageRow(
            equipmentId = o.optString("EquipmentId"),
            lastFreeDay = o.optString("LastFreeDay")
        )
    }
}

data class ContainerRecord(
    val equipmentId: String = "",
    val summary: Summary = Summary(),
    val activity: List<Activity> = emptyList(),
    val eta: List<EtaRow> = emptyList(),
    val customStatus: List<CustomsRow> = emptyList(),
    val storage: List<StorageRow> = emptyList(),
    val inMaster: String = "",
    val completed: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("equipmentId", equipmentId)
        put("summary", summary.toJson())
        put("activity", JSONArray().apply { activity.forEach { put(it.toJson()) } })
        put("eta", JSONArray().apply { eta.forEach { put(it.toJson()) } })
        put("customStatus", JSONArray().apply { customStatus.forEach { put(it.toJson()) } })
        put("storage", JSONArray().apply { storage.forEach { put(it.toJson()) } })
        put("inMaster", inMaster)
        if (completed) put("_completed", true)
    }

    companion object {
        fun from(json: String): ContainerRecord = from(JSONObject(json))

        fun from(o: JSONObject): ContainerRecord {
            fun rows(name: String): JSONArray = o.optJSONArray(name) ?: JSONArray()
            val activity = rows("activity").let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { Activity.from(it) }
            }
            val eta = rows("eta").let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { EtaRow.from(it) }
            }
            val customs = rows("customStatus").let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .filter { it.length() > 0 }.map { CustomsRow.from(it) }
            }
            val storage = rows("storage").let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .filter { it.length() > 0 }.map { StorageRow.from(it) }
            }
            return ContainerRecord(
                equipmentId = o.optString("equipmentId"),
                summary = Summary.from(o.optJSONObject("summary")),
                activity = activity,
                eta = eta,
                customStatus = customs,
                storage = storage,
                inMaster = o.optString("inMaster"),
                completed = o.optBoolean("_completed", false)
            )
        }
    }
}

fun JSONObject.optStringOrNull(key: String): String {
    if (isNull(key)) return ""
    return optString(key, "")
}
