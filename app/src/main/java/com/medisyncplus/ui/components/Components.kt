package com.medisyncplus.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medisyncplus.data.models.ChecklistTaskEntity
import com.medisyncplus.ui.theme.*

// ── Risk Badge ────────────────────────────────────────────────────────────────
@Composable
fun RiskBadge(risk: String, modifier: Modifier = Modifier) {
    val (bg, fg, text) = when (risk.uppercase()) {
        "CRITICAL" -> Triple(RedLight.copy(alpha = 0.2f), Red500, "⚠ Critical")
        "WARNING"  -> Triple(AmberLight.copy(alpha = 0.2f), Amber500, "⚠ Warning")
        else       -> Triple(GreenLight.copy(alpha = 0.2f), Green500, "● Stable")
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = CircleShape
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Status Badge ──────────────────────────────────────────────────────────────
@Composable
fun StatusBadge(label: String, color: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = bgColor.copy(alpha = if (isCurrentThemeDark()) 0.3f else 1f), shape = CircleShape) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun isCurrentThemeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5

private fun Color.luminance(): Double {
    return 0.2126 * this.red + 0.7152 * this.green + 0.0722 * this.blue
}

// ── Section Card ──────────────────────────────────────────────────────────────
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Info Row ──────────────────────────────────────────────────────────────────
@Composable
fun InfoRow(icon: String, label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = valueColor)
        }
    }
}

// ── Pill Dot ──────────────────────────────────────────────────────────────────
@Composable
fun PillDot(colorHex: String, size: Int = 14) {
    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Teal500 }
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.3f)).border(1.dp, color, CircleShape))
}

// ── Alert Banner ──────────────────────────────────────────────────────────────
@Composable
fun AlertBanner(message: String, type: AlertType, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val (bg, border, icon) = when (type) {
        AlertType.DANGER  -> Triple(RedLight.copy(alpha = 0.2f), Red500, "🚨")
        AlertType.WARNING -> Triple(AmberLight.copy(alpha = 0.2f), Amber500, "⚠️")
        AlertType.SUCCESS -> Triple(GreenLight.copy(alpha = 0.2f), Green500, "✅")
        AlertType.INFO    -> Triple(BlueLight.copy(alpha = 0.2f), Blue500, "ℹ️")
    }
    Surface(modifier = modifier.fillMaxWidth(), color = bg, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, border.copy(alpha = 0.5f))) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, fontSize = 16.sp)
            Text(message, Modifier.weight(1f), color = if (isCurrentThemeDark()) MaterialTheme.colorScheme.onSurface else border, fontSize = 13.sp, lineHeight = 20.sp)
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = border, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

enum class AlertType { DANGER, WARNING, SUCCESS, INFO }

// ── Vital Card ────────────────────────────────────────────────────────────────
@Composable
fun VitalCard(
    icon: String, 
    label: String, 
    value: String, 
    unit: String, 
    flag: String, 
    time: String, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val flagColor = when (flag) { "critical" -> Red500; "warning" -> Amber500; else -> MaterialTheme.colorScheme.primary }
    Card(onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, if (flag != "normal") flagColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(), 
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = flagColor, textAlign = TextAlign.Center)
            Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 14.sp)
            if (time.isNotEmpty()) Text(time, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (flag == "warning") StatusBadge("↑ Monitor", Amber500, AmberLight.copy(alpha = 0.2f))
                if (flag == "critical") StatusBadge("⚠ Critical", Red500, RedLight.copy(alpha = 0.2f))
            }
        }
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────
@Composable
fun MediDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

// ── Agent Status Row ──────────────────────────────────────────────────────────
@Composable
fun AgentStatusRow(agentName: String, status: String, statusColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        Text(agentName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(130.dp))
        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Loading Shimmer ───────────────────────────────────────────────────────────
@Composable
fun AgentThinkingCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Text("🤖 MediSync AI is analysing...", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Primary Button ────────────────────────────────────────────────────────────
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                  enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth().height(54.dp), 
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
    }
}

// ── Outline Button ────────────────────────────────────────────────────────────
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick, 
        modifier = modifier.fillMaxWidth().height(54.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary), 
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
    }
}

// ── Screen Header ─────────────────────────────────────────────────────────────
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onSettingsClick: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surface, 
        shadowElevation = 4.dp, 
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary, 
                    fontWeight = FontWeight.ExtraBold 
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle, 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                action?.invoke()
                if (onSettingsClick != null) {
                    Surface(
                        onClick = onSettingsClick,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Checklist Task Item ───────────────────────────────────────────────────────
@Composable
fun ChecklistTaskItem(task: ChecklistTaskEntity, onToggle: (Boolean) -> Unit) {
    val (bg, fg) = when {
        task.templateId.startsWith("T_MED") -> Pair(TealLight, Teal500)
        task.templateId == "T_WEIGHT" -> Pair(BlueLight, Blue500)
        task.templateId == "T_WALK" -> Pair(PurpleLight, Purple500)
        task.templateId == "T_BP" -> Pair(AmberLight, Amber500)
        else -> Pair(BorderGrey.copy(alpha = 0.5f), SlateGrey)
    }

    val isDark = isCurrentThemeDark()

    Surface(
        onClick = { onToggle(!task.isDone) },
        modifier = Modifier.fillMaxWidth(),
        color = if (task.isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, if (task.isDone) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Identifier Box
            Surface(
                color = if (task.isDone) MaterialTheme.colorScheme.outline.copy(alpha = 0.1f) else bg.copy(alpha = if(isDark) 0.2f else 1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.widthIn(min = 72.dp)
            ) {
                Text(
                    text = task.actionKeyword.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else if(isDark) Color.White else fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    task.description,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    style = androidx.compose.ui.text.TextStyle(textDecoration = if (task.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                )
                Text("${task.timeOfDay} · ${task.scheduledTime}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }

            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (task.isDone) MaterialTheme.colorScheme.primary else Color.Transparent).border(2.dp, if (task.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (task.isDone) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

// ── Medication Status Color ───────────────────────────────────────────────────
fun medStatusColor(status: String) = when (status) {
    "TAKEN"    -> Green500
    "MISSED"   -> Red500
    "UPCOMING" -> Amber500
    else       -> SlateGrey
}

fun medStatusBg(status: String) = when (status) {
    "TAKEN"    -> GreenLight
    "MISSED"   -> RedLight
    "UPCOMING" -> AmberLight
    else       -> Color.Transparent
}
