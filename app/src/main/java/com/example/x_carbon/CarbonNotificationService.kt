package com.example.x_carbon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// ══════════════════════════════════════════════════════════
//  NOTIFICATION CHANNELS
// ══════════════════════════════════════════════════════════

object NotificationChannels {
    const val CHANNEL_ALERT = "carbon_alert"
    const val CHANNEL_INFO  = "carbon_info"
    const val CHANNEL_TIPS  = "carbon_tips"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "Carbon Alerts",  NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when your CO₂ crosses a threshold"; enableLights(true); lightColor = android.graphics.Color.RED; enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_INFO, "Carbon Updates", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Daily carbon footprint summaries" })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TIPS, "Eco Tips",       NotificationManager.IMPORTANCE_LOW).apply    { description = "Tips to reduce your digital carbon footprint" })
    }
}

// ══════════════════════════════════════════════════════════
//  THRESHOLD CONFIG
// ══════════════════════════════════════════════════════════

data class NotificationThreshold(
    val id:          String,
    val label:       String,
    val thresholdKg: Double,
    val enabled:     Boolean = true
)

val DEFAULT_THRESHOLDS = listOf(
    NotificationThreshold("low",      "Low alert",      0.005),
    NotificationThreshold("moderate", "Moderate alert", 0.015),
    NotificationThreshold("high",     "High alert",     0.030),
    NotificationThreshold("critical", "Critical alert", 0.060),
)

// ══════════════════════════════════════════════════════════
//  NOTIFICATION SERVICE
// ══════════════════════════════════════════════════════════

object CarbonNotificationService {
    private val firedThresholds = mutableSetOf<String>()

    fun checkAndNotify(context: Context, co2Kg: Double, thresholds: List<NotificationThreshold>) {
        thresholds.filter { it.enabled && co2Kg >= it.thresholdKg && it.id !in firedThresholds }
            .forEach { threshold ->
                firedThresholds.add(threshold.id)
                fireNotification(context, co2Kg, threshold)
            }
        if (co2Kg < DEFAULT_THRESHOLDS.first().thresholdKg) firedThresholds.clear()
    }

    fun resetFiredThresholds() = firedThresholds.clear()

    private fun fireNotification(context: Context, co2Kg: Double, threshold: NotificationThreshold) {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi     = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val co2g   = (co2Kg * 1000).toInt()
        val body   = when (threshold.id) {
            "low"      -> "You've emitted ${co2g}g CO₂ today. You're on track — keep it up!"
            "moderate" -> "You've emitted ${co2g}g CO₂ today. Consider switching to Wi-Fi to reduce your footprint."
            "high"     -> "You've emitted ${co2g}g CO₂ today — above average. Closing background apps and reducing mobile data would help."
            "critical" -> "You've emitted ${co2g}g CO₂ today — critical level. Tap to see your XAI breakdown and recommended actions."
            else       -> "Carbon emission update: ${co2g}g CO₂ today."
        }
        val notif = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("X-Carbon: ${threshold.label}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(when (threshold.id) { "critical" -> NotificationCompat.PRIORITY_MAX; "high" -> NotificationCompat.PRIORITY_HIGH; else -> NotificationCompat.PRIORITY_DEFAULT })
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(threshold.id.hashCode(), notif) } catch (e: SecurityException) { }
    }
}