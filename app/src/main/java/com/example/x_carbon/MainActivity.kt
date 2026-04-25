package com.example.x_carbon

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log   // ✅ Add this import
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

// ── ESG models ────────────────────────────────────────────

data class ESGReport(
    val carbonFootprint:  Double,
    val gridPurity:       Int,
    val networkEfficiency:Double,
    val hardwareStress:   Double,
    val treesOffset:      Double,
    val telemetryData:    List<TelemetryMetric>
)

data class TelemetryMetric(
    val label:   String,
    val value:   String,
    val subtext: String,
    val icon:    ImageVector,
    val color:   Color
)

enum class Screen(val label: String, val icon: ImageVector) {
    Dashboard("ESG Live",   Icons.Default.Public),
    XAI      ("Explain AI", Icons.Default.Psychology),
    Chat     ("AI Chat",    Icons.AutoMirrored.Filled.Chat),
    History  ("History",    Icons.Default.History)
}

val CarbonGreen  = Color(0xFF00E676)
val DeepSpace    = Color(0xFF07090B)
val GlassSurface = Color(0xFF121418)
val BorderColor  = Color(0x1AFFFFFF)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ESGTheme { MainContainer(this) } }
    }

    fun getNetworkData(context: Context): Pair<Double, Double> {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        return try {
            val cal         = Calendar.getInstance().apply { add(Calendar.HOUR, -24) }
            val wifiStats   = nsm.querySummaryForUser(ConnectivityManager.TYPE_WIFI,   null, cal.timeInMillis, System.currentTimeMillis())
            val mobileStats = nsm.querySummaryForUser(ConnectivityManager.TYPE_MOBILE, null, cal.timeInMillis, System.currentTimeMillis())
            Pair(
                (wifiStats.rxBytes   + wifiStats.txBytes)   / (1024.0 * 1024.0),
                (mobileStats.rxBytes + mobileStats.txBytes) / (1024.0 * 1024.0)
            )
        } catch (e: Exception) { Pair(450.0, 120.0) }
    }

    fun hasPermissions(): Boolean {
        val appOps    = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val usageMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        else @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        val locationMode = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return usageMode == AppOpsManager.MODE_ALLOWED && locationMode == PackageManager.PERMISSION_GRANTED
    }
}

// ══════════════════════════════════════════════════════════
//  PARTICLE BACKGROUND (same as before – unchanged)
// ══════════════════════════════════════════════════════════

data class Particle(
    val x: Float, val y: Float, val radius: Float,
    val speedX: Float, val speedY: Float,
    val alpha: Float, val color: Color
)


@Composable
fun AnimatedParticleBackground() {
    val particles = remember {
        List(35) {
            Particle(
                x      = (Math.random() * 1000).toFloat(),
                y      = (Math.random() * 2000).toFloat(),
                radius = (Math.random() * 3 + 1).toFloat(),
                speedX = ((Math.random() - 0.5) * 0.4).toFloat(),
                speedY = ((Math.random() - 0.5) * 0.4).toFloat(),
                alpha  = (Math.random() * 0.4 + 0.05).toFloat(),
                color  = when ((Math.random() * 3).toInt()) {
                    0    -> Color(0xFF00E676)
                    1    -> Color(0xFF4FC3F7)
                    else -> Color(0xFF7E57C2)
                }
            )
        }
    }
    val time  by produceState(0f) { while (true) { delay(16); value += 0.016f } }
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        0.85f, 1.15f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pa"
    )
    Box(modifier = Modifier.fillMaxSize().drawBehind {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF00E676).copy(0.04f * pulse), Color.Transparent),
                Offset(size.width * 0.2f, size.height * 0.15f), size.width * 0.7f),
            size.width * 0.7f, Offset(size.width * 0.2f, size.height * 0.15f)
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF7E57C2).copy(0.03f * pulse), Color.Transparent),
                Offset(size.width * 0.8f, size.height * 0.6f), size.width * 0.6f),
            size.width * 0.6f, Offset(size.width * 0.8f, size.height * 0.6f)
        )
        particles.forEach { p ->
            val x = ((p.x + p.speedX * time * 60) % size.width  + size.width)  % size.width
            val y = ((p.y + p.speedY * time * 60) % size.height + size.height) % size.height
            drawCircle(p.color.copy(p.alpha), p.radius, Offset(x, y))
        }
    })
}

