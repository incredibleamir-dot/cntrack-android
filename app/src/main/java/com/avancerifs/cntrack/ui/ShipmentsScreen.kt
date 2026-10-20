package com.avancerifs.cntrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.GroupMode
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.ShipmentView
import com.avancerifs.cntrack.data.Db
import com.avancerifs.cntrack.data.Shipment
import com.avancerifs.cntrack.ui.components.EmptyState
import com.avancerifs.cntrack.ui.components.Pill
import com.avancerifs.cntrack.ui.components.SearchField
import com.avancerifs.cntrack.ui.components.SectionCard
import com.avancerifs.cntrack.ui.components.StatusPill
import com.avancerifs.cntrack.ui.theme.AppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ETA_IN: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val ETA_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

private fun fmtEta(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    return runCatching { LocalDate.parse(s.take(10), ETA_IN).format(ETA_OUT) }.getOrDefault(s)
}

@Composable
fun ShipmentsScreen(
    vm: MainViewModel,
    listState: LazyListState = rememberLazyListState(),
    onOpenContainer: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Shipment?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var expandedArchived by remember { mutableStateOf(false) }

    val (active, archived) = vm.shipmentsView(search)
    val customers = vm.customers()
    val activeGroups: List<Pair<String, List<ShipmentView>>> = vm.groupShipments(active)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SearchField(search, { search = it }, "Search job, MBL, HBL, PO, customer or container")
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
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
                SectionCard("Active shipments (${active.size})", AppColors.accentShipments) {
                    if (active.isEmpty()) {
                        EmptyState("No active shipments.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            activeGroups.forEach { (cust, list) ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (cust.isNotEmpty()) {
                                        Text(
                                            "$cust  (${list.size})",
                                            color = AppColors.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    list.forEach { sv ->
                                        ShipmentCard(
                                            sv = sv,
                                            vm = vm,
                                            onOpenContainer = onOpenContainer,
                                            onEdit = { editing = sv.shipment; showForm = true },
                                            onArchive = { vm.archiveJob(sv.shipment.jobNumber) },
                                            onDelete = { vm.deleteShipment(sv.shipment.jobNumber) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (archived.isNotEmpty()) {
                item {
                    SectionCard("Archived (${archived.size})", AppColors.neutralInk) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expandedArchived = !expandedArchived }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (expandedArchived) "Hide closed shipments" else "Show closed shipments",
                                color = AppColors.inkMuted, fontSize = 13.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (expandedArchived) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null, tint = AppColors.inkMuted, modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(visible = expandedArchived) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                archived.forEach { sv ->
                                    ShipmentCard(
                                        sv = sv,
                                        vm = vm,
                                        onOpenContainer = onOpenContainer,
                                        onEdit = { editing = sv.shipment; showForm = true },
                                        onArchive = { vm.restoreJob(sv.shipment.jobNumber) },
                                        onDelete = { vm.deleteShipment(sv.shipment.jobNumber) },
                                        archivedCard = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = null; showForm = true },
            containerColor = AppColors.accentShipments,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add shipment") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (showForm) {
        ShipmentFormDialog(
            initial = editing,
            customers = customers,
            onDismiss = { showForm = false },
            onSave = { data ->
                vm.saveShipment(data, editing?.jobNumber)
                showForm = false
            }
        )
    }
}

@Composable
private fun ShipmentCard(
    sv: com.avancerifs.cntrack.ShipmentView,
    vm: MainViewModel,
    onOpenContainer: (String) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    archivedCard: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val s = sv.shipment
    val cids = remember(sv.shipment.containers) { vm.parseContainers(s.containers) }
    val rows = remember(sv, vm.tracking, vm.shipmentsTick) {
        cids.map { cid -> vm.containerRow(cid) }
    }

    Surface(
        color = if (archivedCard) AppColors.surface2 else AppColors.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 5.dp, height = 34.dp)
                        .background(AppColors.accentShipments, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.jobNumber, color = AppColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (Db.isPendingJob(s.jobNumber, s.mbl)) {
                            Spacer(Modifier.width(6.dp))
                            Pill("TBA", AppColors.warningInk, AppColors.warningBg)
                        }
                    }
                    Text(
                        listOf(s.customer, s.portOfDischarge).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "—" },
                        color = AppColors.inkMuted, fontSize = 12.sp
                    )
                }
                Pill("${sv.done}/${sv.total}", AppColors.accentShipments, AppColors.infoBg)
            }

            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetaLine("ETA", fmtEta(s.etaDate))
                val lfd = rows.firstNotNullOfOrNull { it.lfd.takeIf { d -> d.isNotBlank() && d != "—" } }
                if (lfd != null) MetaLine("LFD", lfd)
                MetaLine("MBL", s.mbl)
                MetaLine("HBL", s.hbl)
                MetaLine("PO", s.poNumber)
                MetaLine("Containers", cids.joinToString(", ").ifBlank { "—" })
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "Hide containers" else "View containers", fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onArchive) {
                    Icon(
                        if (archivedCard) Icons.Filled.Unarchive else Icons.Filled.Inventory2,
                        contentDescription = if (archivedCard) "Restore" else "Archive",
                        tint = AppColors.inkMuted
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = AppColors.inkMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AppColors.dangerFill)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { r ->
                        Surface(
                            color = AppColors.surface2,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenContainer(r.cid) }
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.cid, color = AppColors.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${r.location} • ${r.eventTime}",
                                        color = AppColors.inkMuted, fontSize = 11.sp
                                    )
                                }
                                StatusPill(r.status)
                                Spacer(Modifier.width(6.dp))
                                com.avancerifs.cntrack.ui.components.VerdictPill(r.verdict)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row {
        Text("$label  ", color = AppColors.inkMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value.ifBlank { "—" }, color = AppColors.ink, fontSize = 12.sp)
    }
}

@Composable
private fun ShipmentFormDialog(
    initial: Shipment?,
    customers: List<String>,
    onDismiss: () -> Unit,
    onSave: (Shipment) -> Unit
) {
    var job by remember { mutableStateOf(initial?.jobNumber ?: "") }
    var mbl by remember { mutableStateOf(initial?.mbl ?: "") }
    var hbl by remember { mutableStateOf(initial?.hbl ?: "") }
    var po by remember { mutableStateOf(initial?.poNumber ?: "") }
    var pod by remember { mutableStateOf(initial?.portOfDischarge ?: "") }
    var customer by remember { mutableStateOf(initial?.customer ?: "") }
    var containers by remember { mutableStateOf(initial?.containers ?: "") }
    var customerMenu by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add shipment" else "Edit shipment", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormField("Job number *", job, { job = it.uppercase() })
                FormField("Master B/L", mbl, { mbl = it.uppercase() })
                FormField("House B/L", hbl, { hbl = it.uppercase() })
                FormField("PO number", po, { po = it })
                FormField("Port of discharge", pod, { pod = it.uppercase() })
                Box {
                    Surface(
                        color = AppColors.surface2,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customerMenu = true }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("CUSTOMER", color = AppColors.inkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(customer.ifBlank { "Select or type below" }, color = AppColors.ink, fontSize = 14.sp)
                        }
                    }
                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { customer = c; customerMenu = false })
                        }
                    }
                }
                FormField("Customer (free text)", customer, { customer = it.uppercase() })
                OutlinedTextField(
                    value = containers,
                    onValueChange = { containers = it.uppercase() },
                    label = { Text("Containers (comma / space separated)") },
                    minLines = 3,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (err.isNotEmpty()) Text(err, color = AppColors.dangerInk, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (job.isBlank()) { err = "Job number is required."; return@Button }
                onSave(
                    Shipment(
                        jobNumber = job.trim().uppercase(),
                        mbl = mbl.trim(), hbl = hbl.trim(), poNumber = po.trim(),
                        portOfDischarge = pod.trim(), customer = customer.trim(),
                        containers = containers.trim()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AppColors.surface,
    unfocusedContainerColor = AppColors.surface,
    focusedIndicatorColor = AppColors.primary,
    unfocusedIndicatorColor = AppColors.line,
    focusedTextColor = AppColors.ink,
    unfocusedTextColor = AppColors.ink,
    cursorColor = AppColors.primary
)
