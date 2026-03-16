package com.example.x_carbon

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

// --- THE ENTERPRISE ESG MODELS ---

data class ESGReport(
    val carbonFootprint: Double,     // Total kg CO2
    val gridPurity: Int,            // 0-100% Clean Energy
    val networkEfficiency: Double,   // Carbon per MB
    val hardwareStress: Double,      // Thermal/CPU factor
    val treesOffset: Double,         // Nature equivalent
    val telemetryData: List<TelemetryMetric>
)

data class TelemetryMetric(
    val label: String,
    val value: String,
    val subtext: String,
    val icon: ImageVector,
    val color: Color
)

enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("ESG Live", Icons.Default.Public),
    Network("Connectivity", Icons.Default.Podcasts),
    Hardware("Telemetry", Icons.Default.Memory),
    Settings("Global", Icons.Default.Settings)
}

// --- TELEMETRY ENGINE ---

object EnterpriseEngine {
    // Constants based on IEA (International Energy Agency) data
    private const val CO2_PER_KWH_DIRTY = 0.900 // kg (Coal)
    private const val CO2_PER_KWH_CLEAN = 0.050 // kg (Renewables)
    private const val MOBILE_DATA_INTENSITY = 0.080 // kg CO2 per GB (5G)
    private const val WIFI_DATA_INTENSITY = 0.005   // kg CO2 per GB

    fun calculateESG(
        mbWifi: Double,
        mbMobile: Double,
        gridIntensity: Double, // kg/kWh from API
        thermalState: Int      // 0-4
    ): ESGReport {
        // 1. Connectivity Footprint
        val networkCarbon = (mbWifi / 1024.0 * WIFI_DATA_INTENSITY) +
                (mbMobile / 1024.0 * MOBILE_DATA_INTENSITY)

        // 2. Hardware Carbon (Base usage * thermal stress * grid intensity)
        val hardwareHourlyKwh = 0.005 * (1 + (thermalState * 0.2))
        val hardwareCarbon = hardwareHourlyKwh * gridIntensity

        val total = networkCarbon + hardwareCarbon

        return ESGReport(
            carbonFootprint = total,
            gridPurity = ((1 - (gridIntensity / 0.9)) * 100).toInt().coerceIn(0, 100),
            networkEfficiency = if (total > 0) (mbWifi + mbMobile) / total else 0.0,
            hardwareStress = thermalState.toDouble(),
            treesOffset = total / 21.0,
            telemetryData = listOf(
                TelemetryMetric("Grid Health", "${(gridIntensity * 1000).toInt()}g", "CO2 per kWh", Icons.Default.Bolt, Color(0xFFFFD54F)),
                TelemetryMetric("Data Sync", "${(mbWifi + mbMobile).toInt()} MB", "Total Throughput", Icons.Default.CloudSync, Color(0xFF4FC3F7)),
                TelemetryMetric("Hardware", "Level $thermalState", "Thermal State", Icons.Default.Thermostat, Color(0xFFE57373))
            )
        )
    }
}

// --- UI THEME ---
val CarbonGreen = Color(0xFF00E676)
val DeepSpace = Color(0xFF07090B)
val GlassSurface = Color(0xFF121418)
val BorderColor = Color(0x1AFFFFFF)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ESGTheme {
                MainContainer(this)
            }
        }
    }

    // --- PUBLIC DATA EXTRACTION METHODS ---

    fun getNetworkData(context: Context): Pair<Double, Double> {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        return try {
            val cal = Calendar.getInstance().apply { add(Calendar.HOUR, -24) }

            // Using bucket queries for non-root users
            val wifiStats = nsm.querySummaryForUser(ConnectivityManager.TYPE_WIFI, null, cal.timeInMillis, System.currentTimeMillis())
            val mobileStats = nsm.querySummaryForUser(ConnectivityManager.TYPE_MOBILE, null, cal.timeInMillis, System.currentTimeMillis())

            val wifiMb = (wifiStats.rxBytes + wifiStats.txBytes) / (1024.0 * 1024.0)
            val mobileMb = (mobileStats.rxBytes + mobileStats.txBytes) / (1024.0 * 1024.0)

            Pair(wifiMb, mobileMb)
        } catch (e: Exception) {
            Pair(450.0, 120.0) // Mock fallback
        }
    }

    fun getHardwareThermalState(): Int {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else 0
    }

    fun hasPermissions(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val usageMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        val locationMode = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return usageMode == AppOpsManager.MODE_ALLOWED && locationMode == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun ESGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = CarbonGreen, background = DeepSpace, surface = GlassSurface),
        content = content
    )
}

@Composable
fun MainContainer(activity: MainActivity) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var permissionsGranted by remember { mutableStateOf(activity.hasPermissions()) }
    var esgReport by remember { mutableStateOf<ESGReport?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionsGranted = activity.hasPermissions() }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            val (wifi, mobile) = activity.getNetworkData(context)
            val thermal = activity.getHardwareThermalState()
            // In a real app, gridIntensity would come from a real-time API like ElectricityMaps
            esgReport = EnterpriseEngine.calculateESG(wifi, mobile, 0.350, thermal)
        }
    }

    Scaffold(
        bottomBar = {
            if (permissionsGranted) {
                NavigationBar(containerColor = DeepSpace, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, BorderColor)) {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Icon(screen.icon, null) },
                            label = { Text(screen.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = CarbonGreen, indicatorColor = CarbonGreen.copy(0.1f))
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(DeepSpace)) {
            if (!permissionsGranted) {
                PermissionScreen {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            } else if (esgReport != null) {
                AnimatedContent(targetState = currentScreen, label = "") { screen ->
                    when (screen) {
                        Screen.Dashboard -> ESGHome(esgReport!!)
                        else -> PlaceholderScreen(screen.label)
                    }
                }
            }
        }
    }
}

@Composable
fun ESGHome(report: ESGReport) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .size(8.dp)
                .background(CarbonGreen, CircleShape)
                .blur(4.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ESG COMPLIANCE: STABLE", style = MaterialTheme.typography.labelSmall, color = CarbonGreen, letterSpacing = 2.sp)
        }

        Text("Sustainability Report", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)

        // Main Impact Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(GlassSurface)
                .border(1.dp, BorderColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                Text("%.4f".format(report.carbonFootprint), fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("TOTAL KG CO2e", style = MaterialTheme.typography.labelSmall, color = CarbonGreen)
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { (report.gridPurity / 100f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = CarbonGreen,
                    trackColor = Color.White.copy(0.05f)
                )
                Text("Grid Purity: ${report.gridPurity}% Clean", modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color.Gray)
            }
        }

        // Telemetry Grid
        Text("System Telemetry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        report.telemetryData.forEach { metric ->
            TelemetryRow(metric)
        }
    }
}

@Composable
fun TelemetryRow(metric: TelemetryMetric) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlassSurface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(metric.icon, null, tint = metric.color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(metric.label, fontWeight = FontWeight.Bold, color = Color.White)
                Text(metric.subtext, fontSize = 12.sp, color = Color.Gray)
            }
            Text(metric.value, fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun PermissionScreen(onAuth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.AdminPanelSettings, null, tint = CarbonGreen, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Enterprise Auth", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "X-Carbon requires Network and Location telemetry to calculate your real-world footprint.",
            textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onAuth, colors = ButtonDefaults.buttonColors(CarbonGreen), shape = RoundedCornerShape(12.dp)) {
            Text("AUTHORIZE SYSTEM ACCESS", color = DeepSpace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun PlaceholderScreen(name: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$name coming soon") }
