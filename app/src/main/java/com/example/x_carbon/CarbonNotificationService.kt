package com.example.x_carbon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// ══════════════════════════════════════════════════════════
//  NOTIFICATION CHANNELS
// ══════════════════════════════════════════════════════════

object NotificationChannels {
    const val CHANNEL_ALERT   = "carbon_alert"    // High priority — threshold breach
    const val CHANNEL_INFO    = "carbon_info"     // Low priority — daily summary
    const val CHANNEL_TIPS    = "carbon_tips"     // Low priority — eco tips

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ALERT,
            "Carbon Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description  = "Alerts when your CO₂ crosses a threshold"
            enableLights(true)
            lightColor   = android.graphics.Color.RED
            enableVibration(true)
        })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_INFO,
            "Carbon Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily carbon footprint summaries"
        })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_TIPS,
            "Eco Tips",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tips to reduce your digital carbon footprint"
        })
    }
}

// ══════════════════════════════════════════════════════════
//  THRESHOLD CONFIG
// ══════════════════════════════════════════════════════════

data class NotificationThreshold(
    val id:              String,
    val label:           String,
    val thresholdKg:     Double,    // kg CO₂ — trigger level
    val color:           Color,
    val emoji:           String,
    val enabled:         Boolean    = true
)

// Default thresholds — user can toggle in settings UI below
val DEFAULT_THRESHOLDS = listOf(
    NotificationThreshold("low",      "Low alert",      0.005,  Color(0xFF66BB6A), "🌿"),
    NotificationThreshold("moderate", "Moderate alert", 0.015,  Color(0xFFFFB300), "⚠️"),
    NotificationThreshold("high",     "High alert",     0.030,  Color(0xFFFF7043), "🔥"),
    NotificationThreshold("critical", "Critical alert", 0.060,  Color(0xFFEF5350), "🚨"),
)

// ══════════════════════════════════════════════════════════
//  NOTIFICATION SERVICE
// ══════════════════════════════════════════════════════════

object CarbonNotificationService {

    // Tracks which thresholds have already fired this session
    // so we don't spam the user
    private val firedThresholds = mutableSetOf<String>()

    // Call this every time ESGReport updates
    fun checkAndNotify(
        context:    Context,
        co2Kg:      Double,
        thresholds: List<NotificationThreshold>
    ) {
        thresholds
            .filter { it.enabled }
            .filter { co2Kg >= it.thresholdKg }
            .filter { it.id !in firedThresholds }
            .forEach { threshold ->
                firedThresholds.add(threshold.id)
                fireNotification(context, co2Kg, threshold)
            }

        // Reset fired list when CO₂ drops significantly
        // (e.g. new day / new session)
        if (co2Kg < DEFAULT_THRESHOLDS.first().thresholdKg) {
            firedThresholds.clear()
        }
    }

    fun resetFiredThresholds() {
        firedThresholds.clear()
    }

    private fun fireNotification(
        context:   Context,
        co2Kg:     Double,
        threshold: NotificationThreshold
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val co2Grams    = (co2Kg * 1000).toInt()
        val actionLabel = getActionLabel(threshold.id)
        val bodyText    = buildNotificationBody(co2Grams, threshold)

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${threshold.emoji} X-Carbon: ${threshold.label}")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(
                when (threshold.id) {
                    "critical" -> NotificationCompat.PRIORITY_MAX
                    "high"     -> NotificationCompat.PRIORITY_HIGH
                    else       -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_info_details,
                actionLabel,
                pendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                threshold.id.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — handled gracefully
        }
    }

    private fun buildNotificationBody(co2Grams: Int, threshold: NotificationThreshold): String {
        return when (threshold.id) {
            "low"      -> "You've emitted ${co2Grams}g CO₂ today. You're on track — keep it up!"
            "moderate" -> "You've emitted ${co2Grams}g CO₂ today. Consider switching to Wi-Fi to reduce your footprint."
            "high"     -> "You've emitted ${co2Grams}g CO₂ today — above average. Closing background apps and reducing mobile data would help."
            "critical" -> "You've emitted ${co2Grams}g CO₂ today — critical level. Tap to see your XAI breakdown and recommended actions."
            else       -> "Carbon emission update: ${co2Grams}g CO₂ today."
        }
    }

    private fun getActionLabel(thresholdId: String) = when (thresholdId) {
        "critical", "high" -> "See why →"
        else               -> "View report"
    }
}

// ══════════════════════════════════════════════════════════
//  NOTIFICATION SETTINGS UI CARD
//  Add this card to your Settings screen or Dashboard
// ══════════════════════════════════════════════════════════

@Composable
fun NotificationSettingsCard(
    thresholds:    List<NotificationThreshold>,
    onToggle:      (String, Boolean) -> Unit,
    currentCO2Kg:  Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Header ────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    null,
                    tint     = CarbonGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CO₂ Alert Thresholds",
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        fontSize   = 15.sp
                    )
                    Text(
                        "Notify me when my carbon footprint reaches:",
                        fontSize = 11.sp,
                        color    = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Threshold rows ────────────────────────────
            thresholds.forEach { threshold ->
                ThresholdRow(
                    threshold    = threshold,
                    currentCO2Kg = currentCO2Kg,
                    onToggle     = { enabled -> onToggle(threshold.id, enabled) }
                )
                if (threshold != thresholds.last()) {
                    HorizontalDivider(
                        color    = BorderColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(10.dp))

            Text(
                "ⓘ  Notifications fire once per session per threshold. They reset when you restart the app or your CO₂ drops to zero.",
                fontSize   = 10.sp,
                color      = Color.Gray,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ThresholdRow(
    threshold:    NotificationThreshold,
    currentCO2Kg: Double,
    onToggle:     (Boolean) -> Unit
) {
    val co2Grams        = (threshold.thresholdKg * 1000).toInt()
    val alreadyPast     = currentCO2Kg >= threshold.thresholdKg
    val progressAnim by animateFloatAsState(
        targetValue   = if (alreadyPast) 1f
        else (currentCO2Kg / threshold.thresholdKg).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(600),
        label         = "thresholdProgress"
    )

    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Colour dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(threshold.color, CircleShape)
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    threshold.label,
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "at ${co2Grams}g CO₂  ·  ${if (alreadyPast) "✓ already triggered" else "not yet reached"}",
                    fontSize = 11.sp,
                    color    = if (alreadyPast) threshold.color.copy(0.8f) else Color.Gray
                )
            }

            Switch(
                checked         = threshold.enabled,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = threshold.color,
                    checkedTrackColor   = threshold.color.copy(0.3f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.Gray.copy(0.2f)
                )
            )
        }

        Spacer(Modifier.height(6.dp))

        // Progress bar showing how close you are to this threshold
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.05f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressAnim)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(threshold.color.copy(if (alreadyPast) 1f else 0.6f))
            )
        }
    }
}