// ══════════════════════════════════════════════════════════
//  SPLASH SCREEN (unchanged)
// ══════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(300); phase = 1
        delay(600); phase = 2
        delay(800); phase = 3
        delay(700); onFinished()
    }

    val logoAlpha    by animateFloatAsState(if (phase >= 1) 1f else 0f, tween(500), label = "la")
    val logoScale    by animateFloatAsState(if (phase >= 1) 1f else 0.6f, tween(600, easing = FastOutSlowInEasing), label = "ls")
    val taglineAlpha by animateFloatAsState(if (phase >= 2) 1f else 0f, tween(400), label = "ta")
    val exitAlpha    by animateFloatAsState(if (phase >= 3) 0f else 1f, tween(600), label = "ea")
    val scanProgress by animateFloatAsState(if (phase >= 2) 1f else 0f, tween(900, easing = LinearEasing), label = "sp")
    val ringScale    by rememberInfiniteTransition(label = "r").animateFloat(
        0.9f, 1.1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "rs"
    )

    Box(Modifier.fillMaxSize().background(DeepSpace).graphicsLayer(alpha = exitAlpha), Alignment.Center) {
        AnimatedParticleBackground()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(140.dp).scale(ringScale)
                        .background(Brush.radialGradient(listOf(CarbonGreen.copy(0.08f), Color.Transparent)), CircleShape)
                        .border(1.dp, CarbonGreen.copy(0.2f), CircleShape)
                )
                Box(
                    Modifier.size(96.dp)
                        .graphicsLayer(alpha = logoAlpha, scaleX = logoScale, scaleY = logoScale)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, CarbonGreen.copy(0.5f), CircleShape),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.Public, null, tint = CarbonGreen, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.graphicsLayer(alpha = logoAlpha).drawBehind {
                if (scanProgress > 0f && scanProgress < 1f) {
                    val y = size.height * scanProgress
                    drawLine(CarbonGreen.copy(0.8f), Offset(0f, y), Offset(size.width, y), 2f)
                    drawRect(Brush.verticalGradient(listOf(CarbonGreen.copy(0.15f), Color.Transparent), y, y + 40f))
                }
            }) {
                Text("X-CARBON", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 6.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("Enterprise ESG Intelligence", fontSize = 13.sp, color = CarbonGreen.copy(taglineAlpha), letterSpacing = 3.sp)
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.graphicsLayer(alpha = taglineAlpha)) {
                repeat(3) { i ->
                    val da by rememberInfiniteTransition(label = "d$i").animateFloat(
                        0.2f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse), label = "da$i"
                    )
                    Box(Modifier.size(6.dp).background(CarbonGreen.copy(da), CircleShape))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  THEME
// ══════════════════════════════════════════════════════════

@Composable
fun ESGTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = CarbonGreen, background = DeepSpace, surface = GlassSurface),
    content     = content
)

// ══════════════════════════════════════════════════════════
//  SCROLL REVEAL
// ══════════════════════════════════════════════════════════

@Composable
fun ScrollRevealItem(delayMs: Int = 0, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(380)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { 50 }
    ) { content() }
}

// ══════════════════════════════════════════════════════════
//  MAIN CONTAINER (FIXED: Azure load merged)
// ══════════════════════════════════════════════════════════

