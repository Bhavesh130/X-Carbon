package com.example.x_carbon

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
//  XAI DATA MODELS
// ═══════════════════════════════════════════════════════════

data class FeatureAttribution(
    val featureName: String,
    val rawValue: String,
    val co2Contribution: Double,
    val percentShare: Double,
    val shapleyScore: Double,
    val color: Color,
    val direction: AttributionDir
)

enum class AttributionDir { INCREASES, DECREASES, NEUTRAL }

data class Counterfactual(
    val featureName: String,
    val currentValue: String,
    val targetValue: String,
    val co2Saving: Double,
    val newImpactLevel: ImpactLevel,
    val explanation: String
)

enum class ImpactLevel(val label: String, val color: Color) {
    CRITICAL("Critical",  Color(0xFFEF5350)),
    HIGH    ("High",      Color(0xFFFF7043)),
    MODERATE("Moderate",  Color(0xFFFFB300)),
    LOW     ("Low",       Color(0xFF66BB6A)),
    MINIMAL ("Minimal",   Color(0xFF00E676))
}

data class XAIReport(
    val esg: ESGReport,
    val attributions: List<FeatureAttribution>,
    val headline: String,
    val detailedReason: String,
    val confidenceScore: Int,
    val counterfactuals: List<Counterfactual>,
    val impactLevel: ImpactLevel,
    val modelVersion: String,
    val formulaBreakdown: List<Pair<String, String>>
)

// ═══════════════════════════════════════════════════════════
//  XAI ENGINE
// ═══════════════════════════════════════════════════════════

object XAIEngine {

    private const val BASELINE_WIFI_MB   = 200.0
    private const val BASELINE_MOBILE_MB = 50.0
    private const val BASELINE_GRID      = 0.400
    private const val BASELINE_THERMAL   = 1

    private const val WIFI_INTENSITY     = 0.005
    private const val MOBILE_INTENSITY   = 0.080
    private const val BASE_HARDWARE_KWH  = 0.005
    private const val THERMAL_STEP       = 0.2

