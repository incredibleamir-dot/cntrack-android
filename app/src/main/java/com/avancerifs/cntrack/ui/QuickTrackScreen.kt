package com.avancerifs.cntrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.ui.components.DetailKpi
import com.avancerifs.cntrack.ui.components.SectionCard
import com.avancerifs.cntrack.ui.components.StatusPill
import com.avancerifs.cntrack.ui.components.VerdictPill
import com.avancerifs.cntrack.ui.theme.AppColors

@Composable
fun QuickTrackScreen(vm: MainViewModel, onOpenContainer: (String) -> Unit) {
    var cid by remember { mutableStateOf("") }
    var resultCid by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }
    val row = resultCid?.let { vm.containerRow(it) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard("Quick track", AppColors.accentQuick) {
            Text(
                "Type a container number to fetch the latest status from CN Rail.",
                color = AppColors.inkMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = cid,
                onValueChange = { cid = it.uppercase() },
                label = { Text("Container number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.surface,
                    unfocusedContainerColor = AppColors.surface,
                    focusedIndicatorColor = AppColors.accentQuick,
                    unfocusedIndicatorColor = AppColors.line,
                    focusedTextColor = AppColors.ink,
                    unfocusedTextColor = AppColors.ink,
                    cursorColor = AppColors.accentQuick
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    notice = ""
                    resultCid = null
                    vm.quickTrack(cid) { res ->
                        if (res.startsWith("OK:")) {
                            resultCid = res.removePrefix("OK:")
                        } else {
                            notice = res
                        }
                    }
                },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.loading) "Tracking…" else "Track container")
            }
            if (notice.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(notice, color = AppColors.dangerInk, fontSize = 13.sp)
            }
        }

        if (row != null) {
            SectionCard(row.cid, AppColors.accentQuick) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(row.status)
                    VerdictPill(row.verdict)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailKpi("Location", row.location)
                    DetailKpi("Last event", row.lastEvent)
                    DetailKpi("Event time", row.eventTime)
                    DetailKpi("Destination", row.etaStation)
                    DetailKpi("ETA", row.eta)
                    DetailKpi("Last free day", row.lfd)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onOpenContainer(row.cid) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open details")
                }
            }
        }
    }
}
