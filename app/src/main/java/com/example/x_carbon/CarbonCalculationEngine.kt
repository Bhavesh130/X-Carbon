package com.example.x_carbon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.util.*

// ──────────────────────────────────────────────────────────
// Constants & Enums
// ──────────────────────────────────────────────────────────

object NetworkEnergyConstants {
    const val WIFI_5GHZ_KWH_PER_GB   = 0.015
    const val WIFI_2_4GHZ_KWH_PER_GB = 0.020
    const val WIFI_GENERIC_KWH_PER_GB = 0.015
    const val LTE_4G_KWH_PER_GB      = 0.100
    const val NR_5G_KWH_PER_GB       = 0.100
    const val HSPA_3G_KWH_PER_GB     = 0.300
    const val EDGE_2G_KWH_PER_GB     = 0.900
    const val DATACENTER_KWH_PER_GB  = 0.070

    fun forNetworkType(type: DeviceNetworkType): Double = when (type) {
        DeviceNetworkType.WIFI_5GHZ   -> WIFI_5GHZ_KWH_PER_GB
        DeviceNetworkType.WIFI_2_4GHZ -> WIFI_2_4GHZ_KWH_PER_GB
        DeviceNetworkType.WIFI        -> WIFI_GENERIC_KWH_PER_GB
        DeviceNetworkType.CELLULAR_5G -> NR_5G_KWH_PER_GB
        DeviceNetworkType.CELLULAR_4G -> LTE_4G_KWH_PER_GB
        DeviceNetworkType.CELLULAR_3G -> HSPA_3G_KWH_PER_GB
        DeviceNetworkType.CELLULAR_2G -> EDGE_2G_KWH_PER_GB
        DeviceNetworkType.NONE        -> WIFI_GENERIC_KWH_PER_GB
    }
}

object DevicePowerConstants {
    const val CPU_IDLE_WATTS       = 0.10
    const val CPU_ACTIVE_WATTS     = 0.35
    const val DISPLAY_OLED_MIN_W   = 0.10
    const val DISPLAY_OLED_MAX_W   = 1.20
    const val DISPLAY_LCD_MIN_W    = 0.40
    const val DISPLAY_LCD_MAX_W    = 2.20
    const val MODEM_WIFI_IDLE_W    = 0.05
    const val MODEM_WIFI_ACTIVE_W  = 0.15
    const val MODEM_LTE_ACTIVE_W   = 0.50
    const val MODEM_5G_ACTIVE_W    = 0.80
    const val THERMAL_MULTIPLIER   = 0.20
}

enum class DeviceNetworkType(val label: String) {
    WIFI_5GHZ   ("Wi-Fi 5GHz"),
    WIFI_2_4GHZ ("Wi-Fi 2.4GHz"),
    WIFI        ("Wi-Fi"),
    CELLULAR_5G ("5G"),
    CELLULAR_4G ("4G LTE"),
    CELLULAR_3G ("3G"),
    CELLULAR_2G ("2G"),
    NONE        ("No connection")
}

enum class PowerMethod { BATTERY_MEASURED, MODEL_ESTIMATED }
enum class UserActivity { STATIONARY, WALKING, RUNNING, IN_VEHICLE }

// ──────────────────────────────────────────────────────────
// Data Classes
// ──────────────────────────────────────────────────────────

data class AppCarbon(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,
    val estimatedCO2Kg: Double,
    val percentage: Double
)

data class DeviceReadings(
    val networkType: DeviceNetworkType,
    val downlinkMbps: Double,
    val wifiMb: Double,
    val mobileMb: Double,
    val signalStrength: Int,
    val batteryPct: Int,
    val batteryDrainPct: Double,
    val batteryCapacityMah: Int,
    val isCharging: Boolean,
    val batteryVoltageV: Double,
    val screenBrightness: Int,
    val isOLED: Boolean,
    val thermalState: Int,
    val cpuCores: Int,
    val sessionHours: Double,
    val gridIntensityKgPerKwh: Double,
    val gridRegionName: String,
    val batteryCycleCount: Int,
    val userActivity: UserActivity,
    val isDarkMode: Boolean,
    val behaviorFactor: Double,
    val locationAvailable: Boolean
)

