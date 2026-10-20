package com.avancerifs.cntrack.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.avancerifs.cntrack.MainViewModel
import com.avancerifs.cntrack.logic.Diag
import com.avancerifs.cntrack.notify.Notifier
import com.avancerifs.cntrack.notify.NotifyScheduler
import com.avancerifs.cntrack.ui.theme.AppColors
import kotlinx.coroutines.launch

enum class Page(val label: String, val accent: Color) {
    SHIPMENTS("Shipments", AppColors.accentShipments),
    CONTAINERS("Containers", AppColors.accentContainers),
    TRACK("Quick Track", AppColors.accentQuick),
    ALERTS("Alerts", AppColors.accentDetails),
    SETTINGS("Settings", AppColors.accentAbout),
    ABOUT("About", AppColors.accentAbout)
}

sealed interface Dest {
    data class Tab(val page: Page) : Dest
    data class Detail(val cid: String) : Dest
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    vm: MainViewModel,
    initialCid: String? = null,
    onInitialCidConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf<Dest>(Dest.Tab(Page.SHIPMENTS)) }
    val stack = remember { mutableStateListOf<Dest>() }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shipmentsState = rememberLazyListState()
    val containersState = rememberLazyListState()

    fun navigate(dest: Dest) {
        if (dest == current) return
        stack.add(current)
        current = dest
    }

    fun goBack() {
        if (stack.isNotEmpty()) current = stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.isNotEmpty()) { goBack() }

    LaunchedEffect(vm.error) {
        vm.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    LaunchedEffect(initialCid) {
        initialCid?.let {
            navigate(Dest.Detail(it))
            onInitialCidConsumed()
        }
    }

    val items = listOf(
        Triple(Page.SHIPMENTS, Icons.Filled.LocalShipping, "Shipments"),
        Triple(Page.CONTAINERS, Icons.Filled.Inventory2, "Containers"),
        Triple(Page.TRACK, Icons.Filled.QueryStats, "Track"),
        Triple(Page.ALERTS, Icons.Filled.NotificationsActive, "Alerts")
    )

    val cur = current
    val currentTab = (cur as? Dest.Tab)?.page
    val onDetail = cur is Dest.Detail
    val showBottomBar = !onDetail && currentTab != Page.ABOUT && currentTab != Page.SETTINGS
    val accent = when (cur) {
        is Dest.Detail -> AppColors.accentDetails
        is Dest.Tab -> cur.page.accent
    }
    val title = when (cur) {
        is Dest.Detail -> cur.cid
        is Dest.Tab -> cur.page.label
    }
    val subtitle = when {
        cur is Dest.Detail -> vm.containerRow(cur.cid).job.ifBlank { "Container details" }
        cur is Dest.Tab && cur.page == Page.ABOUT -> "Developer: Amir Arshad"
        cur is Dest.Tab && cur.page == Page.SETTINGS -> "Tracking API and sheet sync"
        vm.syncing -> "Syncing shipments from sheet…"
        vm.loading -> "Updating…"
        else -> vm.message.ifBlank { "CNTrack" }
    }

    Scaffold(
        containerColor = AppColors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (stack.isNotEmpty()) {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = accent),
                actions = {
                    IconButton(onClick = { vm.refresh(true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh data") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; vm.refresh(true) }
                        )
                        DropdownMenuItem(
                            text = { Text("Pull shipments from sheet") },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                            onClick = { menuOpen = false; vm.syncFromSheet() }
                        )
                        DropdownMenuItem(
                            text = { Text("Check milestones now") },
                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                NotifyScheduler.runNow(context)
                                scope.launch { snackbar.showSnackbar("Checking for new milestones…") }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send test notification") },
                            leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                            onClick = { menuOpen = false; Notifier.test(context) }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; navigate(Dest.Tab(Page.SETTINGS)) }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuOpen = false; navigate(Dest.Tab(Page.ABOUT)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share diagnostics") },
                            leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val f = Diag.file(context)
                                if (f.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context, context.packageName + ".fileprovider", f
                                    )
                                    val i = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "CNTrack diagnostics")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(i, "Share diagnostics"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear diagnostics") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            onClick = { menuOpen = false; Diag.clear(context) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = AppColors.surface) {
                    items.forEach { (p, icon, label) ->
                        NavigationBarItem(
                            selected = currentTab == p,
                            onClick = { navigate(Dest.Tab(p)) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = p.accent,
                                selectedTextColor = p.accent,
                                indicatorColor = AppColors.surface2,
                                unselectedIconColor = AppColors.inkMuted,
                                unselectedTextColor = AppColors.inkMuted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (cur) {
                is Dest.Detail -> DetailScreen(vm, cur.cid, onBack = { goBack() })
                is Dest.Tab -> when (cur.page) {
                    Page.SHIPMENTS -> ShipmentsScreen(vm, shipmentsState) { navigate(Dest.Detail(it)) }
                    Page.CONTAINERS -> ContainersScreen(vm, containersState) { navigate(Dest.Detail(it)) }
                    Page.TRACK -> QuickTrackScreen(vm) { navigate(Dest.Detail(it)) }
                    Page.ALERTS -> AlertsScreen(vm) { navigate(Dest.Detail(it)) }
                    Page.SETTINGS -> SettingsScreen(vm)
                    Page.ABOUT -> AboutScreen()
                }
            }
        }
    }
}
