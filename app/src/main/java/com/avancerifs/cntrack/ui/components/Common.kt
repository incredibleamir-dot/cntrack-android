package com.avancerifs.cntrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avancerifs.cntrack.ui.theme.AppColors

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusPill(status: String) {
    val (fg, bg) = when {
        status.equals("Closed", true) -> AppColors.neutralInk to AppColors.neutralBg
        status.equals("Planned", true) -> AppColors.infoInk to AppColors.infoBg
        status.equals("no data yet", true) -> AppColors.neutralInk to AppColors.neutralBg
        else -> AppColors.successInk to AppColors.successBg
    }
    Pill(status, fg, bg)
}

@Composable
fun VerdictPill(verdict: String) {
    val (fg, bg) = when (verdict) {
        "Early" -> AppColors.successInk to AppColors.successBg
        "On Time" -> AppColors.infoInk to AppColors.infoBg
        "Delayed" -> AppColors.dangerInk to AppColors.dangerBg
        "Planned" -> AppColors.infoInk to AppColors.infoBg
        else -> AppColors.neutralInk to AppColors.neutralBg
    }
    Pill(verdict, fg, bg)
}

@Composable
fun CustomsPill(state: String) {
    val (label, fg, bg) = when (state) {
        "cleared" -> Triple("cleared", AppColors.successInk, AppColors.successBg)
        "hold" -> Triple("HOLD", AppColors.dangerInk, AppColors.dangerBg)
        "not_released" -> Triple("Not Released", AppColors.dangerInk, AppColors.dangerBg)
        "unknown" -> Triple("customs", AppColors.warningInk, AppColors.warningBg)
        else -> Triple("—", AppColors.neutralInk, AppColors.neutralBg)
    }
    Pill(label, fg, bg)
}

@Composable
fun KpiCard(label: String, value: String, accent: Color = AppColors.primary, modifier: Modifier = Modifier) {
    Surface(
        color = AppColors.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(width = 26.dp, height = 4.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label.uppercase(),
                color = AppColors.inkMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(value, color = AppColors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
fun DetailKpi(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = AppColors.ink) {
    Column(modifier) {
        Text(
            label.uppercase(),
            color = AppColors.inkMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionCard(
    title: String,
    accent: Color = AppColors.primary,
    content: @Composable () -> Unit
) {
    Surface(
        color = AppColors.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Box(
                    Modifier
                        .size(width = 5.dp, height = 18.dp)
                        .background(accent, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(title, color = AppColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search…",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = AppColors.inkMuted, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.inkMuted) },
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppColors.surface,
            unfocusedContainerColor = AppColors.surface,
            focusedIndicatorColor = AppColors.primary,
            unfocusedIndicatorColor = AppColors.line,
            focusedTextColor = AppColors.ink,
            unfocusedTextColor = AppColors.ink,
            cursorColor = AppColors.primary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = AppColors.surface2,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text,
            color = AppColors.inkMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun StatChip(label: String, value: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(accent, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text("$label ", color = AppColors.inkMuted, fontSize = 12.sp)
        Text(value, color = AppColors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LfdBadge(days: Int?) {
    if (days == null) return
    val (fg, bg) = when {
        days < 0 -> AppColors.dangerInk to AppColors.dangerBg
        days <= 3 -> AppColors.warningInk to AppColors.warningBg
        else -> AppColors.successInk to AppColors.successBg
    }
    val label = when {
        days < 0 -> "LFD expired ${-days}d ago"
        days == 0 -> "LFD due today"
        else -> "LFD in ${days}d"
    }
    Pill(label, fg, bg)
}