data class CarbonCalculationResult(
    val networkCO2: Double,
    val displayCO2: Double,
    val cpuCO2: Double,
    val modemCO2: Double,
    val dataCentreCO2: Double,
    val totalUsePhaseCO2: Double,
    val networkEnergyKwh: Double,
    val displayEnergyKwh: Double,
    val cpuEnergyKwh: Double,
    val modemEnergyKwh: Double,
    val totalEnergyKwh: Double,
    val estimatedDisplayW: Double,
    val estimatedCpuW: Double,
    val estimatedModemW: Double,
    val measuredBatteryW: Double?,
    val amortisedEmbodiedKg: Double,
    val degradationPremiumKg: Double,
    val totalWithEmbodiedAndDegradation: Double,
    val readings: DeviceReadings,
    val powerMethod: PowerMethod,
    val networkEnergyConstant: Double,
    val predictedLifetimeYears: Double,
    val perAppBreakdown: List<AppCarbon>
)

// ──────────────────────────────────────────────────────────
// Embodied Carbon & Lifetime Prediction
// ──────────────────────────────────────────────────────────

private object EmbodiedCarbonDatabase {
    private val COMPONENTS = listOf(
        "Processor (SoC)" to 22.0,
        "RAM (8GB)" to 5.5,
        "Storage (128GB UFS)" to 4.2,
        "Display + Touch" to 8.5,
        "Camera sensors" to 6.0,
        "PCB + Passive components" to 3.8,
        "Battery" to 4.5,
        "Wi-Fi/BT/Modem ICs" to 3.2,
        "Power management ICs" to 2.5,
        "Chassis + Assembly" to 9.8
    )
    fun getTotalEmbodiedKg(): Double = COMPONENTS.sumOf { it.second }
}

private fun predictRemainingLifetimeYears(
    batteryCycleCount: Int,
    avgThermalState: Double,
    deviceAgeYears: Double
): Double {
    var remaining = 3.0 - deviceAgeYears
    remaining -= (batteryCycleCount / 300) * 0.5
    if (avgThermalState > 2) remaining -= (avgThermalState - 2) * 0.3
    return remaining.coerceIn(0.5, 5.0)
}

private fun calculateAmortisedEmbodiedKg(
    sessionHours: Double,
    deviceAgeYears: Double,
    batteryCycleCount: Int,
    avgThermalState: Double
): Pair<Double, Double> {
    val totalEmbodied = EmbodiedCarbonDatabase.getTotalEmbodiedKg()
    val predictedLifetime = predictRemainingLifetimeYears(batteryCycleCount, avgThermalState, deviceAgeYears)
    val annualAmortised = totalEmbodied / predictedLifetime
    val perSession = annualAmortised / (365.0 * 24.0) * sessionHours
    return perSession to predictedLifetime
}

// ──────────────────────────────────────────────────────────
// Battery Degradation Model
// ──────────────────────────────────────────────────────────

private fun chargingEfficiency(cycleCount: Int): Double {
    val eta0 = 0.94
    val alpha = 0.06
    return eta0 * (1 - alpha * sqrt(cycleCount.toDouble()))
}

private fun degradationPremiumKg(
    deviceEnergyWh: Double,
    cycleCount: Int,
    gridIntensity: Double
): Double {
    val eta = chargingEfficiency(cycleCount)
    val extraWh = deviceEnergyWh * ((1 / eta) - 1)
    return extraWh / 1000.0 * gridIntensity
}

// ──────────────────────────────────────────────────────────
// CPU Thermal from Frequency (direct reading)
// ──────────────────────────────────────────────────────────

