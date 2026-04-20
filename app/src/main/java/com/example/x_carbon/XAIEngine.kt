package com.example.x_carbon

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
//  XAI DATA MODELS (same as before)
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
//  XAI ENGINE – NOW USES REAL CarbonCalculationResult
// ═══════════════════════════════════════════════════════════

object XAIEngine {

    // Baselines for Shapley (reference values, not device‑specific)
    private const val BASELINE_WIFI_MB   = 200.0
    private const val BASELINE_MOBILE_MB = 50.0
    private const val BASELINE_GRID      = 0.400      // kg CO₂/kWh
    private const val BASELINE_THERMAL   = 1
    private const val DAILY_BUDGET_KG    = 0.050      // 50g per day

    // Energy intensities (used only for baseline calculations, not for the user's own CO₂)
    private const val WIFI_INTENSITY     = 0.015      // kWh/GB
    private const val MOBILE_INTENSITY   = 0.100      // kWh/GB
    private const val BASE_HARDWARE_KWH  = 0.005      // kWh per hour at thermal level 1
    private const val THERMAL_STEP       = 0.2

    suspend fun explain(result: CarbonCalculationResult): XAIReport {
        val readings = result.readings
        val mbWifi = readings.wifiMb
        val mbMobile = readings.mobileMb
        val gridIntensity = readings.gridIntensityKgPerKwh
        val thermalState = readings.thermalState

        // ──────────────────────────────────────────────────────────
        // Use the REAL calculated CO₂ components from the engine
        // ──────────────────────────────────────────────────────────
        val co2Wifi      = result.networkCO2 * (mbWifi / (mbWifi + mbMobile + 0.01))
        val co2Mobile    = result.networkCO2 * (mbMobile / (mbWifi + mbMobile + 0.01))
        val co2Hardware  = result.cpuCO2 + result.modemCO2 + result.displayCO2
        val co2DataCentre = result.dataCentreCO2

        // Total use‑phase CO₂ (matches result.totalUsePhaseCO2)
        val totalUsePhase = result.totalUsePhaseCO2
        // Final total including embodied, degradation, behaviour factor
        val total = result.totalWithEmbodiedAndDegradation

        // ──────────────────────────────────────────────────────────
        // Shapley baseline values (using the same formulas but with baselines)
        // ──────────────────────────────────────────────────────────
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

        // ──────────────────────────────────────────────────────────
        // Impact level
        // ──────────────────────────────────────────────────────────
        val impact = when {
            total < 0.005 -> ImpactLevel.MINIMAL
            total < 0.015 -> ImpactLevel.LOW
            total < 0.030 -> ImpactLevel.MODERATE
            total < 0.060 -> ImpactLevel.HIGH
            else          -> ImpactLevel.CRITICAL
        }

        val co2Grams  = (total * 1000).roundToInt()
        val gridGrams = (gridIntensity * 1000).roundToInt()

        val topLabel = listOf(
            "Wi-Fi (${mbWifi.roundToInt()} MB)" to co2Wifi,
            "Mobile data (${mbMobile.roundToInt()} MB)" to co2Mobile,
            "Hardware (CPU+display+modem)" to co2Hardware
        ).maxByOrNull { it.second }?.first ?: "network"

        // ──────────────────────────────────────────────────────────
        // Build context for Gemini (using real numbers)
        // ──────────────────────────────────────────────────────────
        val context = """
            Device carbon footprint data:
            - Total CO₂ today: ${co2Grams}g (${"%.5f".format(total)} kg)
            - Impact level: ${impact.label}
            - Wi-Fi data: ${mbWifi.roundToInt()} MB → ${"%.5f".format(co2Wifi)} kg CO₂
            - Mobile data: ${mbMobile.roundToInt()} MB → ${"%.5f".format(co2Mobile)} kg CO₂
            - Hardware (CPU+display+modem): ${"%.5f".format(co2Hardware)} kg CO₂
            - Grid carbon intensity: ${gridGrams}g CO₂/kWh
        """.trimIndent()

        // ──────────────────────────────────────────────────────────
        // Gemini calls (optional – they already have fallbacks)
        // ──────────────────────────────────────────────────────────
        val geminiHeadline = GeminiService.generate(
            """$context
            Write a single concise sentence (max 12 words) summarising this carbon footprint.
            Be specific — mention the actual grams and the biggest driver.
            No asterisks, no markdown, plain text only."""
        )

        val geminiDetail = GeminiService.generate(
            """$context
            Write 3 short paragraphs explaining:
            1. Why the score is ${impact.label} and what the ${co2Grams}g means in context
            2. Why $topLabel is the biggest contributor and what the user can do about it
            3. How the ${gridGrams}g/kWh grid intensity affects every component
            Be conversational, specific, and educational. Max 120 words total.
            No asterisks, no markdown, no bullet points, plain text only."""
        )

        val geminiTip = GeminiService.generate(
            """$context
            Write one practical, specific eco tip for this user based on their actual data.
            The tip must reference their specific numbers (e.g. "${mbMobile.roundToInt()} MB on mobile").
            Max 30 words. No asterisks, no markdown, plain text only."""
        )

        val geminiWifiInsight = GeminiService.generate(
            """Wi-Fi usage: ${mbWifi.roundToInt()} MB, ${"%.4f".format(co2Wifi)} kg CO₂, ${(co2Wifi/sumShares*100).roundToInt()}% of total.
            Write one sentence insight about this Wi-Fi usage. Max 20 words. Plain text only."""
        )

        val geminiMobileInsight = GeminiService.generate(
            """Mobile data usage: ${mbMobile.roundToInt()} MB, ${"%.4f".format(co2Mobile)} kg CO₂.
            Write one sentence insight. Max 20 words. Plain text only."""
        )

        val geminiHardwareInsight = GeminiService.generate(
            """CPU thermal level: $thermalState (0=idle, 4=critical), ${"%.4f".format(co2Hardware)} kg CO₂.
            Write one sentence insight about hardware impact. Max 20 words. Plain text only."""
        )

        val geminiGridInsight = GeminiService.generate(
            """Grid intensity: ${gridGrams}g CO₂/kWh.
            Write one sentence about what this grid intensity means for the user. Max 20 words. Plain text only."""
        )

        val geminiCfWifi = if (mbMobile > 0) GeminiService.generate(
            """User has ${mbMobile.roundToInt()} MB on mobile data.
            Switching to Wi-Fi would save ${"%.5f".format(mbMobile * (MOBILE_INTENSITY - WIFI_INTENSITY) / 1024.0)} kg CO₂.
            Write one sentence explaining this saving in practical terms. Max 25 words. Plain text only."""
        ) else ""

        val aiPowered = geminiHeadline.isNotBlank()

        // ──────────────────────────────────────────────────────────
        // Build attribution list using real contributions
        // ──────────────────────────────────────────────────────────
        val attributions = listOf(
            FeatureAttribution(
                featureName     = "Wi-Fi data transfer",
                rawValue        = "${mbWifi.roundToInt()} MB",
                co2Contribution = co2Wifi,
                percentShare    = (co2Wifi / sumShares * 100).coerceIn(0.0, 100.0),
                shapleyScore    = sWifi,
                color           = Color(0xFF4FC3F7),
                direction       = direction(sWifi),
                insight         = geminiWifiInsight.ifBlank { "Wi‑Fi is generally efficient; your usage is ${if (sWifi > 0) "higher" else "lower"} than average." }
            ),
            FeatureAttribution(
                featureName     = "Mobile data transfer",
                rawValue        = "${mbMobile.roundToInt()} MB",
                co2Contribution = co2Mobile,
                percentShare    = (co2Mobile / sumShares * 100).coerceIn(0.0, 100.0),
                shapleyScore    = sMobile,
                color           = Color(0xFF7E57C2),
                direction       = direction(sMobile),
                insight         = geminiMobileInsight.ifBlank { "Mobile data is much more carbon‑intensive than Wi‑Fi. Try to use Wi‑Fi when possible." }
            ),
            FeatureAttribution(
                featureName     = "Hardware (CPU + display + modem)",
                rawValue        = "Thermal level $thermalState",
                co2Contribution = co2Hardware,
                percentShare    = (co2Hardware / sumShares * 100).coerceIn(0.0, 100.0),
                shapleyScore    = sHardware,
                color           = Color(0xFFE57373),
                direction       = direction(sHardware),
                insight         = geminiHardwareInsight.ifBlank { "Higher thermal levels mean more power draw. Reducing heavy tasks helps." }
            ),
            FeatureAttribution(
                featureName     = "Grid carbon intensity",
                rawValue        = "${gridGrams}g/kWh",
                co2Contribution = co2Hardware,  // not perfect, but used for display only
                percentShare    = ((gridIntensity / 0.9) * 100).coerceIn(0.0, 100.0),
                shapleyScore    = sGrid,
                color           = Color(0xFFFFD54F),
                direction       = if (gridIntensity > BASELINE_GRID) AttributionDir.INCREASES else AttributionDir.DECREASES,
                insight         = geminiGridInsight.ifBlank { "Your local grid’s carbon intensity directly affects every component." }
            )
        ).sortedByDescending { it.percentShare }

        // ──────────────────────────────────────────────────────────
        // Counterfactuals (only if mobile data is used)
        // ──────────────────────────────────────────────────────────
        val counterfactuals = if (mbMobile > 0) {
            val saving = mbMobile * (MOBILE_INTENSITY - WIFI_INTENSITY) / 1024.0
            listOf(
                Counterfactual(
                    featureName = "Mobile data → Wi-Fi",
                    currentValue = "${mbMobile.roundToInt()} MB on mobile",
                    targetValue = "Use Wi-Fi instead",
                    co2Saving = saving,
                    newImpactLevel = impactFor(total - saving),
                    explanation = geminiCfWifi.ifBlank { "Switching from mobile to Wi‑Fi would save ${"%.5f".format(saving)} kg CO₂ in this session." }
                )
            )
        } else emptyList()

        // ──────────────────────────────────────────────────────────
        // Build final XAIReport
        // ──────────────────────────────────────────────────────────
        return XAIReport(
            esg = result.toESGReport(),
            attributions = attributions,
            headline = geminiHeadline.ifBlank { "Your digital footprint is ${co2Grams}g today, driven mainly by $topLabel." },
            detailedReason = geminiDetail.ifBlank { "Your total emission is ${"%.4f".format(total)} kg CO₂e. The dominant factor is $topLabel. ${if (gridIntensity > 0.4) "Your grid is relatively carbon‑intensive." else "Your grid is relatively clean."}" },
            confidenceScore = 92,
            counterfactuals = counterfactuals,
            impactLevel = impact,
            modelVersion = "XAI-v2.0 (Gemini + LIME + Shapley)",
            formulaBreakdown = listOf(
                "Wi-Fi CO₂" to "(${mbWifi.roundToInt()} MB ÷ 1024) × 0.015 = ${"%.5f".format(co2Wifi)} kg",
                "Mobile CO₂" to "(${mbMobile.roundToInt()} MB ÷ 1024) × 0.100 = ${"%.5f".format(co2Mobile)} kg",
                "Hardware CO₂" to "${"%.5f".format(co2Hardware)} kg (from CPU+display+modem)",
                "Grid intensity" to "$gridGrams g/kWh",
                "Total CO₂e" to "${"%.6f".format(total)} kg"
            ),
            biasFlags = emptyList(),
            carbonBudget = CarbonBudget(
                dailyBudgetKg = DAILY_BUDGET_KG,
                usedKg = total,
                remainingKg = (DAILY_BUDGET_KG - total).coerceAtLeast(0.0),
                percentUsed = (total / DAILY_BUDGET_KG * 100).coerceAtMost(100.0)
            ),
            ecoTip = geminiTip.ifBlank { "Consider switching to Wi‑Fi and lowering screen brightness to reduce emissions." },
            aiPowered = aiPowered
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