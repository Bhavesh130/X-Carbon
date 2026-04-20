package com.example.x_carbon

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import android.content.Context

// ══════════════════════════════════════════════════════════
//  DATA MODEL
// ══════════════════════════════════════════════════════════

data class CarbonHistoryRecord(
    val timestamp:     Long,
    val co2Kg:         Double,
    val gridIntensity: Double,
    val thermalState:  Int,
    val mbWifi:        Double,
    val mbMobile:      Double
) {
    val co2Grams:  Int    get() = (co2Kg * 1000).roundToInt()
    val timeLabel: String get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    val dateLabel: String get() = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
}

object CarbonHistoryManager {
    fun addRecord(existing: List<CarbonHistoryRecord>, co2Kg: Double, gridIntensity: Double, thermalState: Int, mbWifi: Double, mbMobile: Double): List<CarbonHistoryRecord> {
        return (existing + CarbonHistoryRecord(System.currentTimeMillis(), co2Kg, gridIntensity, thermalState, mbWifi, mbMobile)).takeLast(48)
    }
}



// ══════════════════════════════════════════════════════════
//  HISTORY SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun HistoryScreen(
    records:     List<CarbonHistoryRecord>,
    predictions: List<CarbonPrediction> = emptyList()
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = CarbonGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("CARBON HISTORY", style = MaterialTheme.typography.labelSmall,
                color = CarbonGreen, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(20.dp), color = CarbonGreen.copy(0.1f)) {
                Text("${records.size} records",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp, color = CarbonGreen)
            }
        }

        Text("CO₂ over time", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold)

        if (records.isEmpty()) { EmptyHistoryCard(); return@Column }

        HistorySummaryRow(records)
        HistoryLineChart(records)

        // ── AI Forecast section ───────────────────────────
        if (predictions.isNotEmpty()) {
            ForecastCard(predictions)
        }

        HistoryBreakdownChart(records)
        HistoryLogTable(records.takeLast(5))
        Spacer(Modifier.height(8.dp))
    }
}

// ── 7-Day Forecast Card ────────────────────────────────────