private fun getCpuFrequencyThermal(): Int {
    return try {
        var maxCurrentKhz = 0L
        var maxPossibleKhz = 1L
        for (core in 0..7) {
            val curFile = java.io.File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
            val maxFile = java.io.File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
            if (!curFile.exists()) continue
            val cur = curFile.readText().trim().toLongOrNull() ?: continue
            val max = maxFile.readText().trim().toLongOrNull() ?: continue
            if (cur > maxCurrentKhz) {
                maxCurrentKhz = cur
                maxPossibleKhz = max
            }
        }
        if (maxCurrentKhz == 0L) return 0
        val ratio = maxCurrentKhz.toDouble() / maxPossibleKhz.toDouble()
        when {
            ratio < 0.25 -> 0
            ratio < 0.45 -> 1
            ratio < 0.65 -> 2
            ratio < 0.85 -> 3
            else -> 4
        }
    } catch (e: Exception) { 0 }
}

// ──────────────────────────────────────────────────────────
// User Context & Behavior Factor
// ──────────────────────────────────────────────────────────

private suspend fun getUserBehaviorFactor(context: Context): Pair<UserActivity, Double> {
    val isDarkMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Settings.Secure.getInt(context.contentResolver, "ui_night_mode", 1) == 2
    } else false

    var speed = 0f
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        try {
            val location = Tasks.await(client.lastLocation)
            speed = location?.speed ?: 0f
        } catch (e: Exception) { }
    }

    val activity = when {
        speed < 0.5f -> UserActivity.STATIONARY
        speed < 2.5f -> UserActivity.WALKING
        speed < 5.0f -> UserActivity.RUNNING
        else -> UserActivity.IN_VEHICLE
    }
    var factor = 1.0
    if (isDarkMode) factor *= 0.9
    if (activity == UserActivity.IN_VEHICLE) factor *= 1.1
    return activity to factor
}

// ──────────────────────────────────────────────────────────
// Device Reader (all telemetry)
// ──────────────────────────────────────────────────────────

object DeviceReader {
    suspend fun read(
        context: Context,
        wifiMb: Double,
        mobileMb: Double,
        sessionHours: Double,
        gridIntensity: Double,
        gridRegion: String,
        prevBatteryPct: Int? = null,
        deviceAgeYears: Double = 1.0
    ): DeviceReadings = withContext(Dispatchers.IO) {
        val networkType = detectNetworkType(context)
        val downlink = getDownlink(context)
        val signal = getSignalStrength(context)
        val batteryPct = getBatteryPct(context)
        val batteryDrain = if (prevBatteryPct != null) max(0.0, (prevBatteryPct - batteryPct).toDouble()) else 0.0
        val batteryCapacity = getBatteryCapacity(context)
        val isCharging = isCharging(context)
        val voltage = getBatteryVoltage(context)
        val brightness = getScreenBrightness(context)
        val isOLED = detectOLED(context)
        val thermal = getCpuFrequencyThermal()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cycleCount = getBatteryCycleCount(context)
        val (activity, behaviorFactor) = getUserBehaviorFactor(context)
        val isDarkMode = behaviorFactor < 1.0

        DeviceReadings(
            networkType = networkType,
            downlinkMbps = downlink,
            wifiMb = wifiMb,
            mobileMb = mobileMb,
            signalStrength = signal,
            batteryPct = batteryPct,
            batteryDrainPct = batteryDrain,
            batteryCapacityMah = batteryCapacity,
            isCharging = isCharging,
            batteryVoltageV = voltage,
            screenBrightness = brightness,
            isOLED = isOLED,
            thermalState = thermal,
            cpuCores = cpuCores,
            sessionHours = sessionHours,
            gridIntensityKgPerKwh = gridIntensity,
            gridRegionName = gridRegion,
            batteryCycleCount = cycleCount,
            userActivity = activity,
            isDarkMode = isDarkMode,
            behaviorFactor = behaviorFactor,
            locationAvailable = false
        )
    }

