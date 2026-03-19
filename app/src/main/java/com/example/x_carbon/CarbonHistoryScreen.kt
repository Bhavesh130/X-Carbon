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
    val timestamp:     Long,          // Unix ms
    val co2Kg:         Double,        // total kg CO₂
    val gridIntensity: Double,        // kg/kWh
    val thermalState:  Int,
    val mbWifi:        Double,
    val mbMobile:      Double
) {
    val co2Grams:  Int    get() = (co2Kg * 1000).roundToInt()
    val timeLabel: String get() = SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date(timestamp))
    val dateLabel: String get() = SimpleDateFormat("dd MMM", Locale.getDefault())
        .format(Date(timestamp))
}

// ══════════════════════════════════════════════════════════
//  HISTORY MANAGER — keeps last 48 records (in-memory)
// ══════════════════════════════════════════════════════════

object CarbonHistoryManager {
    private const val MAX_RECORDS = 48

    fun addRecord(
        existing:      List<CarbonHistoryRecord>,
        co2Kg:         Double,
        gridIntensity: Double,
        thermalState:  Int,
        mbWifi:        Double,
        mbMobile:      Double
    ): List<CarbonHistoryRecord> {
        val newRecord = CarbonHistoryRecord(
            timestamp     = System.currentTimeMillis(),
            co2Kg         = co2Kg,
            gridIntensity = gridIntensity,
            thermalState  = thermalState,
            mbWifi        = mbWifi,
            mbMobile      = mbMobile
        )
        return (existing + newRecord).takeLast(MAX_RECORDS)
    }

}

// ══════════════════════════════════════════════════════════
//  HISTORY SCREEN
// ══════════════════════════════════════════════════════════
object CarbonHistoryStorage {
    private const val PREFS_NAME = "carbon_history"
    private const val KEY_RECORDS = "records"
    private const val MAX_STORED  = 48

    // Serialize a record to a single string: "timestamp|co2|grid|thermal|wifi|mobile"
    private fun encode(r: CarbonHistoryRecord) =
        "${r.timestamp}|${r.co2Kg}|${r.gridIntensity}|${r.thermalState}|${r.mbWifi}|${r.mbMobile}"

    private fun decode(s: String): CarbonHistoryRecord? {
        return try {
            val p = s.split("|")
            CarbonHistoryRecord(
                timestamp     = p[0].toLong(),
                co2Kg         = p[1].toDouble(),
                gridIntensity = p[2].toDouble(),
                thermalState  = p[3].toInt(),
                mbWifi        = p[4].toDouble(),
                mbMobile      = p[5].toDouble()
            )
        } catch (e: Exception) { null }
    }

    fun save(context: Context, records: List<CarbonHistoryRecord>) {
        val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = records.takeLast(MAX_STORED).joinToString(";") { encode(it) }
        prefs.edit().putString(KEY_RECORDS, encoded).apply()
    }

    fun load(context: Context): List<CarbonHistoryRecord> {
        val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw     = prefs.getString(KEY_RECORDS, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { decode(it) }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}

@Composable
fun HistoryScreen(records: List<CarbonHistoryRecord>) {
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
            Icon(
                Icons.Default.History,
                null,
                tint     = CarbonGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "CARBON HISTORY",
                style         = MaterialTheme.typography.labelSmall,
                color         = CarbonGreen,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CarbonGreen.copy(alpha = 0.1f)
            ) {
                Text(
                    "${records.size} records",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color    = CarbonGreen
                )
            }
        }

        Text(
            "CO₂ over time",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )

        if (records.isEmpty()) {
            EmptyHistoryCard()
            return@Column
        }

        // ── Summary stats ─────────────────────────────────
        HistorySummaryRow(records)

        // ── Line chart ────────────────────────────────────
        HistoryLineChart(records)

        // ── Bar chart — CO₂ per session ───────────────────
        HistoryBarChart(records)

        // ── Breakdown chart — wifi vs mobile vs hardware ──
        HistoryBreakdownChart(records)

        // ── Record log table ──────────────────────────────
        HistoryLogTable(records)

        Spacer(Modifier.height(8.dp))
    }
}

// ══════════════════════════════════════════════════════════
//  EMPTY STATE
// ══════════════════════════════════════════════════════════

@Composable
private fun EmptyHistoryCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier            = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.History,
                null,
                tint     = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No history yet",
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                fontSize   = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant permissions on the Dashboard and your CO₂ history will appear here automatically.",
                color     = Color.Gray,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SUMMARY ROW