    fun explain(
        mbWifi: Double,
        mbMobile: Double,
        gridIntensity: Double,
        thermalState: Int,
        esg: ESGReport
    ): XAIReport {

        // ── 1. Isolated CO2 contributions ──────────────────────────────
        val co2Wifi     = mbWifi   / 1024.0 * WIFI_INTENSITY
        val co2Mobile   = mbMobile / 1024.0 * MOBILE_INTENSITY
        val hwKwh       = BASE_HARDWARE_KWH * (1 + thermalState * THERMAL_STEP)
        val co2Hardware = hwKwh * gridIntensity
        val total       = esg.carbonFootprint.coerceAtLeast(0.0001)

        // ── 2. Shapley marginal contributions ──────────────────────────
        val baseWifi   = BASELINE_WIFI_MB   / 1024.0 * WIFI_INTENSITY
        val baseMobile = BASELINE_MOBILE_MB / 1024.0 * MOBILE_INTENSITY
        val baseHwKwh  = BASE_HARDWARE_KWH  * (1 + BASELINE_THERMAL * THERMAL_STEP)
        val baseHw     = baseHwKwh * BASELINE_GRID
        val baseTotal  = baseWifi + baseMobile + baseHw

        fun shapley(isolated: Double, baseline: Double) =
            ((isolated - baseline) / baseTotal).coerceIn(-1.0, 1.0)

        val sWifi     = shapley(co2Wifi,     baseWifi)
        val sMobile   = shapley(co2Mobile,   baseMobile)
        val sHardware = shapley(co2Hardware, baseHw)

        fun direction(s: Double) = when {
            s >  0.05 -> AttributionDir.INCREASES
            s < -0.05 -> AttributionDir.DECREASES
            else      -> AttributionDir.NEUTRAL
        }

        val attributions = listOf(
            FeatureAttribution(
                featureName     = "Wi-Fi data transfer",
                rawValue        = "${mbWifi.roundToInt()} MB",
                co2Contribution = co2Wifi,
                percentShare    = co2Wifi / total * 100,
                shapleyScore    = sWifi,
                color           = Color(0xFF4FC3F7),
                direction       = direction(sWifi)
            ),
            FeatureAttribution(
                featureName     = "Mobile data transfer",
                rawValue        = "${mbMobile.roundToInt()} MB",
                co2Contribution = co2Mobile,
                percentShare    = co2Mobile / total * 100,
                shapleyScore    = sMobile,
                color           = Color(0xFF7E57C2),
                direction       = direction(sMobile)
            ),
            FeatureAttribution(
                featureName     = "Hardware (CPU/thermal)",
                rawValue        = "Thermal level $thermalState",
                co2Contribution = co2Hardware,
                percentShare    = co2Hardware / total * 100,
                shapleyScore    = sHardware,
                color           = Color(0xFFE57373),
                direction       = direction(sHardware)
            ),
            FeatureAttribution(
                featureName     = "Grid carbon intensity",
                rawValue        = "${(gridIntensity * 1000).roundToInt()} g/kWh",
                co2Contribution = co2Hardware,
                percentShare    = (gridIntensity / 0.9) * 100,
                shapleyScore    = ((gridIntensity - BASELINE_GRID) / 0.9).coerceIn(-1.0, 1.0),
                color           = Color(0xFFFFD54F),
                direction       = if (gridIntensity > BASELINE_GRID)
                    AttributionDir.INCREASES
                else AttributionDir.DECREASES
            )
        ).sortedByDescending { it.percentShare }

        // ── 3. Impact level ─────────────────────────────────────────────
        val impact = when {
            total < 0.001 -> ImpactLevel.MINIMAL
            total < 0.005 -> ImpactLevel.LOW
            total < 0.015 -> ImpactLevel.MODERATE
            total < 0.030 -> ImpactLevel.HIGH
            else          -> ImpactLevel.CRITICAL
        }

        // ── 4. Natural language explanation ─────────────────────────────
        val topFeature = attributions.first()
        val topPct     = topFeature.percentShare.roundToInt()
        val gridLabel  = when {
            gridIntensity < 0.2 -> "very clean (renewables-heavy)"
            gridIntensity < 0.4 -> "moderately clean"
            gridIntensity < 0.6 -> "average (mixed sources)"
            else                -> "carbon-intensive (fossil-heavy)"
        }

        val headline = when (impact) {
            ImpactLevel.MINIMAL  -> "Excellent — your digital footprint is near zero."
            ImpactLevel.LOW      -> "Low impact — well below the global average."
            ImpactLevel.MODERATE -> "Moderate — ${topFeature.featureName} is the primary driver ($topPct%)."
            ImpactLevel.HIGH     -> "High impact — ${topFeature.featureName} accounts for $topPct% of total CO₂."
            ImpactLevel.CRITICAL -> "Critical — immediate action recommended."
        }

        val detailedReason = buildString {
            append("Your total emission is ${"%.4f".format(total)} kg CO₂e. ")
            append("The dominant factor is ${topFeature.featureName} ")
            append("(${topPct}% of total, ${topFeature.rawValue}). ")
            if (co2Mobile > co2Wifi) {
                append("Mobile data is 16× more carbon-intensive than Wi-Fi per GB — ")
                append("switching to Wi-Fi would be the single biggest improvement. ")
            }
            append("Your local grid is $gridLabel at ${(gridIntensity * 1000).roundToInt()} g CO₂/kWh. ")
            if (thermalState >= 2) {
                append("Thermal stress at level $thermalState is increasing hardware energy draw. ")
                append("Closing background apps would reduce this. ")
            }
            append("Model confidence: ${buildConfidence(mbWifi, mbMobile, thermalState)}%.")
        }

        // ── 5. Counterfactuals ──────────────────────────────────────────
        val counterfactuals = buildCounterfactuals(
            mbWifi, mbMobile, gridIntensity, thermalState, total, impact
        )

        // ── 6. Formula breakdown ────────────────────────────────────────
        val formulaBreakdown = listOf(
            "Wi-Fi CO₂"      to "(${"%.0f".format(mbWifi)} MB ÷ 1024) × 0.005 = ${"%.5f".format(co2Wifi)} kg",
            "Mobile CO₂"     to "(${"%.0f".format(mbMobile)} MB ÷ 1024) × 0.080 = ${"%.5f".format(co2Mobile)} kg",
            "Hardware CO₂"   to "0.005 × (1 + $thermalState×0.2) × ${"%.3f".format(gridIntensity)} = ${"%.5f".format(co2Hardware)} kg",
            "Grid intensity" to "${(gridIntensity * 1000).roundToInt()} g/kWh (regional grid)",
            "Total CO₂e"     to "${"%.6f".format(total)} kg CO₂e"
        )

        return XAIReport(
            esg               = esg,
            attributions      = attributions,
            headline          = headline,
            detailedReason    = detailedReason,
            confidenceScore   = buildConfidence(mbWifi, mbMobile, thermalState),
            counterfactuals   = counterfactuals,
            impactLevel       = impact,
            modelVersion      = "XAI-v1.0 (LIME + Shapley)",
            formulaBreakdown  = formulaBreakdown
        )
    }

    private fun buildConfidence(mbWifi: Double, mbMobile: Double, thermal: Int): Int {
        var score = 95
        if (mbWifi == 450.0 && mbMobile == 120.0) score -= 20
        if (thermal == 0) score -= 5
        return score.coerceIn(50, 98)
    }

