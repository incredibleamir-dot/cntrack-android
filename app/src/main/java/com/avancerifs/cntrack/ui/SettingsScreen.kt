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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.ui.components.SectionCard
import com.avancerifs.cntrack.ui.theme.AppColors

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var tracking by remember { mutableStateOf(vm.apiBaseUrl) }
    var sheet by remember { mutableStateOf(vm.sheetSyncUrl) }
    var savedMsg by remember { mutableStateOf("") }

    LaunchedEffect(vm.apiBaseUrl, vm.sheetSyncUrl) {
        tracking = vm.apiBaseUrl
        sheet = vm.sheetSyncUrl
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard("API endpoints", AppColors.accentAbout) {
            Text(
                "Paste the /exec URLs of your deployed Apps Script projects. " +
                    "Both settings are saved on this device.",
                color = AppColors.inkMuted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tracking,
                onValueChange = { tracking = it; savedMsg = "" },
                label = { Text("Tracking proxy URL") },
                placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                supportingText = { Text("Returns CN Rail tracking data for the app") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sheet,
                onValueChange = { sheet = it; savedMsg = "" },
                label = { Text("Sheet sync URL") },
                placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                supportingText = { Text("Returns the shipment rows from your Main Sheet") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Row {
                Button(
                    onClick = {
                        vm.saveEndpoints(tracking, sheet)
                        savedMsg = "Saved"
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.primary,
                        contentColor = AppColors.onPrimary
                    )
                ) { Text("Save") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {
                        tracking = ""
                        sheet = ""
                        vm.saveEndpoints("", "")
                        savedMsg = "Cleared"
                    }
                ) { Text("Clear") }
            }
            if (savedMsg.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(savedMsg, color = AppColors.successInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        SectionCard("How to set up", AppColors.accentAbout) {
            Step("1", "Deploy cn-rail-proxy.gs as a Web App (execute as you, access for anyone). It stores your CN Rail API key and returns the JSON the app expects.")
            Spacer(Modifier.height(8.dp))
            Step("2", "Deploy sheet-sync.gs as a Web App. Point it at your shipment sheet ID and share that sheet with the script owner.")
            Spacer(Modifier.height(8.dp))
            Step("3", "Paste both /exec URLs above and tap Save. Pull shipments from ⋮ → Pull shipments from sheet.")
        }

        SectionCard("Notes", AppColors.accentAbout) {
            Text(
                "• CN credentials live only in the Apps Script project, never in the app.\n" +
                    "• Tracking responses are cached for 24 hours. Use Refresh to force a live pull.\n" +
                    "• An empty tracking URL disables live updates; cached data still displays.\n" +
                    "• The map and container history work without any URL configured.",
                color = AppColors.ink, fontSize = 12.sp, lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row {
        Text(
            number,
            color = AppColors.accentAbout,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp)
        )
        Text(text, color = AppColors.ink, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