    // -----------------------------------------------------------------
    // Private helpers – real implementations
    // -----------------------------------------------------------------
    private fun detectNetworkType(context: Context): DeviceNetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED)
            return DeviceNetworkType.WIFI
        val network = cm.activeNetwork ?: return DeviceNetworkType.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return DeviceNetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> DeviceNetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> detectCellularGeneration(context)
            else -> DeviceNetworkType.NONE
        }
    }

    private fun detectCellularGeneration(context: Context): DeviceNetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED)
            return DeviceNetworkType.CELLULAR_4G
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return DeviceNetworkType.CELLULAR_4G
        val downKbps = caps.linkDownstreamBandwidthKbps
        return when {
            downKbps > 100_000 -> DeviceNetworkType.CELLULAR_5G
            downKbps > 10_000 -> DeviceNetworkType.CELLULAR_4G
            downKbps > 1_000 -> DeviceNetworkType.CELLULAR_3G
            else -> DeviceNetworkType.CELLULAR_2G
        }
    }

    private fun getDownlink(context: Context): Double {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) return 0.0
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return 0.0
        return caps.linkDownstreamBandwidthKbps / 1000.0
    }

    private fun getSignalStrength(context: Context): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) return -80
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return -80
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.signalStrength.takeIf { it != Int.MIN_VALUE } ?: -80
        } else -80
    }

    private fun getBatteryPct(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getBatteryCapacity(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargeUah = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level > 0 && chargeUah > 0) ((chargeUah / level.toDouble()) / 1000.0).roundToInt()
            else 4000
        } catch (e: Exception) { 4000 }
    }

    private fun getBatteryVoltage(context: Context): Double {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3700) ?: 3700
        return voltageMv / 1000.0
    }

    private fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun getScreenBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (e: Exception) { 128 }
    }

    private fun detectOLED(context: Context): Boolean {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        } else false
    }

    private fun getBatteryCycleCount(context: Context): Int {
        val prefs = context.getSharedPreferences("battery_health", Context.MODE_PRIVATE)
        var cycles = prefs.getInt("cycle_count", 0)
        val lastPct = prefs.getInt("last_pct", 100)
        val currentPct = getBatteryPct(context)
        val discharged = (lastPct - currentPct).coerceAtLeast(0)
        if (discharged > 0 && !isCharging(context)) {
            val totalDischarge = prefs.getFloat("total_discharge", 0f) + discharged
            val newCycles = (totalDischarge / 100f).toInt()
            if (newCycles > cycles) {
                cycles = newCycles
                prefs.edit().putInt("cycle_count", cycles).putFloat("total_discharge", totalDischarge % 100).apply()
            } else {
                prefs.edit().putFloat("total_discharge", totalDischarge).apply()
            }
        }
        prefs.edit().putInt("last_pct", currentPct).apply()
        return cycles
    }
}

// ──────────────────────────────────────────────────────────
// Per‑App Carbon Attribution (simplified)
// ──────────────────────────────────────────────────────────

private suspend fun getPerAppCarbon(
    context: Context,
    sessionStartMs: Long,
    totalUsePhaseCO2: Double
): List<AppCarbon> = withContext(Dispatchers.IO) {
    try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val packageManager = context.packageManager
        val usageStats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            sessionStartMs,
            System.currentTimeMillis()
        )
        val appTimes = usageStats.filter { it.totalTimeInForeground > 1000 }
            .associate { it.packageName to it.totalTimeInForeground }
        val totalTime = appTimes.values.sum()
        if (totalTime == 0L) return@withContext emptyList()

        appTimes.mapNotNull { (pkg, timeMs) ->
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            val fraction = timeMs.toDouble() / totalTime
            val co2 = totalUsePhaseCO2 * fraction
            AppCarbon(
                packageName = pkg,
                appName = appName,
                foregroundTimeMs = timeMs,
                estimatedCO2Kg = co2,
                percentage = fraction * 100
            )
        }.sortedByDescending { it.estimatedCO2Kg }.take(5)
    } catch (e: Exception) { emptyList() }
}

// ──────────────────────────────────────────────────────────
// Carbon Calculation Engine (MAIN)
// ──────────────────────────────────────────────────────────

