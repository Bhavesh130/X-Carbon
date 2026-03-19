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
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Locale



// ── Network energy constants (kWh per GB) ─────────────────
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

// ── Device power model constants (Watts) ─────────────────
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

data class DeviceReadings(
    val networkType:      DeviceNetworkType,
    val downlinkMbps:     Double,
    val wifiMb:           Double,
    val mobileMb:         Double,
    val signalStrength:   Int,
    val batteryPct:       Int,
    val batteryDrainPct:  Double,
    val batteryCapacityMah: Int,
    val isCharging:       Boolean,
    val batteryVoltageV:  Double,
    val screenBrightness: Int,
    val isOLED:           Boolean,
    val thermalState:     Int,
    val cpuCores:         Int,
    val sessionHours:     Double,
    val gridIntensityKgPerKwh: Double,
    val gridRegionName:   String
)

data class CarbonCalculationResult(
    val networkCO2:       Double,
    val displayCO2:       Double,
    val cpuCO2:           Double,
    val modemCO2:         Double,
    val dataCentreCO2:    Double,
    val totalCO2:         Double,
    val networkEnergyKwh: Double,
    val displayEnergyKwh: Double,
    val cpuEnergyKwh:     Double,
    val modemEnergyKwh:   Double,
    val totalEnergyKwh:   Double,
    val estimatedDisplayW: Double,
    val estimatedCpuW:     Double,
    val estimatedModemW:   Double,
    val measuredBatteryW:  Double?,
    val readings:          DeviceReadings,
    val powerMethod:       PowerMethod,
    val networkEnergyConstant: Double
)

enum class PowerMethod {
    BATTERY_MEASURED,
    MODEL_ESTIMATED
}

object DeviceReader {

    fun read(
        context:      Context,
        wifiMb:       Double,
        mobileMb:     Double,
        thermalState: Int,
        sessionHours: Double,
        gridIntensity: Double,
        gridRegion:   String,
        prevBatteryPct: Int? = null
    ): DeviceReadings {
        return DeviceReadings(
            networkType         = detectNetworkType(context),
            downlinkMbps        = getDownlink(context),
            wifiMb              = wifiMb,
            mobileMb            = mobileMb,
            signalStrength      = getSignalStrength(context),
            batteryPct          = getBatteryPct(context),
            batteryDrainPct     = if (prevBatteryPct != null)
                max(0.0, (prevBatteryPct - getBatteryPct(context)).toDouble())
            else 0.0,
            batteryCapacityMah  = getBatteryCapacity(context),
            isCharging          = isCharging(context),
            batteryVoltageV     = getBatteryVoltage(context),
            screenBrightness    = getScreenBrightness(context),
            isOLED              = detectOLED(context),
            thermalState        = thermalState,
            cpuCores            = Runtime.getRuntime().availableProcessors(),
            sessionHours        = sessionHours,
            gridIntensityKgPerKwh = gridIntensity,
            gridRegionName      = gridRegion
        )
    }

    fun detectNetworkType(context: Context): DeviceNetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return DeviceNetworkType.WIFI
        }
        val network = cm.activeNetwork ?: return DeviceNetworkType.NONE
        val caps    = cm.getNetworkCapabilities(network) ?: return DeviceNetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> DeviceNetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> detectCellularGeneration(context)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> DeviceNetworkType.WIFI
            else -> DeviceNetworkType.NONE
        }
    }

    private fun detectCellularGeneration(context: Context): DeviceNetworkType {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                return DeviceNetworkType.CELLULAR_4G
            }
            val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return DeviceNetworkType.CELLULAR_4G
            val downKbps = nc.linkDownstreamBandwidthKbps
            when {
                downKbps > 100_000 -> DeviceNetworkType.CELLULAR_5G
                downKbps > 10_000  -> DeviceNetworkType.CELLULAR_4G
                downKbps > 1_000   -> DeviceNetworkType.CELLULAR_3G
                else               -> DeviceNetworkType.CELLULAR_2G
            }
        } catch (e: Exception) { DeviceNetworkType.CELLULAR_4G }
    }

    fun getDownlink(context: Context): Double {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                return 0.0
            }
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return 0.0
            caps.linkDownstreamBandwidthKbps / 1000.0
        } catch (e: Exception) { 0.0 }
    }

    fun getBatteryPct(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getBatteryCapacity(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargeUah     = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val level         = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level > 0 && chargeUah > 0)
                ((chargeUah / level.toDouble()) / 1000.0).roundToInt()
            else 4000
        } catch (e: Exception) { 4000 }
    }

    fun getBatteryVoltage(context: Context): Double {
        return try {
            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3700) ?: 3700
            voltageMv / 1000.0
        } catch (e: Exception) { 3.7 }
    }

    fun isCharging(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.isCharging
        } catch (e: Exception) { false }
    }

    fun getScreenBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
        } catch (e: Exception) { 128 }
    }

    private fun detectOLED(context: Context): Boolean {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
            } else false
        } catch (e: Exception) { false }
    }

    fun getSignalStrength(context: Context): Int {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                return -80
            }
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return -80
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps.signalStrength.takeIf { it != Int.MIN_VALUE } ?: -80
            } else -80
        } catch (e: Exception) { -80 }
    }
}