@Composable
fun ForecastCard(predictions: List<CarbonPrediction>) {
    val trend     = predictions.first().trend
    val trendColor = when (trend) {
        PredictionTrend.IMPROVING  -> CarbonGreen
        PredictionTrend.STABLE     -> Color(0xFF4FC3F7)
        PredictionTrend.WORSENING  -> Color(0xFFFF7043)
    }
    val trendLabel = when (trend) {
        PredictionTrend.IMPROVING -> "Improving ↓"
        PredictionTrend.STABLE    -> "Stable →"
        PredictionTrend.WORSENING -> "Worsening ↑"
    }

    val animProgress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "forecastAnim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, trendColor.copy(0.35f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(trendColor.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrendingUp, null,
                        tint = trendColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("7-Day CO₂ Forecast", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 15.sp)
                    Text("Linear regression · ${predictions.size}-day window",
                        fontSize = 11.sp, color = Color.Gray)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = trendColor.copy(0.12f)) {
                    Text(trendLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp, color = trendColor, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Forecast bars
            val maxVal = predictions.maxOf { it.upperBound }.coerceAtLeast(0.001)
            Row(
                modifier              = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.Bottom
            ) {
                predictions.forEach { pred ->
                    val barHeight = ((pred.predictedCo2Kg / maxVal) * animProgress)
                        .toFloat().coerceIn(0.02f, 1f)
                    val predColor = when {
                        pred.predictedCo2Kg < 0.005 -> CarbonGreen
                        pred.predictedCo2Kg < 0.015 -> Color(0xFFFFB300)
                        pred.predictedCo2Kg < 0.030 -> Color(0xFFFF7043)
                        else                        -> Color(0xFFEF5350)
                    }

                    Column(
                        modifier            = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Predicted value
                        Text(
                            "${(pred.predictedCo2Kg * 1000).roundToInt()}g",
                            fontSize = 8.sp, color = predColor,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        // Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(predColor, predColor.copy(0.5f))
                                    )
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        // Day label
                        Text(pred.dayLabel, fontSize = 8.sp, color = Color.Gray,
                            textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(10.dp))

            // Stats row
            val avgPredicted  = predictions.map { it.predictedCo2Kg }.average()
            val peakDay       = predictions.maxByOrNull { it.predictedCo2Kg }
            val bestDay       = predictions.minByOrNull { it.predictedCo2Kg }

            Row(modifier = Modifier.fillMaxWidth()) {
                ForecastStat("Avg/day",
                    "${(avgPredicted * 1000).roundToInt()}g",
                    Color(0xFF4FC3F7), Modifier.weight(1f))
                ForecastStat("Peak",
                    "${peakDay?.dayLabel ?: "–"}",
                    Color(0xFFFF7043), Modifier.weight(1f))
                ForecastStat("Best",
                    "${bestDay?.dayLabel ?: "–"}",
                    CarbonGreen, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            // Model note
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = Color.White.copy(0.03f),
                shape    = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Color.Gray,
                        modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Prediction uses linear regression on your last ${predictions.size} days " +
                                "of readings. Accuracy improves with more history. " +
                                "Confidence bands show 95% prediction interval.",
                        fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

// ══════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════

@Composable
private fun EmptyHistoryCard() {
    Surface(Modifier.fillMaxWidth(), color=GlassSurface, shape=RoundedCornerShape(24.dp), border=BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(40.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).background(Color.Gray.copy(0.1f), CircleShape), Alignment.Center) {
                Icon(Icons.Default.History, null, tint=Color.Gray, modifier=Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("No history yet", fontWeight=FontWeight.Bold, color=Color.White, fontSize=16.sp)
            Spacer(Modifier.height(8.dp))
            Text("Grant permissions on the Dashboard and your CO₂ history will appear here automatically.", color=Color.Gray, fontSize=13.sp, textAlign=TextAlign.Center, lineHeight=20.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SUMMARY ROW
// ══════════════════════════════════════════════════════════

@Composable
private fun HistorySummaryRow(records: List<CarbonHistoryRecord>) {
    val avg   = records.map { it.co2Kg }.average()
    val max   = records.maxOf { it.co2Kg }
    val min   = records.minOf { it.co2Kg }
    val trend = if (records.size >= 2) records.last().co2Kg - records[records.size - 2].co2Kg else 0.0

    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        SummaryTile("Average",  "${(avg   * 1000).roundToInt()}g", Color(0xFF4FC3F7), Modifier.weight(1f))
        SummaryTile("Peak",     "${(max   * 1000).roundToInt()}g", Color(0xFFEF5350), Modifier.weight(1f))
        SummaryTile("Best",     "${(min   * 1000).roundToInt()}g", CarbonGreen,       Modifier.weight(1f))
        SummaryTile("Trend",    "${if (trend>0) "+" else ""}${(trend*1000).roundToInt()}g", if (trend>0) Color(0xFFEF5350) else CarbonGreen, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color=GlassSurface, shape=RoundedCornerShape(14.dp), border=BorderStroke(1.dp, color.copy(0.25f))) {
        Column(Modifier.padding(10.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Text(value, fontWeight=FontWeight.Black, color=color, fontSize=16.sp)
            Text(label, fontSize=9.sp, color=Color.Gray)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  LINE CHART
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryLineChart(records: List<CarbonHistoryRecord>) {
    val animProgress by animateFloatAsState(1f, tween(1200, easing=FastOutSlowInEasing), label="la")

    Surface(Modifier.fillMaxWidth(), color=GlassSurface, shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(CarbonGreen.copy(0.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.ShowChart, null, tint=CarbonGreen, modifier=Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("CO₂ trend over time", fontWeight=FontWeight.Bold, color=Color.White, fontSize=14.sp)
                    Text("kg CO₂ per reading", fontSize=11.sp, color=Color.Gray)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(180.dp).drawBehind { drawLineChart(records, animProgress, this.size) })
            Spacer(Modifier.height(8.dp))
            if (records.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(records.first().timeLabel,                    fontSize=9.sp, color=Color.Gray)
                    if (records.size > 2) Text(records[records.size/2].timeLabel, fontSize=9.sp, color=Color.Gray)
                    Text(records.last().timeLabel,                     fontSize=9.sp, color=Color.Gray)
                }
            }
        }
    }
}

private fun DrawScope.drawLineChart(records: List<CarbonHistoryRecord>, progress: Float, size: androidx.compose.ui.geometry.Size) {
    if (records.size < 2) return
    val values  = records.map { it.co2Kg.toFloat() }
    val maxVal  = values.max().coerceAtLeast(0.001f)
    val minVal  = values.min()
    val range   = (maxVal - minVal).coerceAtLeast(0.0001f)
    val w = size.width; val h = size.height
    val padTop = 12f; val padBot = 8f; val usableH = h - padTop - padBot

    repeat(4) { i ->
        val y = padTop + usableH * (i / 3f)
        drawLine(Color.White.copy(0.05f), Offset(0f, y), Offset(w, y), 1f)
    }

    val visibleCount = (records.size * progress).roundToInt().coerceAtLeast(2)
    val visibleRecs  = records.take(visibleCount)
    val points = visibleRecs.mapIndexed { i, rec ->
        val x = if (visibleRecs.size > 1) w * i / (visibleRecs.size - 1).toFloat() else w / 2f
        val y = padTop + usableH * (1f - (rec.co2Kg.toFloat() - minVal) / range)
        Offset(x, y)
    }

    if (points.size >= 2) {
        val fillPath = Path().apply { moveTo(points.first().x, h); points.forEach { lineTo(it.x, it.y) }; lineTo(points.last().x, h); close() }
        drawPath(fillPath, Brush.verticalGradient(listOf(Color(0xFF00E676).copy(0.25f), Color(0xFF00E676).copy(0f)), 0f, h))
        val linePath = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(linePath, Color(0xFF00E676), style=Stroke(2.5f, cap=StrokeCap.Round, join=StrokeJoin.Round))
    }

    points.forEachIndexed { i, pt ->
        val isLast = i == points.lastIndex
        drawCircle(if (isLast) Color(0xFF00E676) else Color(0xFF00E676).copy(0.5f), if (isLast) 6f else 3f, pt)
        if (isLast) drawCircle(Color(0xFF07090B), 3f, pt)
    }
}

// ══════════════════════════════════════════════════════════
//  BAR CHART
// ══════════════════════════════════════════════════════════



// ══════════════════════════════════════════════════════════
//  BREAKDOWN CHART
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryBreakdownChart(records: List<CarbonHistoryRecord>) {
    val display  = records.takeLast(8)
    val wifiAvg  = display.map { it.mbWifi   / 1024.0 * 0.005 }.average()
    val mobAvg   = display.map { it.mbMobile / 1024.0 * 0.080 }.average()
    val hwAvg    = display.map { it.gridIntensity * 0.005 * (1 + it.thermalState * 0.2) }.average()
    val total    = (wifiAvg + mobAvg + hwAvg).coerceAtLeast(0.0001)
    val segments = listOf(Triple("Wi-Fi", wifiAvg, Color(0xFF4FC3F7)), Triple("Mobile", mobAvg, Color(0xFF7E57C2)), Triple("Hardware", hwAvg, Color(0xFFE57373)))
    val animProg by animateFloatAsState(1f, tween(1000), label="bd")

    Surface(Modifier.fillMaxWidth(), color=GlassSurface, shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(Color(0xFFE57373).copy(0.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.PieChart, null, tint=Color(0xFFE57373), modifier=Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Average CO₂ source breakdown", fontWeight=FontWeight.Bold, color=Color.White, fontSize=14.sp)
                    Text("Last ${display.size} sessions", fontSize=11.sp, color=Color.Gray)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                segments.forEach { (_, value, color) ->
                    val f = ((value / total) * animProg).toFloat()
                    if (f > 0f) Box(Modifier.weight(f.coerceAtLeast(0.001f)).fillMaxHeight().background(color))
                }
            }
            Spacer(Modifier.height(14.dp))
            segments.forEach { (label, value, color) ->
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp), verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color=Color.White, fontSize=13.sp, modifier=Modifier.weight(1f))
                    Text("${(value * 1000 / total * 100).roundToInt()}%", color=color, fontSize=13.sp, fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("${"%.4f".format(value)} kg avg", color=Color.Gray, fontSize=11.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  STATS CARD
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryStatsCard(records: List<CarbonHistoryRecord>) {
    val totalCO2      = records.sumOf { it.co2Kg }
    val avgGrid       = records.map { it.gridIntensity * 1000 }.average()
    val cleanSessions = records.count { it.co2Kg < 0.005 }
    val treesNeeded   = totalCO2 / 21.0

    Surface(Modifier.fillMaxWidth(), color=GlassSurface, shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(CarbonGreen.copy(0.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.Analytics, null, tint=CarbonGreen, modifier=Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Session Statistics", fontWeight=FontWeight.Bold, color=Color.White, fontSize=14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatChip("Total CO₂",       "${"%.3f".format(totalCO2)} kg",                      Color(0xFFEF5350), Modifier.weight(1f))
                StatChip("Avg Grid",         "${avgGrid.roundToInt()} g/kWh",                      Color(0xFFFFD54F), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatChip("Clean sessions",  "$cleanSessions / ${records.size}",                    CarbonGreen,       Modifier.weight(1f))
                StatChip("Trees needed",    "${"%.4f".format(treesNeeded)}",                       Color(0xFF81C784),  Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color=color.copy(0.07f), shape=RoundedCornerShape(12.dp), border=BorderStroke(1.dp, color.copy(0.2f))) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontWeight=FontWeight.Bold, color=color, fontSize=14.sp)
            Text(label, fontSize=10.sp, color=Color.Gray)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  LOG TABLE
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryLogTable(records: List<CarbonHistoryRecord>) {
    Surface(Modifier.fillMaxWidth(), color=GlassSurface, shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.padding(bottom=12.dp)) {
                Box(Modifier.size(32.dp).background(Color.Gray.copy(0.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.TableChart, null, tint=Color.Gray, modifier=Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Session log", fontWeight=FontWeight.Bold, color=Color.White, fontSize=14.sp)
            }
            Row(Modifier.fillMaxWidth()) {
                LogCell("Time",    Color.Gray, Modifier.weight(1.2f))
                LogCell("CO₂ (g)", Color.Gray, Modifier.weight(1f))
                LogCell("Grid",    Color.Gray, Modifier.weight(1f))
                LogCell("Thermal", Color.Gray, Modifier.weight(0.8f))
            }
            HorizontalDivider(color=BorderColor, modifier=Modifier.padding(vertical=6.dp))
            records.reversed().forEach { rec ->
                val rowColor = when { rec.co2Kg < 0.005 -> CarbonGreen; rec.co2Kg < 0.015 -> Color(0xFFFFB300); rec.co2Kg < 0.030 -> Color(0xFFFF7043); else -> Color(0xFFEF5350) }
                Row(Modifier.fillMaxWidth().padding(vertical=5.dp), verticalAlignment=Alignment.CenterVertically) {
                    LogCell("${rec.dateLabel} ${rec.timeLabel}", Color.Gray,  Modifier.weight(1.2f))
                    LogCell("${rec.co2Grams}g",                              rowColor,    Modifier.weight(1f))
                    LogCell("${(rec.gridIntensity * 1000).roundToInt()}g/kWh", Color.Gray, Modifier.weight(1f))
                    LogCell("Lvl ${rec.thermalState}",                        Color.Gray,  Modifier.weight(0.8f))
                }
                HorizontalDivider(color=BorderColor.copy(0.5f))
            }
        }
    }
}

@Composable
private fun LogCell(text: String, color: Color, modifier: Modifier) {
    Text(text, color=color, fontSize=11.sp, modifier=modifier)
}