package com.avancerifs.cntrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avancerifs.cntrack.ui.AppScaffold
import com.avancerifs.cntrack.ui.theme.CnTrackTheme

class MainActivity : ComponentActivity() {

    private val pendingCid = mutableStateOf<String?>(null)

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingCid.value = intent?.getStringExtra("cid")
        maybeRequestNotifPermission()
        setContent {
            CnTrackTheme {
                val vm: MainViewModel = viewModel()
                AppScaffold(
                    vm,
                    initialCid = pendingCid.value,
                    onInitialCidConsumed = { pendingCid.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCid.value = intent.getStringExtra("cid")
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