    private fun buildCounterfactuals(
        mbWifi: Double,
        mbMobile: Double,
        gridIntensity: Double,
        thermalState: Int,
        total: Double,
        currentImpact: ImpactLevel
    ): List<Counterfactual> {
        val results = mutableListOf<Counterfactual>()
        lowerImpact(currentImpact) ?: return results

        // CF1 — switch mobile to Wi-Fi
        if (mbMobile > 0) {
            val savingPerMb = (MOBILE_INTENSITY - WIFI_INTENSITY) / 1024.0
            val co2Saving   = mbMobile * savingPerMb
            results.add(Counterfactual(
                featureName    = "Mobile data → Wi-Fi",
                currentValue   = "${mbMobile.roundToInt()} MB on mobile",
                targetValue    = "Use Wi-Fi instead",
                co2Saving      = co2Saving,
                newImpactLevel = impactFor(total - co2Saving),
                explanation    = "Moving ${"%.0f".format(mbMobile)} MB from mobile to Wi-Fi saves " +
                        "${"%.5f".format(co2Saving)} kg CO₂. Wi-Fi is 16× more efficient per GB."
            ))
        }

        // CF2 — reduce mobile data by 30%
        val reducedMobile  = mbMobile * 0.70
        val co2SavedMobile = (mbMobile - reducedMobile) / 1024.0 * MOBILE_INTENSITY
        results.add(Counterfactual(
            featureName    = "Reduce mobile data 30%",
            currentValue   = "${mbMobile.roundToInt()} MB",
            targetValue    = "${reducedMobile.roundToInt()} MB",
            co2Saving      = co2SavedMobile,
            newImpactLevel = impactFor(total - co2SavedMobile),
            explanation    = "Reducing background mobile data by 30% (disable auto-play, lower video " +
                    "quality) saves ${"%.5f".format(co2SavedMobile)} kg CO₂."
        ))

        // CF3 — close background apps (lower thermal)
        if (thermalState >= 2) {
            val newThermal = (thermalState - 1).coerceAtLeast(0)
            val oldHw      = BASE_HARDWARE_KWH * (1 + thermalState * THERMAL_STEP) * gridIntensity
            val newHw      = BASE_HARDWARE_KWH * (1 + newThermal  * THERMAL_STEP) * gridIntensity
            val co2SavedHw = oldHw - newHw
            results.add(Counterfactual(
                featureName    = "Close background apps",
                currentValue   = "Thermal level $thermalState",
                targetValue    = "Thermal level $newThermal",
                co2Saving      = co2SavedHw,
                newImpactLevel = impactFor(total - co2SavedHw),
                explanation    = "Closing background apps drops thermal state $thermalState → $newThermal, " +
                        "saving ${"%.5f".format(co2SavedHw)} kg CO₂/hr."
            ))
        }

        return results.sortedByDescending { it.co2Saving }
    }

    private fun lowerImpact(current: ImpactLevel) = when (current) {
        ImpactLevel.CRITICAL -> ImpactLevel.HIGH
        ImpactLevel.HIGH     -> ImpactLevel.MODERATE
        ImpactLevel.MODERATE -> ImpactLevel.LOW
        ImpactLevel.LOW      -> ImpactLevel.MINIMAL
        ImpactLevel.MINIMAL  -> null
    }

    private fun impactFor(co2: Double) = when {
        co2 < 0.001 -> ImpactLevel.MINIMAL
        co2 < 0.005 -> ImpactLevel.LOW
        co2 < 0.015 -> ImpactLevel.MODERATE
        co2 < 0.030 -> ImpactLevel.HIGH
        else        -> ImpactLevel.CRITICAL
    }
}

// ═══════════════════════════════════════════════════════════
//  XAI COMPOSABLE SCREENS
// ═══════════════════════════════════════════════════════════

@Composable
fun XAIScreen(xai: XAIReport) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        XAIHeader(xai)
        ImpactVerdictCard(xai)
        AttributionSection(xai.attributions)
        if (xai.counterfactuals.isNotEmpty()) {
            CounterfactualSection(xai.counterfactuals)
        }
        TransparencySection(xai)
    }
}