object CarbonCalculationEngine {

    fun calculate(readings: DeviceReadings): CarbonCalculationResult {
        val grid = readings.gridIntensityKgPerKwh

        val totalDataGb       = (readings.wifiMb + readings.mobileMb) / 1024.0
        val networkConstant   = NetworkEnergyConstants.forNetworkType(readings.networkType)
        val dataCentreConstant= NetworkEnergyConstants.DATACENTER_KWH_PER_GB

        val networkEnergyKwh  = totalDataGb * networkConstant
        val dataCentreEnergyKwh = totalDataGb * dataCentreConstant
        val networkCO2        = networkEnergyKwh  * grid
        val dataCentreCO2     = dataCentreEnergyKwh * grid

        val brightnessRatio  = readings.screenBrightness / 255.0
        val displayPowerW    = if (readings.isOLED) {
            DevicePowerConstants.DISPLAY_OLED_MIN_W +
                    (DevicePowerConstants.DISPLAY_OLED_MAX_W - DevicePowerConstants.DISPLAY_OLED_MIN_W) * brightnessRatio
        } else {
            DevicePowerConstants.DISPLAY_LCD_MIN_W +
                    (DevicePowerConstants.DISPLAY_LCD_MAX_W - DevicePowerConstants.DISPLAY_LCD_MIN_W) * brightnessRatio
        }
        val displayEnergyKwh = displayPowerW * readings.sessionHours / 1000.0
        val displayCO2       = displayEnergyKwh * grid

        val signalFactor = when {
            readings.signalStrength > -70 -> 0.8
            readings.signalStrength > -85 -> 1.0
            else                          -> 1.3
        }
        val modemPowerW = when (readings.networkType) {
            DeviceNetworkType.WIFI,
            DeviceNetworkType.WIFI_5GHZ,
            DeviceNetworkType.WIFI_2_4GHZ -> DevicePowerConstants.MODEM_WIFI_ACTIVE_W * signalFactor
            DeviceNetworkType.CELLULAR_5G -> DevicePowerConstants.MODEM_5G_ACTIVE_W   * signalFactor
            DeviceNetworkType.CELLULAR_4G -> DevicePowerConstants.MODEM_LTE_ACTIVE_W  * signalFactor
            DeviceNetworkType.CELLULAR_3G -> DevicePowerConstants.MODEM_LTE_ACTIVE_W  * signalFactor * 1.2
            DeviceNetworkType.CELLULAR_2G -> DevicePowerConstants.MODEM_LTE_ACTIVE_W  * signalFactor * 1.5
            DeviceNetworkType.NONE        -> DevicePowerConstants.MODEM_WIFI_IDLE_W
        }
        val modemEnergyKwh = modemPowerW * readings.sessionHours / 1000.0
        val modemCO2       = modemEnergyKwh * grid

        val cpuPowerW = DevicePowerConstants.CPU_IDLE_WATTS +
                (DevicePowerConstants.CPU_ACTIVE_WATTS * readings.thermalState *
                        DevicePowerConstants.THERMAL_MULTIPLIER)
        val cpuEnergyKwh = cpuPowerW * readings.sessionHours / 1000.0
        val cpuCO2       = cpuEnergyKwh * grid

        val measuredBatteryW: Double? = if (
            readings.batteryDrainPct > 0 &&
            readings.sessionHours   > 0 &&
            !readings.isCharging
        ) {
            val capacityWh    = readings.batteryCapacityMah * readings.batteryVoltageV / 1000.0
            val energyUsedWh  = capacityWh * (readings.batteryDrainPct / 100.0)
            energyUsedWh / readings.sessionHours
        } else null

        val powerMethod: PowerMethod
        val totalEnergyKwh: Double
        val totalCO2: Double

        val modelEnergyKwh   = displayEnergyKwh + cpuEnergyKwh + modemEnergyKwh
        val modelTotalEnergy = networkEnergyKwh + dataCentreEnergyKwh + modelEnergyKwh

        if (measuredBatteryW != null &&
            measuredBatteryW > 0.05 &&
            measuredBatteryW < 10.0
        ) {
            val measuredDeviceEnergyKwh = measuredBatteryW * readings.sessionHours / 1000.0
            totalEnergyKwh = networkEnergyKwh + dataCentreEnergyKwh + measuredDeviceEnergyKwh
            totalCO2       = totalEnergyKwh * grid
            powerMethod    = PowerMethod.BATTERY_MEASURED
        } else {
            totalEnergyKwh = modelTotalEnergy
            totalCO2       = totalEnergyKwh * grid
            powerMethod    = PowerMethod.MODEL_ESTIMATED
        }

        return CarbonCalculationResult(
            networkCO2            = networkCO2,
            displayCO2            = displayCO2,
            cpuCO2                = cpuCO2,
            modemCO2              = modemCO2,
            dataCentreCO2         = dataCentreCO2,
            totalCO2              = totalCO2,
            networkEnergyKwh      = networkEnergyKwh,
            displayEnergyKwh      = displayEnergyKwh,
            cpuEnergyKwh          = cpuEnergyKwh,
            modemEnergyKwh        = modemEnergyKwh,
            totalEnergyKwh        = totalEnergyKwh,
            estimatedDisplayW     = displayPowerW,
            estimatedCpuW         = cpuPowerW,
            estimatedModemW       = modemPowerW,
            measuredBatteryW      = measuredBatteryW,
            readings              = readings,
            powerMethod           = powerMethod,
            networkEnergyConstant = networkConstant
        )
    }
}