@Composable
fun MainContainer(activity: MainActivity) {
    val context = LocalContext.current

    var showSplash         by remember { mutableStateOf(true) }
    var currentScreen      by remember { mutableStateOf(Screen.Dashboard) }
    var permissionsGranted by remember { mutableStateOf(activity.hasPermissions()) }
    var esgReport          by remember { mutableStateOf<ESGReport?>(null) }
    var xaiReport          by remember { mutableStateOf<XAIReport?>(null) }
    var historyRecords     by remember { mutableStateOf<List<CarbonHistoryRecord>>(emptyList()) }
    var thresholds         by remember { mutableStateOf(DEFAULT_THRESHOLDS) }
    var gridResult         by remember { mutableStateOf<GridIntensityResult?>(null) }
    var prevBatteryPct     by remember { mutableIntStateOf(-1) }
    var sessionStartMs     by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var predictions        by remember { mutableStateOf<List<CarbonPrediction>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionsGranted = activity.hasPermissions() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.createAll(context)
    }

    // ── Load Azure records once when permissions are granted ──────────────
    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect

        // 1. Load from local storage immediately (so UI shows something)
        var localRecords = CarbonHistoryStorage.load(context)
        historyRecords = localRecords.sortedByDescending { it.timestamp }.takeLast(48)
        predictions = CarbonPredictionEngine.predict(historyRecords)

        // 2. Try to load from Azure in background and merge
        launch(Dispatchers.IO) {
            try {
                val azureRecords = AzureDbService.loadRecords()
                if (azureRecords.isNotEmpty()) {
                    val merged = (localRecords + azureRecords)
                        .distinctBy { it.timestamp }
                        .sortedByDescending { it.timestamp }
                        .takeLast(48)
                    withContext(Dispatchers.Main) {
                        historyRecords = merged
                        predictions = CarbonPredictionEngine.predict(merged)
                        CarbonHistoryStorage.save(context, merged) // update local cache
                    }
                }
            } catch (e: Exception) {
                Log.e("AzureSync", "Failed to load from Azure", e)
            }
        }
    }

    // ── Initial calculation ────────────────────────────────
    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect

        // Use existing history if already loaded from Azure
        val currentHistory = historyRecords
        if (currentHistory.isEmpty()) {
            // Fallback to local storage only if Azure didn't provide anything
            historyRecords = CarbonHistoryStorage.load(context)
        }

        val (wifi, mobile) = activity.getNetworkData(context)
        val fetchedGrid    = GridIntensityService.fetch(context)
        gridResult         = fetchedGrid
        val sessionHours   = (System.currentTimeMillis() - sessionStartMs) / 3_600_000.0

        val result = CarbonCalculationEngine.calculate(
            context        = context,
            wifiMb         = wifi,
            mobileMb       = mobile,
            sessionHours   = sessionHours.coerceAtLeast(0.001),
            gridIntensity  = fetchedGrid.intensityKgPerKwh,
            gridRegion     = fetchedGrid.countryName,
            prevBatteryPct = prevBatteryPct.takeIf { it > 0 },
            deviceAgeYears = 1.0,
            sessionStartMs = sessionStartMs
        )
        prevBatteryPct = result.readings.batteryPct

        val esg = result.toESGReport()
        esgReport = esg
        xaiReport = XAIEngine.explain(result)

        val initialRecord = CarbonHistoryRecord(
            timestamp     = System.currentTimeMillis(),
            co2Kg         = esg.carbonFootprint,
            gridIntensity = fetchedGrid.intensityKgPerKwh,
            thermalState  = result.readings.thermalState,
            mbWifi        = wifi,
            mbMobile      = mobile
        )
        historyRecords = (historyRecords + initialRecord).takeLast(48)
        predictions = CarbonPredictionEngine.predict(historyRecords)

// Save locally first (always works)
        CarbonHistoryStorage.save(context, historyRecords)