// ══════════════════════════════════════════════════════════

@Composable
private fun HistorySummaryRow(records: List<CarbonHistoryRecord>) {
    val avg     = records.map { it.co2Kg }.average()
    val max     = records.maxOf { it.co2Kg }
    val min     = records.minOf { it.co2Kg }
    val trend   = if (records.size >= 2)
        records.last().co2Kg - records[records.size - 2].co2Kg
    else 0.0
    val trendUp = trend > 0

    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryTile("Average",  "${(avg  * 1000).roundToInt()}g", Color(0xFF4FC3F7), Modifier.weight(1f))
        SummaryTile("Peak",     "${(max  * 1000).roundToInt()}g", Color(0xFFEF5350), Modifier.weight(1f))
        SummaryTile("Best",     "${(min  * 1000).roundToInt()}g", CarbonGreen,       Modifier.weight(1f))
        SummaryTile(
            label    = "Trend",
            value    = "${if (trendUp) "+" else ""}${(trend * 1000).roundToInt()}g",
            color    = if (trendUp) Color(0xFFEF5350) else CarbonGreen,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryTile(
    label:    String,
    value:    String,
    color:    Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color    = GlassSurface,
        shape    = RoundedCornerShape(16.dp),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontWeight = FontWeight.Black,
                color      = color,
                fontSize   = 18.sp
            )
            Text(
                label,
                fontSize = 10.sp,
                color    = Color.Gray
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  LINE CHART — CO₂ over time (custom Canvas draw)
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryLineChart(records: List<CarbonHistoryRecord>) {
    val animProgress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "lineAnim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "CO₂ trend over time",
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                fontSize   = 14.sp
            )
            Text(
                "kg CO₂ per reading",
                fontSize = 11.sp,
                color    = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .drawBehind {
                        drawLineChart(
                            records  = records,
                            progress = animProgress,
                            size     = this.size
                        )
                    }
            )

            // X-axis labels
            Spacer(Modifier.height(8.dp))
            if (records.isNotEmpty()) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(records.first().timeLabel, fontSize = 9.sp, color = Color.Gray)
                    if (records.size > 2) {
                        Text(records[records.size / 2].timeLabel, fontSize = 9.sp, color = Color.Gray)
                    }
                    Text(records.last().timeLabel, fontSize = 9.sp, color = Color.Gray)
                }
            }
        }
    }
}

