package com.avancerifs.cntrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.BuildConfig
import com.avancerifs.cntrack.R
import com.avancerifs.cntrack.ui.components.SectionCard
import com.avancerifs.cntrack.ui.theme.AppColors

@Composable
fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(color = AppColors.surface, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "CNTrack logo",
                    modifier = Modifier.size(88.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("CNTrack", color = AppColors.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    color = AppColors.inkMuted, fontSize = 13.sp
                )
            }
        }

        SectionCard("About this app", AppColors.accentAbout) {
            Text(
                "CNTrack keeps CN rail container shipments in one place. Track containers " +
                    "live from the CN Rail API, review ETA revisions and delays, watch free-day " +
                    "and customs-hold alerts, explore an interactive route map with rail-accurate " +
                    "paths, and export a container summary as a PDF to share.",
                color = AppColors.ink, fontSize = 14.sp, lineHeight = 20.sp
            )
        }

        SectionCard("Developer", AppColors.accentAbout) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Developed by", color = AppColors.inkMuted, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("Amir Arshad", color = AppColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text("Avancer IFS", color = AppColors.inkMuted, fontSize = 12.sp)
        }

        SectionCard("Details", AppColors.accentAbout) {
            InfoRow("Application ID", BuildConfig.APPLICATION_ID)
            InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Data source", "CN Rail tracking API + local SQLite cache")
            InfoRow("Maps", "OpenStreetMap via osmdroid")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = AppColors.inkMuted, fontSize = 12.sp, modifier = Modifier.width(120.dp))
        Text(value, color = AppColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
