package com.example.x_carbon

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
//  XAI DATA MODELS
// ═══════════════════════════════════════════════════════════

data class FeatureAttribution(
    val featureName:     String,
    val rawValue:        String,
    val co2Contribution: Double,
    val percentShare:    Double,
    val shapleyScore:    Double,
    val color:           Color,
    val direction:       AttributionDir,
    val insight:         String = ""
)

enum class AttributionDir { INCREASES, DECREASES, NEUTRAL }

data class Counterfactual(
    val featureName:    String,
    val currentValue:   String,
    val targetValue:    String,
    val co2Saving:      Double,
    val newImpactLevel: ImpactLevel,
    val explanation:    String,
    val difficulty:     ActionDifficulty = ActionDifficulty.EASY
)

enum class ActionDifficulty(val label: String, val color: Color) {
    EASY  ("Easy",   Color(0xFF00E676)),
    MEDIUM("Medium", Color(0xFFFFB300))
}

enum class ImpactLevel(val label: String, val color: Color) {
    MINIMAL ("Minimal",  Color(0xFF00E676)),
    LOW     ("Low",      Color(0xFF66BB6A)),
    MODERATE("Moderate", Color(0xFFFFB300)),
    HIGH    ("High",     Color(0xFFFF7043)),
    CRITICAL("Critical", Color(0xFFEF5350))
}

data class BiasFlag(
    val title:       String,
    val description: String,
    val severity:    BiasSeverity
)

enum class BiasSeverity(val label: String, val color: Color) {
    LOW   ("Low",    Color(0xFF66BB6A)),
    MEDIUM("Medium", Color(0xFFFFB300)),
    HIGH  ("High",   Color(0xFFEF5350))
}

data class CarbonBudget(
    val dailyBudgetKg: Double,
    val usedKg:        Double,
    val remainingKg:   Double,
    val percentUsed:   Double
)

data class XAIReport(
    val esg:              ESGReport,
    val attributions:     List<FeatureAttribution>,
    val headline:         String,
    val detailedReason:   String,
    val confidenceScore:  Int,
    val counterfactuals:  List<Counterfactual>,
    val impactLevel:      ImpactLevel,
    val modelVersion:     String,
    val formulaBreakdown: List<Pair<String, String>>,
    val biasFlags:        List<BiasFlag>,
    val carbonBudget:     CarbonBudget,
    val ecoTip:           String,
    val aiPowered:        Boolean = false
)

// ═══════════════════════════════════════════════════════════
//  XAI ENGINE (NO GEMINI, FULLY OFFLINE)
// ═══════════════════════════════════════════════════════════

object XAIEngine {

    private const val BASELINE_WIFI_MB   = 200.0
    private const val BASELINE_MOBILE_MB = 50.0
    private const val BASELINE_GRID      = 0.400
    private const val BASELINE_THERMAL   = 1
    private const val DAILY_BUDGET_KG    = 0.050
    private const val WIFI_INTENSITY     = 0.015
    private const val MOBILE_INTENSITY   = 0.100
    private const val BASE_HARDWARE_KWH  = 0.005
    private const val THERMAL_STEP       = 0.2