private fun DrawScope.drawLineChart(
    records:  List<CarbonHistoryRecord>,
    progress: Float,
    size:     androidx.compose.ui.geometry.Size
) {
    if (records.size < 2) return

    val values  = records.map { it.co2Kg.toFloat() }
    val maxVal  = values.max().coerceAtLeast(0.001f)
    val minVal  = values.min()
    val range   = (maxVal - minVal).coerceAtLeast(0.0001f)
    val w       = size.width
    val h       = size.height
    val padTop  = 12f
    val padBot  = 8f
    val usableH = h - padTop - padBot

    // Grid lines
    repeat(4) { i ->
        val y = padTop + usableH * (i / 3f)
        drawLine(
            color       = Color.White.copy(alpha = 0.05f),
            start       = Offset(0f, y),
            end         = Offset(w, y),
            strokeWidth = 1f
        )
    }

    // Points to draw (animated — only draw up to progress * size)
    val visibleCount = (records.size * progress).roundToInt().coerceAtLeast(2)
    val visibleRecs  = records.take(visibleCount)

    // Build path points
    val points = visibleRecs.mapIndexed { i, rec ->
        val x = if (visibleRecs.size > 1) w * i / (visibleRecs.size - 1).toFloat() else w / 2f
        val y = padTop + usableH * (1f - (rec.co2Kg.toFloat() - minVal) / range)
        Offset(x, y)
    }

    // Filled gradient area
    if (points.size >= 2) {
        val path = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(
            path  = path,
            brush = Brush.verticalGradient(
                colors      = listOf(
                    Color(0xFF00E676).copy(alpha = 0.25f),
                    Color(0xFF00E676).copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY   = h
            )
        )

        // Line
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path        = linePath,
            color       = Color(0xFF00E676),
            style       = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // Dots
    points.forEachIndexed { i, pt ->
        val isLast = i == points.lastIndex
        drawCircle(
            color  = if (isLast) Color(0xFF00E676) else Color(0xFF00E676).copy(0.5f),
            radius = if (isLast) 6f else 3f,
            center = pt
        )
        if (isLast) {
            drawCircle(
                color  = Color(0xFF07090B),
                radius = 3f,
                center = pt
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  BAR CHART — CO₂ per session
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryBarChart(records: List<CarbonHistoryRecord>) {
    val display = records.takeLast(12)
    val maxVal  = display.maxOf { it.co2Kg }.coerceAtLeast(0.001)
    val animProgress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "barAnim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Per-session CO₂",
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                fontSize   = 14.sp
            )
            Text(
                "Last ${display.size} readings",
                fontSize = 11.sp,
                color    = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment   = Alignment.Bottom
            ) {
                display.forEach { rec ->
                    val heightFraction = ((rec.co2Kg / maxVal) * animProgress).toFloat()
                    val barColor = when {
                        rec.co2Kg < 0.005 -> CarbonGreen
                        rec.co2Kg < 0.015 -> Color(0xFFFFB300)
                        rec.co2Kg < 0.030 -> Color(0xFFFF7043)
                        else              -> Color(0xFFEF5350)
                    }

                    Column(
                        modifier            = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(heightFraction.coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(barColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            rec.timeLabel,
                            fontSize  = 8.sp,
                            color     = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  BREAKDOWN CHART — Wi-Fi vs Mobile vs Hardware
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryBreakdownChart(records: List<CarbonHistoryRecord>) {
    val display  = records.takeLast(8)
    val wifiAvg  = display.map { it.mbWifi   / 1024.0 * 0.005 }.average()
    val mobAvg   = display.map { it.mbMobile / 1024.0 * 0.080 }.average()
    val hwAvg    = display.map { it.gridIntensity * 0.005 * (1 + it.thermalState * 0.2) }.average()
    val total    = (wifiAvg + mobAvg + hwAvg).coerceAtLeast(0.0001)

    val segments = listOf(
        Triple("Wi-Fi",    wifiAvg, Color(0xFF4FC3F7)),
        Triple("Mobile",   mobAvg,  Color(0xFF7E57C2)),
        Triple("Hardware", hwAvg,   Color(0xFFE57373)),
    )

    val animProgress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1000),
        label         = "breakdownAnim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Average CO₂ source breakdown",
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                fontSize   = 14.sp
            )
            Text(
                "Last ${display.size} sessions",
                fontSize = 11.sp,
                color    = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Stacked bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
            ) {
                segments.forEach { (_, value, color) ->
                    val fraction = ((value / total) * animProgress).toFloat()
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(fraction.coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Legend with values
            segments.forEach { (label, value, color) ->
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White,  fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${(value * 1000 / total * 100).roundToInt()}%",
                        color      = color,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${"%.4f".format(value)} kg avg",
                        color    = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  LOG TABLE — scrollable list of all records
// ══════════════════════════════════════════════════════════

@Composable
private fun HistoryLogTable(records: List<CarbonHistoryRecord>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = GlassSurface,
        shape    = RoundedCornerShape(24.dp),
        border   = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Session log",
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                fontSize   = 14.sp,
                modifier   = Modifier.padding(bottom = 12.dp)
            )

            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                LogCell("Time",      Color.Gray, Modifier.weight(1.2f))
                LogCell("CO₂ (g)",   Color.Gray, Modifier.weight(1f))
                LogCell("Grid",      Color.Gray, Modifier.weight(1f))
                LogCell("Thermal",   Color.Gray, Modifier.weight(0.8f))
            }

            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))

            // Rows — latest first
            records.reversed().forEach { rec ->
                val rowColor = when {
                    rec.co2Kg < 0.005 -> CarbonGreen
                    rec.co2Kg < 0.015 -> Color(0xFFFFB300)
                    rec.co2Kg < 0.030 -> Color(0xFFFF7043)
                    else              -> Color(0xFFEF5350)
                }
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogCell("${rec.dateLabel} ${rec.timeLabel}", Color.Gray,  Modifier.weight(1.2f))
                    LogCell("${rec.co2Grams}g",                  rowColor,    Modifier.weight(1f))
                    LogCell("${(rec.gridIntensity * 1000).roundToInt()}g/kWh", Color.Gray, Modifier.weight(1f))
                    LogCell("Lvl ${rec.thermalState}",           Color.Gray,  Modifier.weight(0.8f))
                }
                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun LogCell(text: String, color: Color, modifier: Modifier) {
    Text(
        text     = text,
        color    = color,
        fontSize = 11.sp,
        modifier = modifier
    )
}