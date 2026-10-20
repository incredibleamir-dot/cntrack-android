package com.avancerifs.cntrack

import android.app.Application
import com.avancerifs.cntrack.logic.Diag
import com.avancerifs.cntrack.notify.Notifier
import com.avancerifs.cntrack.notify.NotifyScheduler
import org.osmdroid.config.Configuration
import java.io.File

class CnTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.install(this)
        Notifier.ensureChannel(this)
        NotifyScheduler.schedule(this)
        val cfg = Configuration.getInstance()
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        cfg.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        val tiles = File(base, "tiles")
        base.mkdirs()
        tiles.mkdirs()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tiles
    }
}
