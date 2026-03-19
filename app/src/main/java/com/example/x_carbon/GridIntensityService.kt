package com.example.x_carbon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

// ══════════════════════════════════════════════════════════
//  GRID INTENSITY DATA MODEL
// ══════════════════════════════════════════════════════════

data class GridIntensityResult(
    val intensityGramsPerKwh: Int,      // e.g. 233 g CO₂/kWh
    val intensityKgPerKwh: Double,      // e.g. 0.233 kg CO₂/kWh
    val fossilFuelPercent: Int,         // e.g. 45%
    val renewablePercent: Int,          // e.g. 55%
    val zone: String,                   // e.g. "IN-SO" (India South)
    val countryName: String,
    val source: GridSource,
    val isLive: Boolean                 // false = fallback used
)

enum class GridSource { LIVE_API, LOCATION_FALLBACK, HARDCODED_FALLBACK }

// Regional fallback grid intensities (g CO₂/kWh)
private val REGIONAL_FALLBACKS = mapOf(
    "IN" to 708,   // India
    "US" to 386,   // USA
    "GB" to 233,   // UK
    "DE" to 385,   // Germany
    "FR" to  85,   // France
    "CN" to 581,   // China
    "AU" to 490,   // Australia
    "BR" to 136,   // Brazil
    "JP" to 474,   // Japan
    "CA" to 150,   // Canada
    "DEFAULT" to 350
)

private val COUNTRY_NAMES = mapOf(
    "IN" to "India", "US" to "United States", "GB" to "United Kingdom",
    "DE" to "Germany", "FR" to "France", "CN" to "China",
    "AU" to "Australia", "BR" to "Brazil", "JP" to "Japan",
    "CA" to "Canada"
)

object GridIntensityService {
    private const val TAG = "GridIntensityService"

    // ── ElectricityMaps API ──────
    private const val API_TOKEN = "fYwgMfwYCXaHpqWy8yEh"

    private const val BASE_URL = "https://api.electricitymaps.com/v3"