// Then try Azure in background (ignore failures)
        launch(Dispatchers.IO) {
            try {
                AzureDbService.saveRecord(initialRecord)
            } catch (e: Exception) {
                Log.e("AzureSave", "Failed to save to Azure", e)
            }
        }
        CarbonNotificationService.checkAndNotify(context, esg.carbonFootprint, thresholds)
    }

    // ── Periodic update every 5 seconds ───────────────────
    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        while (true) {
            delay(5000)

            val (wifi, mobile) = activity.getNetworkData(context)
            val sessionHours   = (System.currentTimeMillis() - sessionStartMs) / 3_600_000.0

            val result = CarbonCalculationEngine.calculate(
                context        = context,
                wifiMb         = wifi,
                mobileMb       = mobile,
                sessionHours   = sessionHours.coerceAtLeast(0.001),
                gridIntensity  = gridResult?.intensityKgPerKwh ?: 0.35,
                gridRegion     = gridResult?.countryName ?: "Global",
                prevBatteryPct = prevBatteryPct.takeIf { it > 0 },
                deviceAgeYears = 1.0,
                sessionStartMs = sessionStartMs
            )
            prevBatteryPct = result.readings.batteryPct

            val esg = result.toESGReport()
            esgReport = esg
            xaiReport = XAIEngine.explain(result)

            val newRecord = CarbonHistoryRecord(
                timestamp     = System.currentTimeMillis(),
                co2Kg         = esg.carbonFootprint,
                gridIntensity = result.readings.gridIntensityKgPerKwh,
                thermalState  = result.readings.thermalState,
                mbWifi        = wifi,
                mbMobile      = mobile
            )

            historyRecords = (historyRecords + newRecord).takeLast(48)
            predictions = CarbonPredictionEngine.predict(historyRecords)

            CarbonHistoryStorage.save(context, historyRecords)   // ✅ local always

            launch(Dispatchers.IO) {
                try {
                    AzureDbService.saveRecord(newRecord)
                } catch (e: Exception) {
                    // ignore – cloud may be down
                }
            }

            CarbonNotificationService.checkAndNotify(context, esg.carbonFootprint, thresholds)
        }
    }

    if (showSplash) { SplashScreen { showSplash = false }; return }

    Scaffold(
        bottomBar = {
            if (permissionsGranted) {
                NavigationBar(
                    containerColor = DeepSpace,
                    tonalElevation = 0.dp,
                    modifier       = Modifier.border(1.dp, BorderColor)
                ) {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick  = { currentScreen = screen },
                            icon     = { Icon(screen.icon, null) },
                            label    = { Text(screen.label, fontSize = 10.sp) },
                            colors   = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (screen == Screen.XAI || screen == Screen.Chat)
                                    Color(0xFF7E57C2) else CarbonGreen,
                                indicatorColor    = if (screen == Screen.XAI || screen == Screen.Chat)
                                    Color(0xFF7E57C2).copy(0.1f) else CarbonGreen.copy(0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(DeepSpace)
        ) {
            AnimatedParticleBackground()
            if (!permissionsGranted) {
                AnimatedPermissionScreen {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            } else {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it * dir } + fadeIn(tween(320)))
                            .togetherWith(slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it * dir } + fadeOut(tween(220)))
                    },
                    label = "screen"
                ) { screen ->
                    when (screen) {
                        Screen.Dashboard -> if (esgReport != null) ESGHome(esgReport!!, gridResult) else AnimatedLoadingScreen()
                        Screen.XAI       -> if (xaiReport != null) XAIScreen(xaiReport!!) else AnimatedLoadingScreen()
                        Screen.History   -> HistoryScreen(historyRecords, predictions)
                        Screen.Chat      -> ChatScreen()
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ESG HOME
// ══════════════════════════════════════════════════════════

@Composable
fun ESGHome(report: ESGReport, gridResult: GridIntensityResult?) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScrollRevealItem(0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(Modifier.size(7.dp).background(CarbonGreen, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("LIVE", fontSize = 11.sp, color = CarbonGreen,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("· updates every 5s", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                    Text(gridResult?.zone ?: "—", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        item { ScrollRevealItem(80)  { AnimatedCO2Card(report, gridResult) } }

        if (gridResult != null) {
            item { ScrollRevealItem(140) { CompactGridIntensityCard(gridResult) } }
        }

        item {
            ScrollRevealItem(180) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("System Telemetry", fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("${report.telemetryData.size} metrics", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        itemsIndexed(report.telemetryData) { i, metric ->
            ScrollRevealItem(i * 50) { TelemetryCard(metric) }
        }

        item {
            ScrollRevealItem(0) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color  = Color(0xFF7E57C2).copy(0.08f),
                    shape  = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(0.25f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).background(Color(0xFF7E57C2).copy(0.15f), CircleShape),
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.Psychology, null,
                                tint = Color(0xFF7E57C2), modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Why this score?", fontWeight = FontWeight.Bold,
                                color = Color.White, fontSize = 13.sp)
                            Text("Feature attribution · counterfactuals · model formula",
                                color = Color.Gray, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null,
                            tint = Color(0xFF7E57C2), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ══════════════════════════════════════════════════════════
//  CHAT SCREEN
// ══════════════════════════════════════════════════════════


// ══════════════════════════════════════════════════════════
//  XAI SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun XAIScreen(report: XAIReport?) {
    if (report == null) {
        AnimatedLoadingScreen()
        return
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp), // Removed top padding here to fix indentation
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(20.dp)) // Managed spacing via explicit Spacer
        // Header with model info
        XAIHeader(report)

        // Impact verdict (headline + detailed reason + eco tip)
        ImpactVerdictCard(report)

        // Carbon budget
        CarbonBudgetCard(report.carbonBudget)

        // Feature attribution (Wi‑Fi, mobile, hardware, grid)
        AttributionSection(report.attributions)

        // Counterfactuals (if any)
        if (report.counterfactuals.isNotEmpty()) {
            CounterfactualSection(report.counterfactuals)
        }

        // Model sources (just a small footer)
        ModelSourcesCard(report)
        Spacer(Modifier.height(20.dp))
    }
}
@Composable
private fun XAIHeader(report: XAIReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Psychology, null, tint = CarbonGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "EXPLAINABLE AI  ·  ${report.modelVersion}",
            fontSize = 10.sp,
            color = Color.Gray,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.weight(1f))
        if (report.aiPowered) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF7E57C2).copy(0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF7E57C2), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Powered", fontSize = 10.sp, color = Color(0xFF7E57C2))
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CarbonGreen.copy(0.1f)
        ) {
            Text(
                "${report.confidenceScore}% confidence",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = CarbonGreen
            )
        }
    }
}

@Composable
private fun ImpactVerdictCard(report: XAIReport) {
    val impactColor = report.impactLevel.color
    val co2Grams = (report.esg.carbonFootprint * 1000).roundToInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlassSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, impactColor.copy(0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).background(impactColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    report.impactLevel.label.uppercase(),
                    fontSize = 11.sp,
                    color = impactColor,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${co2Grams}g CO₂e",
                    fontWeight = FontWeight.Black,
                    color = impactColor,
                    fontSize = 16.sp
                )
            }
            Text(
                report.headline,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(Modifier.height(14.dp))
            ExplainSection(Icons.Default.Insights, "What drove this score", impactColor) {
                Text(
                    report.detailedReason,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = impactColor.copy(0.07f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, impactColor.copy(0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (report.aiPowered) Icons.Default.AutoAwesome else Icons.Default.TipsAndUpdates,
                        null,
                        tint = impactColor,
                        modifier = Modifier.size(14.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        report.ecoTip,
                        color = impactColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplainSection(icon: ImageVector, title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
fun CarbonBudgetCard(budget: CarbonBudget) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlassSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, tint = CarbonGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Daily Carbon Budget", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("${budget.percentUsed.roundToInt()}% used", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(0.1f))) {
                Box(
                    Modifier.fillMaxWidth((budget.percentUsed / 100.0).toFloat().coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                budget.percentUsed > 80 -> Color(0xFFEF5350)
                                budget.percentUsed > 50 -> Color(0xFFFFB300)
                                else -> CarbonGreen
                            }
                        )
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Used today: ${"%.4f".format(budget.usedKg)} kg", fontSize = 12.sp, color = Color.Gray)
                Text("Remaining: ${"%.4f".format(budget.remainingKg)} kg", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AttributionSection(attributions: List<FeatureAttribution>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        XAISectionLabel("Feature Attribution", "SHAP‑style marginal contributions", Icons.Default.BarChart, Color(0xFF4FC3F7))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GlassSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                attributions.forEach { AttributionBar(it) }
            }
        }
    }
}

@Composable
fun AttributionBar(attr: FeatureAttribution) {
    var expanded by remember { mutableStateOf(false) }
    val animPct by animateFloatAsState(
        targetValue = (attr.percentShare / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ab"
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(8.dp).background(attr.color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(attr.featureName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(attr.rawValue, color = Color.Gray, fontSize = 11.sp)
            }
            Text("${attr.percentShare.roundToInt()}%", fontWeight = FontWeight.Black, color = attr.color, fontSize = 15.sp)
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(20.dp)) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(0.05f))) {
            Box(
                Modifier.fillMaxWidth(animPct).height(6.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(attr.color.copy(0.6f), attr.color)))
            )
        }
        AnimatedVisibility(expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = attr.color.copy(0.07f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = attr.color, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(attr.insight, fontSize = 11.sp, color = Color.White.copy(0.8f), lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun CounterfactualSection(counterfactuals: List<Counterfactual>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        XAISectionLabel("Counterfactual Analysis", "What if you changed one thing?", Icons.Default.AutoFixHigh, Color(0xFF81C784))
        counterfactuals.forEach { CounterfactualCard(it) }
    }
}

@Composable
fun CounterfactualCard(cf: Counterfactual) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlassSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, cf.newImpactLevel.color.copy(0.25f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(38.dp).background(cf.newImpactLevel.color.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoFixHigh, null, tint = cf.newImpactLevel.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cf.featureName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("${cf.currentValue} → ${cf.targetValue}", color = cf.newImpactLevel.color, fontSize = 11.sp)
                Text(cf.explanation, color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
fun ModelSourcesCard(report: XAIReport) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CarbonGreen.copy(0.05f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CarbonGreen.copy(0.15f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, null, tint = CarbonGreen, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Model · ${report.modelVersion}", color = CarbonGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun XAISectionLabel(title: String, subtitle: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).background(color.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }
}




// ══════════════════════════════════════════════════════════
//  COMPACT GRID CARD
// ══════════════════════════════════════════════════════════

@Composable
fun CompactGridIntensityCard(grid: GridIntensityResult) {
    val gridColor = when {
        grid.intensityGramsPerKwh < 100 -> Color(0xFF00E676)
        grid.intensityGramsPerKwh < 300 -> Color(0xFF66BB6A)
        grid.intensityGramsPerKwh < 500 -> Color(0xFFFFB300)
        else                            -> Color(0xFFEF5350)
    }
    val renewAnim by animateFloatAsState(grid.renewablePercent / 100f, tween(1000), label = "renew")

    Surface(
        Modifier.fillMaxWidth(),
        color  = GlassSurface,
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, gridColor.copy(0.3f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = gridColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("Grid Intensity", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 13.sp)
                    Text("${grid.countryName} · ${grid.zone}", fontSize = 10.sp, color = Color.Gray)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (grid.isLive) Color(0xFF00E676).copy(0.12f) else Color.Gray.copy(0.12f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (grid.isLive) {
                            Box(Modifier.size(5.dp).background(Color(0xFF00E676), CircleShape))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            if (grid.isLive) "Live" else "Fallback",
                            fontSize = 9.sp,
                            color    = if (grid.isLive) Color(0xFF00E676) else Color.Gray
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("${grid.intensityGramsPerKwh}", fontSize = 26.sp,
                    fontWeight = FontWeight.Black, color = gridColor)
                Spacer(Modifier.width(4.dp))
                Text("g\nCO₂/kWh", fontSize = 9.sp, color = Color.Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(Color(0xFFEF5350).copy(0.25f))) {
                Box(Modifier.fillMaxWidth(renewAnim).height(6.dp).clip(CircleShape)
                    .background(gridColor))
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("${grid.renewablePercent}% renewable", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Text("${grid.fossilFuelPercent}% fossil",   fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  TELEMETRY CARD
// ══════════════════════════════════════════════════════════

@Composable
fun TelemetryCard(metric: TelemetryMetric) {
    Surface(
        Modifier.fillMaxWidth(),
        color  = GlassSurface.copy(0.5f),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp)
                    .background(metric.color.copy(0.1f), CircleShape)
                    .border(1.dp, metric.color.copy(0.3f), CircleShape),
                Alignment.Center
            ) {
                Icon(metric.icon, null, tint = metric.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(metric.label,   fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                Text(metric.subtext, fontSize = 11.sp, color = Color.Gray)
            }
            Text(metric.value, fontWeight = FontWeight.Black, color = metric.color, fontSize = 16.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  CO2 CARD
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedCO2Card(report: ESGReport, gridResult: GridIntensityResult?) {
    val animCO2    by animateFloatAsState(report.carbonFootprint.toFloat(),
        tween(1200, easing = FastOutSlowInEasing), label = "co2")
    val animPurity by animateFloatAsState(report.gridPurity / 100f,
        tween(1000, easing = FastOutSlowInEasing), label = "pur")
    val glow       by rememberInfiniteTransition(label = "g").animateFloat(
        0.1f, 0.35f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "ga"
    )

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(GlassSurface).border(1.dp, CarbonGreen.copy(glow)),
        Alignment.Center
    ) {
        Box(
            Modifier.fillMaxWidth().height(100.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(CarbonGreen.copy(0.04f), Color.Transparent)))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            Text("%.4f".format(animCO2), fontSize = 48.sp,
                fontWeight = FontWeight.Black, color = Color.White)
            Text("TOTAL KG CO2e", style = MaterialTheme.typography.labelSmall,
                color = CarbonGreen, letterSpacing = 2.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                .background(Color.White.copy(0.05f))) {
                Box(Modifier.fillMaxWidth(animPurity).height(7.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(CarbonGreen.copy(0.7f), CarbonGreen))))
            }
            Text(
                "Grid Purity: ${report.gridPurity}% (${gridResult?.countryName ?: "Global Avg"})",
                Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Color.Gray
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  PERMISSION SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedPermissionScreen(onAuth: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }
    val scale     by animateFloatAsState(if (visible) 1f else 0.8f,
        tween(500, easing = FastOutSlowInEasing), label = "ps")
    val alpha     by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "pa")
    val iconPulse by rememberInfiniteTransition(label = "ip").animateFloat(
        0.95f, 1.05f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "ips"
    )

    Column(
        modifier              = Modifier
            .fillMaxSize()
            .padding(40.dp)
            .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(120.dp).scale(iconPulse)
                    .background(CarbonGreen.copy(0.06f), CircleShape)
                    .border(1.dp, CarbonGreen.copy(0.2f), CircleShape)
            )
            Icon(Icons.Default.AdminPanelSettings, null,
                tint = CarbonGreen, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Enterprise Auth",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "X-Carbon requires Network and Location telemetry to calculate your real-world footprint.",
            textAlign = TextAlign.Center, color = Color.Gray,
            modifier  = Modifier.padding(vertical = 16.dp), lineHeight = 22.sp
        )
        Button(
            onClick  = onAuth,
            colors   = ButtonDefaults.buttonColors(CarbonGreen),
            shape    = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("AUTHORIZE SYSTEM ACCESS", color = DeepSpace,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  LOADING SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedLoadingScreen() {
    val rot   by rememberInfiniteTransition(label = "r").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "ra"
    )
    val pulse by rememberInfiniteTransition(label = "l").animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "lpa"
    )
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CarbonGreen,
                    modifier = Modifier.size(60.dp).graphicsLayer(rotationZ = rot))
                Icon(Icons.Default.Public, null,
                    tint = CarbonGreen.copy(pulse), modifier = Modifier.size(26.dp))
            }
            Text("Analysing telemetry…", color = Color.Gray, fontSize = 14.sp,
                modifier = Modifier.graphicsLayer(alpha = pulse))
        }
    }
}
