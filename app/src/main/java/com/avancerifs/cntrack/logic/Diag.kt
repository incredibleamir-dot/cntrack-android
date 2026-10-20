package com.avancerifs.cntrack.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object Diag {

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
    @Volatile private var installed = false
    private var logFile: File? = null
    private val main = Handler(Looper.getMainLooper())

    fun file(context: Context): File = logFile ?: File(context.filesDir, "diagnostics.log")

    fun install(context: Context) {
        if (installed) return
        installed = true
        val f = File(context.filesDir, "diagnostics.log")
        logFile = f
        append(f, "=== CNTrack diagnostics started ${stamp.format(Date())} ===")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            append(f, "CRASH in ${t.name} ${stamp.format(Date())}\n${Log.getStackTraceString(e)}")
            prev?.uncaughtException(t, e)
        }
        Thread({ watchdog(f) }, "cntrack-watchdog").apply { isDaemon = true }.start()
    }

    fun clear(context: Context) {
        file(context).writeText("=== cleared ${stamp.format(Date())} ===\n")
    }

    private fun watchdog(f: File) {
        var reported = false
        while (true) {
            val done = AtomicBoolean(false)
            main.post { done.set(true) }
            Thread.sleep(1500)
            if (!done.get()) {
                if (!reported) {
                    reported = true
                    append(
                        f,
                        "STALL ${stamp.format(Date())} — main thread blocked >1.5s\n${dumpStacks()}"
                    )
                }
                var guard = 0
                while (!done.get() && guard < 1200) {
                    Thread.sleep(100)
                    guard++
                }
                append(f, "=== recovered ${stamp.format(Date())} ===")
            } else {
                reported = false
            }
        }
    }

    private fun dumpStacks(): String {
        val sb = StringBuilder()
        for ((t, st) in Thread.getAllStackTraces()) {
            sb.append("\n--- ${t.name} (${t.state}) ---\n")
            for (el in st) sb.append("    at $el\n")
        }
        return sb.toString()
    }

    @Synchronized
    private fun append(f: File, text: String) {
        runCatching { f.appendText(text + "\n") }
    }
}