    suspend fun fetch(context: Context): GridIntensityResult {
        return withContext(Dispatchers.IO) {
            try {
                val location = getLocation(context)
                if (location == null) {
                    Log.w(TAG, "Location not available, using hardcoded fallback.")
                    return@withContext fallback("DEFAULT", GridSource.HARDCODED_FALLBACK)
                }

                Log.d(TAG, "Fetching live intensity for Lat: ${location.latitude}, Lon: ${location.longitude}")
                val liveResult = fetchLiveIntensity(location.latitude, location.longitude)
                
                if (liveResult != null) {
                    Log.i(TAG, "Successfully fetched live data: ${liveResult.intensityGramsPerKwh}g from ${liveResult.countryName}")
                    liveResult
                } else {
                    val countryCode = getCountryCodeFromCoords(location.latitude, location.longitude)
                    Log.w(TAG, "API call failed, using regional fallback for $countryCode")
                    fallback(countryCode, GridSource.LOCATION_FALLBACK)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetch: ${e.message}", e)
                fallback("DEFAULT", GridSource.HARDCODED_FALLBACK)
            }
        }
    }

    private suspend fun getLocation(context: Context): Location? {
        val hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!hasCoarse && !hasFine) {
            Log.e(TAG, "Location permissions not granted")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cancelToken = CancellationTokenSource()
            
            client.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    Log.d(TAG, "Using last known location")
                    cont.resume(lastLoc)
                } else {
                    Log.d(TAG, "Last location null, requesting current location...")
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancelToken.token)
                        .addOnSuccessListener { loc -> 
                            Log.d(TAG, "Current location found: $loc")
                            cont.resume(loc) 
                        }
                        .addOnFailureListener { e -> 
                            Log.e(TAG, "getCurrentLocation failed", e)
                            cont.resume(null) 
                        }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "lastLocation failed", e)
                cont.resume(null)
            }
            cont.invokeOnCancellation { cancelToken.cancel() }
        }
    }

    private fun fetchLiveIntensity(lat: Double, lon: Double): GridIntensityResult? {
        return try {

            val countryCode = getCountryCodeFromCoords(lat, lon)
            val zone = countryCodeToZone(countryCode)

            val urlString = "$BASE_URL/carbon-intensity/latest?zone=$zone"
            Log.d(TAG, "API Request URL: $urlString")

            val url  = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("auth-token", API_TOKEN)
                connectTimeout = 10000
                readTimeout    = 10000
            }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorMsg = conn.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "API Error $responseCode: $errorMsg")
                return null
            }

            val json      = JSONObject(conn.inputStream.bufferedReader().readText())
            val intensity = json.optInt("carbonIntensity", -1)
            if (intensity < 0) return null

            GridIntensityResult(
                intensityGramsPerKwh = intensity,
                intensityKgPerKwh    = intensity / 1000.0,
                fossilFuelPercent    = ((intensity.toDouble() / 900) * 100).toInt().coerceIn(0, 100),
                renewablePercent     = (100 - ((intensity.toDouble() / 900) * 100).toInt()).coerceIn(0, 100),
                zone                 = zone,
                countryName          = COUNTRY_NAMES[countryCode] ?: zone,
                source               = GridSource.LIVE_API,
                isLive               = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Network request or parsing failed", e)
            null
        }
    }

    // Maps country code → ElectricityMaps zone string
    private fun countryCodeToZone(code: String): String = when (code) {
           // India South — adjust to IN-NO / IN-WE / IN-EA if needed
        "IN" -> "IN-SO"
        "US" -> "US-CAL-CISO"
        "GB" -> "GB"
        "DE" -> "DE"
        "FR" -> "FR"
        "CN" -> "CN"
        "AU" -> "AU-NSW"
        "BR" -> "BR-CS"
        "JP" -> "JP-TK"
        "CA" -> "CA-ON"
        else -> "US-CAL-CISO"  // fallback
    }

    private fun getCountryCodeFromCoords(lat: Double, lon: Double): String {
        return when {
            lat in 6.0..37.0 && lon in 68.0..97.0 -> "IN"
            lat in 24.0..49.0 && lon in -125.0..-66.0 -> "US"
            lat in 49.0..61.0 && lon in -8.0..2.0 -> "GB"
            lat in 47.0..55.0 && lon in 6.0..15.0 -> "DE"
            lat in 41.0..51.0 && lon in -5.0..10.0 -> "FR"
            lat in 18.0..53.0 && lon in 73.0..135.0 -> "CN"
            lat in -44.0..-10.0 && lon in 113.0..154.0 -> "AU"
            lat in -34.0..5.0 && lon in -74.0..-34.0 -> "BR"
            lat in 30.0..46.0 && lon in 130.0..145.0 -> "JP"
            lat in 42.0..83.0 && lon in -141.0..-52.0 -> "CA"
            else -> "DEFAULT"
        }
    }

    private fun fallback(countryCode: String, source: GridSource): GridIntensityResult {
        val g = REGIONAL_FALLBACKS[countryCode] ?: REGIONAL_FALLBACKS["DEFAULT"]!!
        val countryKey = if (REGIONAL_FALLBACKS.containsKey(countryCode)) countryCode else "DEFAULT"
        return GridIntensityResult(
            intensityGramsPerKwh = g,
            intensityKgPerKwh = g / 1000.0,
            fossilFuelPercent = ((g.toDouble() / 900) * 100).toInt().coerceIn(0, 100),
            renewablePercent = (100 - ((g.toDouble() / 900) * 100).toInt()).coerceIn(0, 100),
            zone = countryKey,
            countryName = COUNTRY_NAMES[countryKey] ?: "Global average",
            source = source,
            isLive = false
        )
    }
}

@Composable
fun GridIntensityCard(grid: GridIntensityResult) {
    val renewAnim by animateFloatAsState(
        targetValue = grid.renewablePercent / 100f,
        animationSpec = tween(1000),
        label = "renewAnim"
    )

    val gridColor = when {
        grid.intensityGramsPerKwh < 100 -> Color(0xFF00E676)
        grid.intensityGramsPerKwh < 300 -> Color(0xFF66BB6A)
        grid.intensityGramsPerKwh < 500 -> Color(0xFFFFB300)
        else -> Color(0xFFEF5350)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF121418),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, gridColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = gridColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Grid Intensity", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    Text("${grid.countryName} · ${grid.zone}", fontSize = 11.sp, color = Color.Gray)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (grid.isLive) Color(0xFF00E676).copy(0.12f) else Color.Gray.copy(0.12f)
                ) {
                    Text(
                        if (grid.isLive) "Live" else "Fallback",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = if (grid.isLive) Color(0xFF00E676) else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("${grid.intensityGramsPerKwh}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = gridColor, lineHeight = 48.sp)
                Spacer(Modifier.width(6.dp))
                Text("g CO₂\nper kWh", fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
            }

            Spacer(Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFEF5350).copy(0.3f))) {
                Box(modifier = Modifier.fillMaxWidth(renewAnim).height(10.dp).clip(CircleShape).background(gridColor))
            }
            
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${grid.renewablePercent}% renewable", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Text("${grid.fossilFuelPercent}% fossil", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}