@Composable
private fun XAIHeader(xai: XAIReport) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Psychology, null, tint = CarbonGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "EXPLAINABLE AI",
            style         = MaterialTheme.typography.labelSmall,
            color         = CarbonGreen,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(20.dp), color = CarbonGreen.copy(alpha = 0.1f)) {
            Text(
                "${xai.confidenceScore}% confidence",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color    = CarbonGreen
            )
        }
    }
    Text(
        "Why did I get this score?",
        style      = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun ImpactVerdictCard(xai: XAIReport) {
    val impactColor = xai.impactLevel.color
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, impactColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(impactColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    xai.impactLevel.label.uppercase(),
                    style         = MaterialTheme.typography.labelSmall,
                    color         = impactColor,
                    letterSpacing = 1.5.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                xai.headline,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                fontSize   = 16.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                xai.detailedReason,
                color      = Color.Gray,
                fontSize   = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun AttributionSection(attributions: List<FeatureAttribution>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Feature Attribution", "SHAP-style marginal contributions")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = GlassSurface,
            shape    = RoundedCornerShape(20.dp),
            border   = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                attributions.forEach { AttributionBar(it) }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Each bar shows what % of your total CO₂ comes from that feature. " +
                            "↑ means above baseline average user, ↓ means below.",
                    fontSize   = 11.sp,
                    color      = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun AttributionBar(attr: FeatureAttribution) {
    val animPct by animateFloatAsState(
        targetValue   = (attr.percentShare / 100f).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label         = "attrAnim"
    )

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(attr.color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(attr.featureName, color = Color.White,  fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(attr.rawValue,    color = Color.Gray,   fontSize = 11.sp)
            }
            val (arrowLabel, arrowColor) = when (attr.direction) {
                AttributionDir.INCREASES -> "↑ above avg" to Color(0xFFEF9A9A)
                AttributionDir.DECREASES -> "↓ below avg" to Color(0xFF80CBC4)
                AttributionDir.NEUTRAL   -> "≈ at avg"    to Color.Gray
            }
            Text(arrowLabel, fontSize = 11.sp, color = arrowColor)
            Spacer(Modifier.width(8.dp))
            Text(
                "${attr.percentShare.roundToInt()}%",
                fontWeight = FontWeight.Black,
                color      = attr.color,
                fontSize   = 15.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animPct)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(attr.color.copy(0.6f), attr.color))
                    )
            )
        }
    }
}

@Composable
private fun CounterfactualSection(counterfactuals: List<Counterfactual>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Counterfactual Analysis", "What if you changed one thing?")
        counterfactuals.forEach { CounterfactualCard(it) }
    }
}

@Composable
private fun CounterfactualCard(cf: Counterfactual) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, cf.newImpactLevel.color.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.AutoFixHigh,
                null,
                tint     = cf.newImpactLevel.color,
                modifier = Modifier
                    .size(36.dp)
                    .background(cf.newImpactLevel.color.copy(0.1f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cf.featureName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(0.05f)) {
                        Text(
                            cf.currentValue,
                            fontSize = 11.sp, color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text("  →  ", color = Color.Gray, fontSize = 12.sp)
                    Surface(shape = RoundedCornerShape(6.dp), color = cf.newImpactLevel.color.copy(0.15f)) {
                        Text(
                            cf.targetValue,
                            fontSize = 11.sp, color = cf.newImpactLevel.color,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(cf.explanation, color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = CarbonGreen.copy(0.12f)) {
                        Text(
                            "saves ${"%.5f".format(cf.co2Saving)} kg CO₂",
                            fontSize = 10.sp, color = CarbonGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = cf.newImpactLevel.color.copy(0.12f)) {
                        Text(
                            "→ ${cf.newImpactLevel.label}",
                            fontSize = 10.sp, color = cf.newImpactLevel.color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransparencySection(xai: XAIReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Model Transparency", "Open formula — no black box")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = GlassSurface,
            shape    = RoundedCornerShape(20.dp),
            border   = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Icon(Icons.Default.Science, null, tint = CarbonGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(xai.modelVersion, color = CarbonGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                xai.formulaBreakdown.forEachIndexed { i, (label, formula) ->
                    if (i > 0) HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(label,   color = Color.Gray,  fontSize = 12.sp, modifier = Modifier.width(130.dp))
                        Text(formula, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Model confidence", color = Color.Gray,  fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${xai.confidenceScore}%", color = CarbonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))

                val animConf by animateFloatAsState(
                    targetValue   = xai.confidenceScore / 100f,
                    animationSpec = tween(800),
                    label         = "conf"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animConf)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(CarbonGreen)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Confidence is reduced when network stats fall back to estimated values. " +
                            "All formulas above are reproducible offline.",
                    color      = Color.Gray,
                    fontSize   = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = CarbonGreen.copy(0.05f),
            shape    = RoundedCornerShape(14.dp),
            border   = BorderStroke(1.dp, CarbonGreen.copy(0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Data sources", color = CarbonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                listOf(
                    "Grid intensity — IEA Global Energy Review 2022",
                    "Mobile data CO₂ — Aslan et al. (2018), 0.06–0.08 kWh/GB",
                    "Wi-Fi data CO₂ — Malmodin & Lundén (2018), 0.004–0.006 kWh/GB",
                    "Hardware draw — IEA Smartphones Report 2023, ~0.5W avg",
                    "Attribution — LIME (Ribeiro et al., 2016) + Shapley values"
                ).forEach { source ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("·  ", color = Color.Gray, fontSize = 11.sp)
                        Text(source, color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column {
        Text(title,    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = Color.Gray)
    }
}