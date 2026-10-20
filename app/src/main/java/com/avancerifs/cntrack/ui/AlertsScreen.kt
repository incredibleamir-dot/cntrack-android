package com.avancerifs.cntrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.logic.Alert
import com.avancerifs.cntrack.ui.components.EmptyState
import com.avancerifs.cntrack.ui.components.Pill
import com.avancerifs.cntrack.ui.theme.AppColors

@Composable
fun AlertsScreen(vm: MainViewModel, onOpenContainer: (String) -> Unit) {
    val alerts = vm.alerts()
    val summary = vm.alertSummary()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(color = AppColors.surface, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Alerts", color = AppColors.accentDetails, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(summary, color = AppColors.inkMuted, fontSize = 13.sp)
                }
            }
        }
        if (alerts.isEmpty()) {
            item { EmptyState("No alerts. All clear.") }
        }
        items(alerts, key = { "${it.cid}_${it.kind}" }) { a -> AlertCard(a, onOpenContainer) }
    }
}

@Composable
private fun AlertCard(a: Alert, onOpenContainer: (String) -> Unit) {
    val accent = when (a.kind) {
        "hold" -> AppColors.dangerFill
        "lfd_expired" -> AppColors.dangerFill
        "delayed" -> AppColors.warningFill
        else -> AppColors.warningFill
    }
    val (fg, bg) = when (a.kind) {
        "hold" -> AppColors.dangerInk to AppColors.dangerBg
        "lfd_expired" -> AppColors.dangerInk to AppColors.dangerBg
        "delayed" -> AppColors.warningInk to AppColors.warningBg
        else -> AppColors.warningInk to AppColors.warningBg
    }

    Surface(
        color = AppColors.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenContainer(a.cid) }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, color = AppColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${a.cid} • ${a.job}", color = AppColors.inkMuted, fontSize = 12.sp)
                if (a.detail.isNotEmpty()) {
                    Text(a.detail, color = AppColors.inkMuted, fontSize = 12.sp)
                }
            }
            Pill(a.kind.replace("_", " "), fg, bg)
        }
    }
}