object CarbonCalculationEngine {
    suspend fun calculate(
        context: Context,
        wifiMb: Double,
        mobileMb: Double,
        sessionHours: Double,
        gridIntensity: Double,
        gridRegion: String,
        prevBatteryPct: Int? = null,
        deviceAgeYears: Double = 1.0,
        sessionStartMs: Long = System.currentTimeMillis() - (sessionHours * 3600 * 1000).toLong()
    ): CarbonCalculationResult {
        val readings = DeviceReader.read(context, wifiMb, mobileMb, sessionHours, gridIntensity, gridRegion, prevBatteryPct, deviceAgeYears)

        val grid = readings.gridIntensityKgPerKwh
        val totalDataGb = (readings.wifiMb + readings.mobileMb) / 1024.0
        val networkConstant = NetworkEnergyConstants.forNetworkType(readings.networkType)

        val networkEnergyKwh = totalDataGb * networkConstant
        val dataCentreEnergyKwh = totalDataGb * NetworkEnergyConstants.DATACENTER_KWH_PER_GB
        val networkCO2 = networkEnergyKwh * grid
        val dataCentreCO2 = dataCentreEnergyKwh * grid

        val brightnessRatio = readings.screenBrightness / 255.0
        val displayPowerW = if (readings.isOLED) {
            DevicePowerConstants.DISPLAY_OLED_MIN_W +
                    (DevicePowerConstants.DISPLAY_OLED_MAX_W - DevicePowerConstants.DISPLAY_OLED_MIN_W) * brightnessRatio
        } else {
            DevicePowerConstants.DISPLAY_LCD_MIN_W +
                    (DevicePowerConstants.DISPLAY_LCD_MAX_W - DevicePowerConstants.DISPLAY_LCD_MIN_W) * brightnessRatio
        }
        val displayEnergyKwh = displayPowerW * readings.sessionHours / 1000.0
        val displayCO2 = displayEnergyKwh * grid

        val signalFactor = when {
            readings.signalStrength > -70 -> 0.8
            readings.signalStrength > -85 -> 1.0
            else -> 1.3
        }
        val modemPowerW = when (readings.networkType) {
            DeviceNetworkType.WIFI, DeviceNetworkType.WIFI_5GHZ, DeviceNetworkType.WIFI_2_4GHZ ->
                DevicePowerConstants.MODEM_WIFI_ACTIVE_W * signalFactor
            DeviceNetworkType.CELLULAR_5G -> DevicePowerConstants.MODEM_5G_ACTIVE_W * signalFactor
            DeviceNetworkType.CELLULAR_4G -> DevicePowerConstants.MODEM_LTE_ACTIVE_W * signalFactor
            DeviceNetworkType.CELLULAR_3G -> DevicePowerConstants.MODEM_LTE_ACTIVE_W * signalFactor * 1.2
            DeviceNetworkType.CELLULAR_2G -> DevicePowerConstants.MODEM_LTE_ACTIVE_W * signalFactor * 1.5
            DeviceNetworkType.NONE -> DevicePowerConstants.MODEM_WIFI_IDLE_W
        }
        val modemEnergyKwh = modemPowerW * readings.sessionHours / 1000.0
        val modemCO2 = modemEnergyKwh * grid

        val cpuPowerW = DevicePowerConstants.CPU_IDLE_WATTS +
                (DevicePowerConstants.CPU_ACTIVE_WATTS * readings.thermalState * DevicePowerConstants.THERMAL_MULTIPLIER)
        val cpuEnergyKwh = cpuPowerW * readings.sessionHours / 1000.0
        val cpuCO2 = cpuEnergyKwh * grid

        val measuredBatteryW = if (readings.batteryDrainPct > 0 && readings.sessionHours > 0 && !readings.isCharging) {
            val capacityWh = readings.batteryCapacityMah * readings.batteryVoltageV / 1000.0
            val energyUsedWh = capacityWh * (readings.batteryDrainPct / 100.0)
            energyUsedWh / readings.sessionHours
        } else null

        val modelDeviceEnergyKwh = displayEnergyKwh + cpuEnergyKwh + modemEnergyKwh
        val modelTotalEnergyKwh = networkEnergyKwh + dataCentreEnergyKwh + modelDeviceEnergyKwh
        val modelTotalCO2 = networkCO2 + dataCentreCO2 + displayCO2 + cpuCO2 + modemCO2

        val (totalEnergyKwh, totalUsePhaseCO2, powerMethod) = if (measuredBatteryW != null && measuredBatteryW in 0.05..10.0) {
            val measuredDeviceEnergyKwh = measuredBatteryW * readings.sessionHours / 1000.0
            val totalEnergy = networkEnergyKwh + dataCentreEnergyKwh + measuredDeviceEnergyKwh
            val totalCO2 = totalEnergy * grid
            Triple(totalEnergy, totalCO2, PowerMethod.BATTERY_MEASURED)
        } else {
            Triple(modelTotalEnergyKwh, modelTotalCO2, PowerMethod.MODEL_ESTIMATED)
        }

        val (amortisedKg, predictedLifetime) = calculateAmortisedEmbodiedKg(
            readings.sessionHours,
            deviceAgeYears,
            readings.batteryCycleCount,
            readings.thermalState.toDouble()
        )

        val deviceEnergyWh = modelDeviceEnergyKwh * 1000
        val degradationKg = degradationPremiumKg(deviceEnergyWh, readings.batteryCycleCount, grid)

        val totalWithEmbodiedAndDegradation = (totalUsePhaseCO2 + amortisedKg + degradationKg) * readings.behaviorFactor

        val perApp = getPerAppCarbon(context, sessionStartMs, totalUsePhaseCO2)

        return CarbonCalculationResult(
            networkCO2 = networkCO2,
            displayCO2 = displayCO2,
            cpuCO2 = cpuCO2,
            modemCO2 = modemCO2,
            dataCentreCO2 = dataCentreCO2,
            totalUsePhaseCO2 = totalUsePhaseCO2,
            networkEnergyKwh = networkEnergyKwh,
            displayEnergyKwh = displayEnergyKwh,
            cpuEnergyKwh = cpuEnergyKwh,
            modemEnergyKwh = modemEnergyKwh,
            totalEnergyKwh = totalEnergyKwh,
            estimatedDisplayW = displayPowerW,
            estimatedCpuW = cpuPowerW,
            estimatedModemW = modemPowerW,
            measuredBatteryW = measuredBatteryW,
            amortisedEmbodiedKg = amortisedKg,
            degradationPremiumKg = degradationKg,
            totalWithEmbodiedAndDegradation = totalWithEmbodiedAndDegradation,
            readings = readings,
            powerMethod = powerMethod,
            networkEnergyConstant = networkConstant,
            predictedLifetimeYears = predictedLifetime,
            perAppBreakdown = perApp
        )
    }
}

