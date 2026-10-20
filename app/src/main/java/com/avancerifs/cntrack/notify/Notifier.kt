package com.avancerifs.cntrack.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.avancerifs.cntrack.MainActivity
import com.avancerifs.cntrack.R
import java.util.concurrent.atomic.AtomicInteger

object Notifier {

    const val CHANNEL_ID = "cn_milestones"
    private val counter = AtomicInteger(1000)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Container milestones",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Rail milestones: Ramped, Deramped, In-Gate, Out-Gate"
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun notify(
        context: Context,
        cid: String,
        milestone: String,
        job: String,
        location: String,
        time: String
    ) {
        ensureChannel(context)
        if (!canNotify(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("cid", cid)
        }
        val pi = PendingIntent.getActivity(
            context,
            (cid + milestone).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = listOf(
            job.trim().takeIf { it.isNotEmpty() },
            location.trim().takeIf { it.isNotEmpty() },
            time.trim().takeIf { it.isNotEmpty() }
        ).filterNotNull().joinToString("  •  ").ifBlank { "Milestone reached" }

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$milestone — $cid")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(counter.incrementAndGet(), n) }
    }

    fun test(context: Context) {
        notify(context, "TEST0000000", "TEST", "Sample job", "Test location", "")
    }
}