fun CarbonCalculationResult.toESGReport(): ESGReport {
    val grid = readings.gridIntensityKgPerKwh
    val locale = Locale.US
    return ESGReport(
        carbonFootprint   = totalCO2,
        gridPurity        = ((1 - grid / 0.9) * 100).toInt().coerceIn(0, 100),
        networkEfficiency = if (totalCO2 > 0)
            (readings.wifiMb + readings.mobileMb) / totalCO2
        else 0.0,
        hardwareStress    = readings.thermalState.toDouble(),
        treesOffset       = totalCO2 / 21.0,
        telemetryData     = listOf(
            TelemetryMetric(
                label   = "Grid Intensity",
                value   = "${(grid * 1000).roundToInt()}g",
                subtext = "CO₂/kWh · ${readings.gridRegionName}",
                icon    = Icons.Default.Bolt,
                color   = Color(0xFFFFD54F)
            ),
            TelemetryMetric(
                label   = "Network",
                value   = "${(readings.wifiMb + readings.mobileMb).roundToInt()} MB",
                subtext = "${readings.networkType.label} · ${String.format(locale, "%.1f", networkEnergyConstant * 1000)} Wh/GB",
                icon    = Icons.Default.CloudSync,
                color   = Color(0xFF4FC3F7)
            ),
            TelemetryMetric(
                label   = "Display",
                value   = "${String.format(locale, "%.2f", estimatedDisplayW)}W",
                subtext = "Brightness ${(readings.screenBrightness / 255.0 * 100).roundToInt()}% · ${if (readings.isOLED) "OLED" else "LCD"}",
                icon    = Icons.Default.Brightness6,
                color   = Color(0xFF81C784)
            ),
            TelemetryMetric(
                label   = "Power source",
                value   = if (powerMethod == PowerMethod.BATTERY_MEASURED)
                    "${String.format(locale, "%.2f", measuredBatteryW)}W"
                else "est. ${String.format(locale, "%.2f", estimatedCpuW + estimatedDisplayW + estimatedModemW)}W",
                subtext = if (powerMethod == PowerMethod.BATTERY_MEASURED)
                    "Measured from battery drain"
                else "Model estimated",
                icon    = Icons.Default.BatteryChargingFull,
                color   = Color(0xFFE57373)
            )
        )
    )
}
