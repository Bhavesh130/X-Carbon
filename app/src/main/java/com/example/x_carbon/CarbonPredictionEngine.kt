package com.example.x_carbon

import android.util.Log
import java.text.SimpleDateFormat
import kotlin.math.max
import kotlin.math.sqrt
import java.util.*

data class CarbonPrediction(
    val dayLabel: String,
    val predictedCo2Kg: Double,
    val lowerBound: Double,
    val upperBound: Double,
    val trend: PredictionTrend
)

enum class PredictionTrend { IMPROVING, STABLE, WORSENING }

object CarbonPredictionEngine {

    private const val MIN_RECORDS = 15   // Minimum records needed for prediction
    private const val FORECAST_STEPS = 7 // Number of future predictions (hours or sessions)

    // ── Main predict function (works with raw records, not daily aggregation) ──
    suspend fun predictWithCloudData(localRecords: List<CarbonHistoryRecord>): List<CarbonPrediction> {
        val cloudRecords = try {
            AzureDbService.loadRecords()
        } catch (e: Exception) {
            Log.w("PredictionEngine", "Cloud fetch failed, using local only")
            emptyList()
        }

        val allRecords = (cloudRecords + localRecords)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }

        Log.d("PredictionEngine", "Total records: ${allRecords.size}")
        return predict(allRecords)
    }

    // ── Core prediction using raw records (ordered by time) ──
    fun predict(records: List<CarbonHistoryRecord>): List<CarbonPrediction> {
        if (records.size < MIN_RECORDS) {
            Log.d("PredictionEngine", "Need at least $MIN_RECORDS records, have ${records.size}")
            return emptyList()
        }

        // Use the most recent MIN_RECORDS records
        val training = records
        val xValues = training.indices.map { it.toDouble() }  // 0, 1, 2, ..., N-1
        val yValues = training.map { it.co2Kg }

        val n = xValues.size.toDouble()
        val sumX = xValues.sum()
        val sumY = yValues.sum()
        val sumXY = xValues.zip(yValues).sumOf { (x, y) -> x * y }
        val sumX2 = xValues.sumOf { it * it }
        val denom = n * sumX2 - sumX * sumX

        if (denom == 0.0) return emptyList()

        val m = (n * sumXY - sumX * sumY) / denom   // slope
        val b = (sumY - m * sumX) / n               // intercept

        // R² for confidence
        val yMean = sumY / n
        val ssTot = yValues.sumOf { (it - yMean) * (it - yMean) }
        val fittedY = xValues.map { m * it + b }
        val ssRes = yValues.zip(fittedY).sumOf { (y, yHat) -> (y - yHat) * (y - yHat) }
        val r2 = if (ssTot > 0) 1.0 - ssRes / ssTot else 0.0

        // Standard error
        val mse = ssRes / n.coerceAtLeast(1.0)
        val stdErr = sqrt(mse)

        Log.d("PredictionEngine", "Model: slope=$m, intercept=$b, R²=${"%.3f".format(r2)}")

        val lastX = xValues.last()
        val trend = when {
            m < -0.00001 -> PredictionTrend.IMPROVING
            m > 0.00001  -> PredictionTrend.WORSENING
            else         -> PredictionTrend.STABLE
        }

        // Generate future predictions (next 7 steps/sessions)
        val labels = getStepLabels(FORECAST_STEPS)
        return (1..FORECAST_STEPS).map { step ->
            val x = lastX + step
            val predicted = max(0.0, m * x + b)
            val margin = 1.96 * stdErr * (1.0 + step * 0.05)
            CarbonPrediction(
                dayLabel = labels[step - 1],
                predictedCo2Kg = predicted,
                lowerBound = max(0.0, predicted - margin),
                upperBound = predicted + margin,
                trend = trend
            )
        }
    }

    private fun getStepLabels(count: Int): List<String> {
        val now = Calendar.getInstance()
        return (1..count).map { offset ->
            val cal = now.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, offset)
            if (offset == 1) "Next"
            else SimpleDateFormat("E", Locale.getDefault()).format(cal.time)
        }
    }
}