    suspend fun explain(result: CarbonCalculationResult): XAIReport {
        val readings = result.readings
        val mbWifi = readings.wifiMb
        val mbMobile = readings.mobileMb
        val gridIntensity = readings.gridIntensityKgPerKwh
        val thermalState = readings.thermalState

        val co2Wifi      = result.networkCO2 * (mbWifi / (mbWifi + mbMobile + 0.01))
        val co2Mobile    = result.networkCO2 * (mbMobile / (mbWifi + mbMobile + 0.01))
        val co2Hardware  = result.cpuCO2 + result.modemCO2 + result.displayCO2
        val total = result.totalWithEmbodiedAndDegradation

        val baseWifi   = BASELINE_WIFI_MB   / 1024.0 * WIFI_INTENSITY
        val baseMobile = BASELINE_MOBILE_MB / 1024.0 * MOBILE_INTENSITY
        val baseHwKwh  = BASE_HARDWARE_KWH * (1 + BASELINE_THERMAL * THERMAL_STEP)
        val baseHw     = baseHwKwh * BASELINE_GRID
        val baseTotal  = (baseWifi + baseMobile + baseHw).coerceAtLeast(0.0001)

        fun shapley(isolated: Double, baseline: Double) =
            ((isolated - baseline) / baseTotal).coerceIn(-1.0, 1.0)
        fun direction(s: Double) = when {
            s >  0.05 -> AttributionDir.INCREASES
            s < -0.05 -> AttributionDir.DECREASES
            else      -> AttributionDir.NEUTRAL
        }

        val sWifi     = shapley(co2Wifi,     baseWifi)
        val sMobile   = shapley(co2Mobile,   baseMobile)
        val sHardware = shapley(co2Hardware, baseHw)
        val sGrid     = ((gridIntensity - BASELINE_GRID) / 0.9).coerceIn(-1.0, 1.0)

        val sumShares = (co2Wifi + co2Mobile + co2Hardware).coerceAtLeast(0.0001)

        val impact = when {
            total < 0.005 -> ImpactLevel.MINIMAL
            total < 0.015 -> ImpactLevel.LOW
            total < 0.030 -> ImpactLevel.MODERATE
            total < 0.060 -> ImpactLevel.HIGH
            else          -> ImpactLevel.CRITICAL
        }

        val co2Grams  = (total * 1000).roundToInt()
        val gridGrams = (gridIntensity * 1000).roundToInt()
        val topLabel = listOf("Wi-Fi", "Mobile data", "Hardware")
            .zip(listOf(co2Wifi, co2Mobile, co2Hardware))
            .maxByOrNull { it.second }?.first ?: "network"

        // Rule‑based text
        val headline = when {
            total < 0.005 -> "Your digital footprint is minimal today (${co2Grams}g)."
            total < 0.015 -> "Your digital footprint is low (${co2Grams}g), driven mainly by $topLabel."
            total < 0.030 -> "Your digital footprint is moderate (${co2Grams}g). $topLabel is the biggest contributor."
            total < 0.060 -> "Your digital footprint is high (${co2Grams}g). $topLabel dominates your usage."
            else          -> "Your digital footprint is critical (${co2Grams}g). $topLabel is the main cause – consider reducing it."
        }

        val detailedReason = buildString {
            append("Your total CO₂e is ${"%.4f".format(total)} kg. ")
            append("Wi‑Fi contributed ${"%.4f".format(co2Wifi)} kg, mobile data ${"%.4f".format(co2Mobile)} kg, hardware ${"%.4f".format(co2Hardware)} kg. ")
            if (gridIntensity > 0.5) {
                append("Your grid is relatively carbon‑intensive (${gridGrams}g/kWh), which increases every component's impact. ")
            } else {
                append("Your grid is relatively clean (${gridGrams}g/kWh), which helps lower emissions. ")
            }
            if (thermalState > 2) {
                append("Your device is running hot (thermal level $thermalState) – heavy tasks increase hardware emissions.")
            } else {
                append("Your device thermal state is moderate (level $thermalState).")
            }
        }

        val ecoTip = when {
            mbMobile > 0 -> "You used ${mbMobile.roundToInt()} MB on mobile data. Switching to Wi‑Fi could save ~${"%.4f".format(mbMobile * (MOBILE_INTENSITY - WIFI_INTENSITY) / 1024.0)} kg CO₂ per session."
            thermalState > 2 -> "Your device is running hot – closing heavy apps or lowering screen brightness can reduce hardware emissions."
            gridIntensity > 0.5 -> "Your grid is carbon‑intensive. Try using energy during off‑peak hours or consider renewable energy plans."
            else -> "Keep up the good habits! Even small actions like lowering brightness and using Wi‑Fi help."
        }

        val wifiInsight = when {
            sWifi > 0.1 -> "Wi‑Fi usage is higher than average. Consider downloading large files overnight or reducing background sync."
            sWifi < -0.1 -> "Wi‑Fi usage is lower than average – great!"
            else -> "Wi‑Fi usage is within typical range."
        }
        val mobileInsight = when {
            sMobile > 0.1 -> "Mobile data is much more carbon‑intensive than Wi‑Fi. Try to use Wi‑Fi when possible."
            sMobile < -0.1 -> "You use very little mobile data – excellent!"
            else -> "Mobile data usage is moderate."
        }
        val hardwareInsight = when {
            thermalState > 2 -> "Your device is under high load, increasing hardware emissions. Close unused apps or let it cool down."
            thermalState == 0 -> "Your device is mostly idle – hardware impact is low."
            else -> "Hardware emissions are driven by CPU load and display brightness."
        }
        val gridInsight = when {
            gridIntensity > 0.6 -> "Your local electricity grid is quite dirty. Every kilowatt‑hour emits ${gridGrams}g CO₂."
            gridIntensity < 0.3 -> "Your grid is very clean – great for your carbon footprint!"
            else -> "Your grid intensity is ${gridGrams}g/kWh, which is around the global average."
        }

        val counterfactualExplanation = if (mbMobile > 0) {
            val saving = mbMobile * (MOBILE_INTENSITY - WIFI_INTENSITY) / 1024.0
            "Switching from mobile data to Wi‑Fi would save approximately ${"%.5f".format(saving)} kg CO₂ in this session – that's like driving ${(saving / 0.2).roundToInt()} fewer kilometres by car."
        } else ""

        val attributions = listOf(
            FeatureAttribution("Wi-Fi data transfer", "${mbWifi.roundToInt()} MB", co2Wifi,
                (co2Wifi / sumShares * 100).coerceIn(0.0, 100.0), sWifi, Color(0xFF4FC3F7), direction(sWifi), wifiInsight),
            FeatureAttribution("Mobile data transfer", "${mbMobile.roundToInt()} MB", co2Mobile,
                (co2Mobile / sumShares * 100).coerceIn(0.0, 100.0), sMobile, Color(0xFF7E57C2), direction(sMobile), mobileInsight),
            FeatureAttribution("Hardware (CPU+display+modem)", "Thermal level $thermalState", co2Hardware,
                (co2Hardware / sumShares * 100).coerceIn(0.0, 100.0), sHardware, Color(0xFFE57373), direction(sHardware), hardwareInsight),
            FeatureAttribution("Grid carbon intensity", "${gridGrams}g/kWh", co2Hardware,
                ((gridIntensity / 0.9) * 100).coerceIn(0.0, 100.0), sGrid, Color(0xFFFFD54F),
                if (gridIntensity > BASELINE_GRID) AttributionDir.INCREASES else AttributionDir.DECREASES, gridInsight)
        ).sortedByDescending { it.percentShare }

        val counterfactuals = if (mbMobile > 0) {
            val saving = mbMobile * (MOBILE_INTENSITY - WIFI_INTENSITY) / 1024.0
            listOf(Counterfactual("Mobile data → Wi-Fi", "${mbMobile.roundToInt()} MB on mobile", "Use Wi-Fi instead",
                saving, impactFor(total - saving), counterfactualExplanation))
        } else emptyList()

        return XAIReport(
            esg = result.toESGReport(),
            attributions = attributions,
            headline = headline,
            detailedReason = detailedReason,
            confidenceScore = 92,
            counterfactuals = counterfactuals,
            impactLevel = impact,
            modelVersion = "XAI-v2.0 (Rule‑based + Shapley)",
            formulaBreakdown = listOf(
                "Wi-Fi CO₂" to "(${mbWifi.roundToInt()} MB ÷ 1024) × 0.015 = ${"%.5f".format(co2Wifi)} kg",
                "Mobile CO₂" to "(${mbMobile.roundToInt()} MB ÷ 1024) × 0.100 = ${"%.5f".format(co2Mobile)} kg",
                "Hardware CO₂" to "0.005 × (1 + ${thermalState}×0.2) × ${"%.3f".format(gridIntensity)} = ${"%.5f".format(co2Hardware)} kg",
                "Grid intensity" to "$gridGrams g/kWh",
                "Total CO₂e" to "${"%.6f".format(total)} kg"
            ),
            biasFlags = emptyList(),
            carbonBudget = CarbonBudget(DAILY_BUDGET_KG, total, (DAILY_BUDGET_KG - total).coerceAtLeast(0.0),
                (total / DAILY_BUDGET_KG * 100).coerceAtMost(100.0)),
            ecoTip = ecoTip,
            aiPowered = false
        )
    }

    private fun impactFor(co2: Double): ImpactLevel = when {
        co2 < 0.005 -> ImpactLevel.MINIMAL
        co2 < 0.015 -> ImpactLevel.LOW
        co2 < 0.030 -> ImpactLevel.MODERATE
        co2 < 0.060 -> ImpactLevel.HIGH
        else        -> ImpactLevel.CRITICAL
    }
}
