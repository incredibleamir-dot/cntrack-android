package com.avancerifs.cntrack.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object CnTime {

    private val TZ_MAP = mapOf(
        "PT" to "America/Vancouver", "PDT" to "America/Vancouver", "PST" to "America/Vancouver",
        "MT" to "America/Edmonton", "MDT" to "America/Edmonton", "MST" to "America/Edmonton",
        "CT" to "America/Winnipeg", "CDT" to "America/Winnipeg", "CST" to "America/Winnipeg",
        "ET" to "America/Toronto", "EDT" to "America/Toronto", "EST" to "America/Toronto",
        "AT" to "America/Halifax", "ADT" to "America/Halifax", "AST" to "America/Halifax",
        "NT" to "America/St_Johns", "NDT" to "America/St_Johns", "NST" to "America/St_Johns"
    )

    private val FIXED_OFFSETS = mapOf(
        "PT" to -8.0, "PDT" to -7.0, "PST" to -8.0,
        "MT" to -7.0, "MDT" to -6.0, "MST" to -7.0,
        "CT" to -6.0, "CDT" to -5.0, "CST" to -6.0,
        "ET" to -5.0, "EDT" to -4.0, "EST" to -5.0,
        "AT" to -4.0, "ADT" to -3.0, "AST" to -4.0,
        "NT" to -3.5, "NDT" to -2.5, "NST" to -3.5
    )

    private val TRAILING_TZ = Regex("\\s*([A-Za-z]{2,4})\\s*$")
    private val CN_RAIL = Regex(
        "^(\\d{4}-\\d{2}-\\d{2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?\\s*([A-Za-z]{2,4})?\\s*$"
    )

    private val FMT_PHP = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)
    private val FMT_DASH = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm", Locale.ENGLISH)
    private val FMT_LFD = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    private val FMT_PORT_ETA = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val FMT_SHORT = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH)

    fun cnTimezoneMap(code: String?): String =
        TZ_MAP[(code ?: "").trim().uppercase(Locale.ENGLISH)] ?: "America/Toronto"

    private fun zoneFor(code: String?): ZoneId {
        val iana = cnTimezoneMap(code)
        return runCatching { ZoneId.of(iana) }.getOrElse {
            val hrs = FIXED_OFFSETS[(code ?: "").uppercase(Locale.ENGLISH)] ?: -5.0
            ZoneOffset.ofTotalSeconds((hrs * 3600).toInt())
        }
    }

    fun extractTzCode(value: Any?): String? {
        val m = TRAILING_TZ.find(value?.toString() ?: "") ?: return null
        val code = m.groupValues[1].uppercase(Locale.ENGLISH)
        return if (TZ_MAP.containsKey(code)) code else null
    }

    private fun tryIso(raw: String): ZonedDateTime? {
        val t = raw.replace("Z", "+00:00")
        runCatching { return OffsetDateTime.parse(t).toZonedDateTime() }
        runCatching { return ZonedDateTime.parse(t) }
        runCatching { return LocalDateTime.parse(t).atZone(ZoneOffset.UTC) }
        runCatching { return LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC) }
        return null
    }

    fun parseCnRail(value: Any?): ZonedDateTime? {
        val s = value?.toString()?.trim() ?: return null
        if (s.isEmpty() || s.equals("not available yet", true)) return null
        tryIso(s)?.let { return it }
        val m = CN_RAIL.matchEntire(s) ?: return null
        return runCatching {
            val (dateP, hh, mm, ss, tz) = m.destructured
            val (y, mo, d) = dateP.split("-").map { it.toInt() }
            val ldt = LocalDateTime.of(
                y, mo, d, (hh.ifEmpty { "0" }).toInt(),
                (mm.ifEmpty { "0" }).toInt(), (ss.ifEmpty { "0" }).toInt()
            )
            ldt.atZone(zoneFor(if (tz.isEmpty()) "ET" else tz))
        }.getOrNull()
    }

    private fun phpLike(dt: ZonedDateTime, tzCode: String): String =
        "${dt.format(FMT_PHP)} $tzCode"

    fun shortMdHi(dt: ZonedDateTime, tzCode: String): String =
        "${dt.format(FMT_SHORT)} $tzCode"

    fun formatLocalTime(utcTime: Any?, tzCode: String?, defaultTz: String = "ET"): String? {
        if (utcTime == null || utcTime.toString().trim().isEmpty()) return null
        val code = (tzCode ?: defaultTz).ifEmpty { "ET" }.uppercase(Locale.ENGLISH)
        val dt = parseCnRail(utcTime) ?: return utcTime.toString()
        return runCatching {
            phpLike(dt.toInstant().atZone(zoneFor(code)), code)
        }.getOrElse { utcTime.toString() }
    }

    fun displayTime(value: Any?, fallbackTz: String = "ET"): Pair<String, String> {
        val sv = value?.toString()?.trim() ?: ""
        if (sv.isEmpty() || sv.equals("not available yet", true)) return "-" to "-"
        val code = extractTzCode(sv)
        val dt = parseCnRail(sv) ?: return sv to "-"
        if (code != null) {
            return phpLike(dt, code) to dt.format(FMT_DATE)
        }
        val fb = fallbackTz.ifEmpty { "ET" }.uppercase(Locale.ENGLISH)
        return runCatching {
            val local = dt.toInstant().atZone(zoneFor(fb))
            phpLike(local, fb) to local.format(FMT_DATE)
        }.getOrElse { sv to "-" }
    }

    fun customsStatus(raw: Any?): String {
        val low = raw?.toString()?.trim()?.lowercase(Locale.ENGLISH) ?: return "not_released"
        if (low.isEmpty()) return "not_released"
        for (phrase in listOf(
            "broker filed pars", "broker has filed",
            "cleared customs", "remanifest to freight forwarder"
        )) {
            if (low.contains(phrase)) return "cleared"
        }
        if (low.contains("in bond")) return "hold"
        return "unknown"
    }

    fun isWeekend(value: Any?): Boolean {
        val dt = parseCnRail(value) ?: return false
        return dt.dayOfWeek.value >= 6
    }

    fun dashboardTime(value: Any?, fallbackTz: String = "ET"): String {
        val sv = value?.toString()?.trim() ?: ""
        if (sv.isEmpty() || sv.lowercase(Locale.ENGLISH) in listOf("not available yet", "none")) return "-"
        val code = extractTzCode(sv) ?: fallbackTz.ifEmpty { "ET" }.uppercase(Locale.ENGLISH)
        val dt = parseCnRail(sv) ?: return sv
        return runCatching {
            val local = if (extractTzCode(sv) != null) dt
            else dt.toInstant().atZone(zoneFor(code))
            "${local.format(FMT_DASH)} $code"
        }.getOrElse { sv }
    }

    fun dashboardLfd(value: Any?): String {
        val sv = value?.toString()?.trim() ?: ""
        if (sv.isEmpty() || sv.lowercase(Locale.ENGLISH) in listOf("not available yet", "none")) return "-"
        val dt = parseCnRail(sv) ?: return sv
        return dt.format(FMT_LFD)
    }

    fun portEtaDate(value: Any?): String {
        val sv = value?.toString()?.trim() ?: ""
        if (sv.isEmpty()) return ""
        val d = runCatching { LocalDate.parse(sv.take(10)) }.getOrNull() ?: return sv
        return d.format(FMT_PORT_ETA)
    }
}
