package com.avancerifs.cntrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.ContainerRow
import com.avancerifs.cntrack.GroupMode
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.logic.CnTime
import com.avancerifs.cntrack.ui.components.CustomsPill
import com.avancerifs.cntrack.ui.components.EmptyState
import com.avancerifs.cntrack.ui.components.LfdBadge
import com.avancerifs.cntrack.ui.components.SearchField
import com.avancerifs.cntrack.ui.components.StatusPill
import com.avancerifs.cntrack.ui.components.VerdictPill
import com.avancerifs.cntrack.ui.theme.AppColors

@Composable
fun ContainersScreen(
    vm: MainViewModel,
    listState: LazyListState = rememberLazyListState(),
    onOpenContainer: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val showArchived = vm.showArchivedContainers
    val groups = vm.watchlistGrouped(search, "", showArchived)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SearchField(search, { search = it }, "Search container, job, MBL, HBL or PO")
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                GroupMode.entries.forEach { m ->
                    FilterChip(
                        selected = vm.groupMode == m,
                        onClick = { vm.updateGroupMode(m) },
                        label = { Text(m.label) },
                        leadingIcon = if (vm.groupMode == m) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
        item {
            Surface(color = AppColors.surface, shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show closed / archived", color = AppColors.ink, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showArchived, onCheckedChange = { vm.updateShowArchivedContainers(it) })
                    IconButton(onClick = { vm.refresh(true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = AppColors.accentContainers)
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            item { EmptyState("No containers match. Add a shipment or tap Refresh.") }
        }

        groups.forEach { g ->
            item(key = "h_${g.title}") {
                Column(Modifier.padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.title, color = AppColors.accentContainers, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (g.pending) {
                            Spacer(Modifier.width(6.dp))
                            Pill("TBA", AppColors.warningInk, AppColors.warningBg)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${g.rows.size} container(s)", color = AppColors.inkMuted, fontSize = 11.sp)
                    }
                    val sub = listOf(g.customer, CnTime.portEtaDate(g.etaDate))
                        .filter { it.isNotBlank() }.joinToString("  •  ")
                    if (sub.isNotBlank()) {
                        Text(sub, color = AppColors.inkMuted, fontSize = 11.sp)
                    }
                }
            }
            items(g.rows, key = { it.cid }) { r ->
                ContainerCard(r, onOpenContainer)
            }
        }
    }
}

@Composable
fun ContainerCard(r: ContainerRow, onOpenContainer: (String) -> Unit) {
    Surface(
        color = AppColors.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenContainer(r.cid) }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.cid, color = AppColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                StatusPill(r.status)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${r.location} — ${r.lastEvent}",
                color = AppColors.inkMuted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("Updated ${r.eventTime}", color = AppColors.inkMuted, fontSize = 11.sp)

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VerdictPill(r.verdict)
                Pill(r.src, AppColors.neutralInk, AppColors.neutralBg)
                CustomsPill(r.customsState)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("ETA ", color = AppColors.inkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(r.eta, color = AppColors.ink, fontSize = 12.sp)
            }
            if (r.lfd.isNotBlank() && r.lfd != "—") {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LFD ", color = AppColors.inkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(r.lfd, color = AppColors.ink, fontSize = 12.sp)
                }
            }
            if (r.lfdDays != null) {
                Spacer(Modifier.height(6.dp))
                LfdBadge(r.lfdDays)
            }
        }
    }
}

@Composable
private fun Pill(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    com.avancerifs.cntrack.ui.components.Pill(text, fg, bg)
}
