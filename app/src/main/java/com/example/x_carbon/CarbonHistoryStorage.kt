package com.example.x_carbon

import android.content.Context
import java.util.*

object CarbonHistoryStorage {

    private const val PREFS_NAME = "carbon_history"
    private const val KEY_RECORDS = "records"

    // Encode a single record to a pipe‑separated string
    private fun encode(record: CarbonHistoryRecord): String {
        return "${record.timestamp}|${record.co2Kg}|${record.gridIntensity}|${record.thermalState}|${record.mbWifi}|${record.mbMobile}"
    }

    // Decode a pipe‑separated string back to a CarbonHistoryRecord
    private fun decode(encoded: String): CarbonHistoryRecord? {
        return try {
            val parts = encoded.split("|")
            if (parts.size != 6) return null
            CarbonHistoryRecord(
                timestamp = parts[0].toLong(),
                co2Kg = parts[1].toDouble(),
                gridIntensity = parts[2].toDouble(),
                thermalState = parts[3].toInt(),
                mbWifi = parts[4].toDouble(),
                mbMobile = parts[5].toDouble()
            )
        } catch (e: Exception) {
            null
        }
    }

    // Save a list of records to SharedPreferences (keeps only the last 48)
    fun save(context: Context, records: List<CarbonHistoryRecord>) {
        val toStore = records.takeLast(48)
        val encoded = toStore.joinToString(";") { encode(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS, encoded)
            .apply()
    }

    // Load records from SharedPreferences
    fun load(context: Context): List<CarbonHistoryRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { decode(it) }
            .sortedByDescending { it.timestamp }
    }

    // Optional: clear all stored records
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECORDS)
            .apply()
    }
}