// ──────────────────────────────────────────────────────────
// Conversion to ESGReport for UI (displays ALL telemetry)
// ──────────────────────────────────────────────────────────

fun CarbonCalculationResult.toESGReport(): ESGReport {
    val r = readings
    val locale = Locale.US

    val telemetry = mutableListOf<TelemetryMetric>()

    // 1. Network details
    telemetry.add(TelemetryMetric(
        label = "Network",
        value = "${(r.wifiMb + r.mobileMb).roundToInt()} MB",
        subtext = "${r.networkType.label} · ${String.format(locale, "%.1f", networkEnergyConstant * 1000)} Wh/GB",
        icon = Icons.Default.CloudSync,
        color = Color(0xFF4FC3F7)
    ))

    // 2. Display
    telemetry.add(TelemetryMetric(
        label = "Display",
        value = "${String.format(locale, "%.2f", estimatedDisplayW)}W",
        subtext = "Brightness ${(r.screenBrightness / 255.0 * 100).roundToInt()}% · ${if (r.isOLED) "OLED" else "LCD"}",
        icon = Icons.Default.Brightness6,
        color = Color(0xFF81C784)
    ))

    // 3. Power source (measured vs model)
    telemetry.add(TelemetryMetric(
        label = "Power source",
        value = if (powerMethod == PowerMethod.BATTERY_MEASURED)
            "${String.format(locale, "%.2f", measuredBatteryW)}W"
        else "est. ${String.format(locale, "%.2f", estimatedCpuW + estimatedDisplayW + estimatedModemW)}W",
        subtext = if (powerMethod == PowerMethod.BATTERY_MEASURED) "Measured from battery drain" else "Model estimated",
        icon = Icons.Default.BatteryChargingFull,
        color = Color(0xFFE57373)
    ))

    // 4. CPU Thermal state
    telemetry.add(TelemetryMetric(
        label = "CPU Thermal",
        value = "Level ${r.thermalState}",
        subtext = when (r.thermalState) {
            0 -> "Idle"
            1 -> "Light load"
            2 -> "Moderate"
            3 -> "Heavy"
            else -> "Critical"
        },
        icon = Icons.Default.Memory,
        color = Color(0xFFFF7043)
    ))

    // 5. Signal strength & downlink
    telemetry.add(TelemetryMetric(
        label = "Signal",
        value = "${r.signalStrength} dBm",
        subtext = "Downlink: ${String.format(locale, "%.1f", r.downlinkMbps)} Mbps",
        icon = Icons.Default.SignalCellularAlt,
        color = Color(0xFF4FC3F7)
    ))

    // 6. Embodied carbon + lifetime
    telemetry.add(TelemetryMetric(
        label = "Embodied + Lifetime",
        value = "${String.format(locale, "%.1f", predictedLifetimeYears)} yrs left",
        subtext = "Amortised: ${String.format(locale, "%.5f", amortisedEmbodiedKg)} kg CO₂ this session",
        icon = Icons.Default.Factory,
        color = Color(0xFFFFB300)
    ))

    // 7. Battery health
    telemetry.add(TelemetryMetric(
        label = "Battery health",
        value = "${r.batteryCycleCount} cycles",
        subtext = "Degradation premium: ${String.format(locale, "%.5f", degradationPremiumKg)} kg CO₂",
        icon = Icons.Default.BatteryAlert,
        color = Color(0xFFEF5350)
    ))

    // 8. User context (activity + dark mode)
    telemetry.add(TelemetryMetric(
        label = "User context",
        value = r.userActivity.name,
        subtext = if (r.isDarkMode) "Dark mode ON · factor ${r.behaviorFactor}" else "Light mode · factor ${r.behaviorFactor}",
        icon = Icons.Default.Person,
        color = Color(0xFF9C27B0)
    ))

    // 9. Grid intensity (already shown in separate card, but include for completeness)
    telemetry.add(TelemetryMetric(
        label = "Grid intensity",
        value = "${(r.gridIntensityKgPerKwh * 1000).roundToInt()} g/kWh",
        subtext = "${r.gridRegionName} · ${if (r.locationAvailable) "live" else "fallback"}",
        icon = Icons.Default.Bolt,
        color = Color(0xFFFFD54F)
    ))

    // 10. Top app (if any)
    if (perAppBreakdown.isNotEmpty()) {
        val top = perAppBreakdown.first()
        telemetry.add(TelemetryMetric(
            label = "Top app",
            value = top.appName,
            subtext = "${String.format(locale, "%.2f", top.estimatedCO2Kg)} kg · ${String.format(locale, "%.1f", top.percentage)}% of use‑phase CO₂",
            icon = Icons.Default.Apps,
            color = Color(0xFF7E57C2)
        ))
    }

    return ESGReport(
        carbonFootprint = totalWithEmbodiedAndDegradation,
        gridPurity = ((1 - r.gridIntensityKgPerKwh / 0.9) * 100).toInt().coerceIn(0, 100),
        networkEfficiency = if (totalUsePhaseCO2 > 0) (r.wifiMb + r.mobileMb) / totalUsePhaseCO2 else 0.0,
        hardwareStress = r.thermalState.toDouble(),
        treesOffset = totalWithEmbodiedAndDegradation / 21.0,
        telemetryData = telemetry
    )
}