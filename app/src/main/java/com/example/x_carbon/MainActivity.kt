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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.*

// ── ESG models ────────────────────────────────────────────
data class ESGReport(
    val carbonFootprint: Double,
    val gridPurity: Int,
    val networkEfficiency: Double,
    val hardwareStress: Double,
    val treesOffset: Double,
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
    Dashboard("ESG Live",   Icons.Default.Public),
    XAI      ("Explain AI", Icons.Default.Psychology),
    History  ("History",    Icons.Default.History)
}

// ── Enterprise engine ──────────────────────────────────────


// ── Theme ──────────────────────────────────────────────────
val CarbonGreen  = Color(0xFF00E676)
val DeepSpace    = Color(0xFF07090B)
val GlassSurface = Color(0xFF121418)
val BorderColor  = Color(0x1AFFFFFF)

// ── Activity ───────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ESGTheme { MainContainer(this) } }
    }

    fun getNetworkData(context: Context): Pair<Double, Double> {
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        return try {
            val cal = Calendar.getInstance().apply { add(Calendar.HOUR, -24) }
            val wifiStats   = nsm.querySummaryForUser(ConnectivityManager.TYPE_WIFI,   null, cal.timeInMillis, System.currentTimeMillis())
            val mobileStats = nsm.querySummaryForUser(ConnectivityManager.TYPE_MOBILE, null, cal.timeInMillis, System.currentTimeMillis())
            Pair(
                (wifiStats.rxBytes   + wifiStats.txBytes)   / (1024.0 * 1024.0),
                (mobileStats.rxBytes + mobileStats.txBytes) / (1024.0 * 1024.0)
            )
        } catch (e: Exception) { Pair(450.0, 120.0) }
    }

    fun getHardwareThermalState(): Int {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus else 0
    }

    fun hasPermissions(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val usageMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        else @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        val locationMode = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return usageMode == AppOpsManager.MODE_ALLOWED && locationMode == PackageManager.PERMISSION_GRANTED
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATED PARTICLE BACKGROUND
// ══════════════════════════════════════════════════════════

data class Particle(
    val x: Float, val y: Float,
    val radius: Float,
    val speedX: Float, val speedY: Float,
    val alpha: Float,
    val color: Color
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

    val time by produceState(0f) {
        while (true) {
            delay(16)
            value += 0.016f
        }
    }

    // Slow pulse animation
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue   = 0.85f,
        targetValue    = 1.15f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Background gradient orbs
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E676).copy(alpha = 0.04f * pulse),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.15f),
                    radius = size.width * 0.7f
                )
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7E57C2).copy(alpha = 0.03f * pulse),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.8f, size.height * 0.6f),
                        radius = size.width * 0.6f
                    ),
                    center = Offset(size.width * 0.8f, size.height * 0.6f),
                    radius = size.width * 0.6f
                )

                // Moving particles
                particles.forEach { p ->
                    val x = ((p.x + p.speedX * time * 60) % size.width + size.width) % size.width
                    val y = ((p.y + p.speedY * time * 60) % size.height + size.height) % size.height
                    drawCircle(
                        color  = p.color.copy(alpha = p.alpha),
                        center = Offset(x, y),
                        radius = p.radius
                    )
                }
            }
    )
}

// ══════════════════════════════════════════════════════════
//  SPLASH / BOOT SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }

    // Phase 0 → logo appears
    // Phase 1 → tagline appears
    // Phase 2 → scan line sweeps
    // Phase 3 → exit

    LaunchedEffect(Unit) {
        delay(300);  phase = 1
        delay(600);  phase = 2
        delay(800);  phase = 3
        delay(700);  onFinished()
    }

    val logoAlpha by animateFloatAsState(
        targetValue   = if (phase >= 1) 1f else 0f,
        animationSpec = tween(500),
        label         = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue   = if (phase >= 1) 1f else 0.6f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "logoScale"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue   = if (phase >= 2) 1f else 0f,
        animationSpec = tween(400),
        label         = "tagAlpha"
    )
    val exitAlpha by animateFloatAsState(
        targetValue   = if (phase >= 3) 0f else 1f,
        animationSpec = tween(600),
        label         = "exitAlpha"
    )

    val scanProgress by animateFloatAsState(
        targetValue   = if (phase >= 2) 1f else 0f,
        animationSpec = tween(900, easing = LinearEasing),
        label         = "scan"
    )

    // Pulsing ring
    val ringScale by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue  = 0.9f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label         = "ringScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .graphicsLayer(alpha = exitAlpha),
        contentAlignment = Alignment.Center
    ) {
        AnimatedParticleBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Outer pulsing ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(ringScale)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(CarbonGreen.copy(0.08f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, CarbonGreen.copy(0.2f), CircleShape)
                )

                // Logo circle
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer(alpha = logoAlpha, scaleX = logoScale, scaleY = logoScale)
                        .background(GlassSurface, CircleShape)
                        .border(1.dp, CarbonGreen.copy(0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Public,
                        null,
                        tint     = CarbonGreen,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // App name with scan line reveal
            Box(
                modifier = Modifier
                    .graphicsLayer(alpha = logoAlpha)
                    .drawBehind {
                        // Green scan line sweeping down
                        if (scanProgress > 0f && scanProgress < 1f) {
                            val y = size.height * scanProgress
                            drawLine(
                                color       = CarbonGreen.copy(alpha = 0.8f),
                                start       = Offset(0f, y),
                                end         = Offset(size.width, y),
                                strokeWidth = 2f
                            )
                            // Glow below scan line
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors  = listOf(CarbonGreen.copy(0.15f), Color.Transparent),
                                    startY  = y,
                                    endY    = y + 40f
                                )
                            )
                        }
                    }
            ) {
                Text(
                    "X-CARBON",
                    fontSize      = 40.sp,
                    fontWeight    = FontWeight.Black,
                    color         = Color.White,
                    letterSpacing = 6.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Enterprise ESG Intelligence",
                fontSize      = 13.sp,
                color         = CarbonGreen.copy(alpha = taglineAlpha),
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(48.dp))

            // Loading dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer(alpha = taglineAlpha)
            ) {
                repeat(3) { i ->
                    val dotAlpha by rememberInfiniteTransition(label = "dot$i").animateFloat(
                        initialValue  = 0.2f,
                        targetValue   = 1f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(600, delayMillis = i * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotAnim$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CarbonGreen.copy(alpha = dotAlpha), CircleShape)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  THEME
// ══════════════════════════════════════════════════════════

@Composable
fun ESGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary    = CarbonGreen,
            background = DeepSpace,
            surface    = GlassSurface
        ),
        content = content
    )
}

// ══════════════════════════════════════════════════════════
//  MAIN CONTAINER
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
    var isDemoMode         by remember { mutableStateOf(false) }
    var demoTick           by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionsGranted = activity.hasPermissions() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationChannels.createAll(context)
    }
    // Track battery for drain measurement
    var prevBatteryPct by remember { mutableIntStateOf(-1) }
    var sessionStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            if (historyRecords.isEmpty()) {
                historyRecords = CarbonHistoryStorage.load(context)
            }

            val (wifi, mobile)  = activity.getNetworkData(context)
            val thermal         = activity.getHardwareThermalState()
            val fetchedGrid     = GridIntensityService.fetch(context)
            gridResult          = fetchedGrid

            val sessionHours    = (System.currentTimeMillis() - sessionStartMs) / 3_600_000.0

            // Read all device inputs
            val readings = DeviceReader.read(
                context         = context,
                wifiMb          = wifi,
                mobileMb        = mobile,
                thermalState    = thermal,
                sessionHours    = sessionHours.coerceAtLeast(0.001),
                gridIntensity   = fetchedGrid.intensityKgPerKwh,
                gridRegion      = fetchedGrid.countryName,
                prevBatteryPct  = prevBatteryPct.takeIf { it > 0 }
            )

            // Save battery level for next reading
            prevBatteryPct = readings.batteryPct

            // Run the calculation engine
            val calcResult = CarbonCalculationEngine.calculate(readings)

            // Convert to ESGReport for rest of app
            val esg = calcResult.toESGReport()
            esgReport = esg
            xaiReport = XAIEngine.explain(
                mbWifi        = wifi,
                mbMobile      = mobile,
                gridIntensity = fetchedGrid.intensityKgPerKwh,
                thermalState  = thermal,
                esg           = esg
            )

            historyRecords = CarbonHistoryManager.addRecord(
                existing      = historyRecords,
                co2Kg         = esg.carbonFootprint,
                gridIntensity = fetchedGrid.intensityKgPerKwh,
                thermalState  = thermal,
                mbWifi        = wifi,
                mbMobile      = mobile
            )
            CarbonHistoryStorage.save(context, historyRecords)
            CarbonNotificationService.checkAndNotify(
                context    = context,
                co2Kg      = esg.carbonFootprint,
                thresholds = thresholds
            )
        }
    }

    // Demo mode ticker
    LaunchedEffect(isDemoMode) {
        if (!isDemoMode) return@LaunchedEffect
        while (true) {
            delay(3000)
            demoTick++

            val demoWifi  = 200.0 + (demoTick * 18.5) + (Math.random() * 40)
            val demoMob   = 50.0  + (demoTick * 6.2)  + (Math.random() * 15)
            val demoGrid  = gridResult?.intensityKgPerKwh ?: 0.580
            val demoTherm = when {
                demoTick % 9 < 3 -> 0
                demoTick % 9 < 6 -> 1
                else             -> 2
            }

            // Vary brightness and network type each tick for visible demo changes
            val demoBrightness = (80 + (demoTick * 17) % 175)  // cycles 80–255
            val demoNetworkType = when (demoTick % 4) {
                0    -> DeviceNetworkType.WIFI
                1    -> DeviceNetworkType.CELLULAR_4G
                2    -> DeviceNetworkType.CELLULAR_5G
                else -> DeviceNetworkType.CELLULAR_3G
            }

            // Build demo readings using real device values where possible,
            // simulated values for the demo-specific inputs
            val demoReadings = DeviceReadings(
                networkType           = demoNetworkType,
                downlinkMbps          = 10.0 + (Math.random() * 90),
                wifiMb                = demoWifi,
                mobileMb              = demoMob,
                signalStrength        = -60 - (demoTick % 30),   // cycles -60 to -90 dBm
                batteryPct            = DeviceReader.getBatteryPct(context),
                batteryDrainPct       = 0.5 + (Math.random() * 1.5),
                batteryCapacityMah    = DeviceReader.getBatteryCapacity(context),
                isCharging            = false,
                batteryVoltageV       = DeviceReader.getBatteryVoltage(context),
                screenBrightness      = demoBrightness,
                isOLED                = true,
                thermalState          = demoTherm,
                cpuCores              = Runtime.getRuntime().availableProcessors(),
                sessionHours          = (demoTick * 3.0) / 3600.0,   // grows each tick
                gridIntensityKgPerKwh = demoGrid,
                gridRegionName        = gridResult?.countryName ?: "Demo"
            )

            val calcResult = CarbonCalculationEngine.calculate(demoReadings)
            val esg        = calcResult.toESGReport()

            esgReport  = esg
            xaiReport  = XAIEngine.explain(demoWifi, demoMob, demoGrid, demoTherm, esg)

            historyRecords = CarbonHistoryManager.addRecord(
                existing      = historyRecords,
                co2Kg         = esg.carbonFootprint,
                gridIntensity = demoGrid,
                thermalState  = demoTherm,
                mbWifi        = demoWifi,
                mbMobile      = demoMob
            )
            CarbonHistoryStorage.save(context, historyRecords)
            CarbonNotificationService.checkAndNotify(
                context    = context,
                co2Kg      = esg.carbonFootprint,
                thresholds = thresholds
            )
        }
    }
    // Show splash first
    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

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
                                selectedIconColor = if (screen == Screen.XAI) Color(0xFF7E57C2) else CarbonGreen,
                                indicatorColor    = if (screen == Screen.XAI) Color(0xFF7E57C2).copy(0.1f) else CarbonGreen.copy(0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DeepSpace)
        ) {
            // Live particle background on all screens
            AnimatedParticleBackground()

            if (!permissionsGranted) {
                AnimatedPermissionScreen {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            } else {
                AnimatedContent(
                    targetState   = currentScreen,
                    transitionSpec = {
                        (slideInHorizontally { it } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally { -it } + fadeOut(tween(200)))
                    },
                    label = "screen"
                ) { screen ->
                    when (screen) {
                        Screen.Dashboard -> if (esgReport != null) ESGHome(
                            report       = esgReport!!,
                            gridResult   = gridResult,
                            thresholds   = thresholds,
                            isDemoMode   = isDemoMode,
                            onDemoToggle = { isDemoMode = !isDemoMode; demoTick = 0 },
                            onToggle     = { id, enabled ->
                                thresholds = thresholds.map {
                                    if (it.id == id) it.copy(enabled = enabled) else it
                                }
                            }
                        ) else AnimatedLoadingScreen()
                        Screen.XAI     -> if (xaiReport != null) XAIScreen(xaiReport!!) else AnimatedLoadingScreen()
                        Screen.History -> HistoryScreen(historyRecords)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATED ESG HOME
// ══════════════════════════════════════════════════════════

@Composable
fun ESGHome(
    report:       ESGReport,
    gridResult:   GridIntensityResult?,
    thresholds:   List<NotificationThreshold>,
    isDemoMode:   Boolean,
    onDemoToggle: () -> Unit,
    onToggle:     (String, Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    // Stagger animation — items fly in one by one
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Status bar
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(400)) + slideInVertically { -20 }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(CarbonGreen, CircleShape).blur(4.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "ESG COMPLIANCE: STABLE",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = CarbonGreen,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                if (isDemoMode) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF7E57C2).copy(0.15f)
                    ) {
                        Text(
                            "DEMO",
                            modifier      = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize      = 10.sp,
                            color         = Color(0xFF7E57C2),
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
        }

        // Demo toggle
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(400, delayMillis = 80)) + slideInVertically { -20 }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = if (isDemoMode) Color(0xFF7E57C2).copy(0.12f) else Color.White.copy(0.03f),
                shape    = RoundedCornerShape(16.dp),
                border   = BorderStroke(1.dp, if (isDemoMode) Color(0xFF7E57C2).copy(0.4f) else BorderColor)
            ) {
                Row(
                    modifier          = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isDemoMode) Icons.Default.PlayCircle else Icons.Default.PlayCircleOutline,
                        null,
                        tint     = if (isDemoMode) Color(0xFF7E57C2) else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isDemoMode) "Demo mode active" else "Demo mode",
                            fontWeight = FontWeight.Bold,
                            color      = if (isDemoMode) Color.White else Color.Gray,
                            fontSize   = 13.sp
                        )
                        Text(
                            if (isDemoMode) "Simulating realistic usage — updates every 3s"
                            else            "Simulate varying carbon for demonstration",
                            color      = Color.Gray,
                            fontSize   = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked         = isDemoMode,
                        onCheckedChange = { onDemoToggle() },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = Color(0xFF7E57C2),
                            checkedTrackColor   = Color(0xFF7E57C2).copy(0.3f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.Gray.copy(0.2f)
                        )
                    )
                }
            }
        }

        // Grid card
        AnimatedVisibility(
            visible = visible && gridResult != null,
            enter   = fadeIn(tween(400, delayMillis = 160)) + slideInVertically { 30 }
        ) {
            if (gridResult != null) GridIntensityCard(gridResult)
        }

        // Main CO2 card with animated number
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500, delayMillis = 240)) + scaleIn(
                initialScale  = 0.92f,
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
        ) {
            AnimatedCO2Card(report, gridResult)
        }

        // Telemetry
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(400, delayMillis = 320)) + slideInVertically { 40 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "System Telemetry",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                report.telemetryData.forEachIndexed { i, metric ->
                    AnimatedTelemetryRow(metric, delayMs = i * 80)
                }
            }
        }

        // Notification settings
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(400, delayMillis = 480)) + slideInVertically { 40 }
        ) {
            NotificationSettingsCard(
                thresholds   = thresholds,
                onToggle     = onToggle,
                currentCO2Kg = report.carbonFootprint
            )
        }

        // XAI teaser
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(400, delayMillis = 560)) + slideInVertically { 40 }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = Color(0xFF7E57C2).copy(alpha = 0.08f),
                shape    = RoundedCornerShape(18.dp),
                border   = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.25f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null, tint = Color(0xFF7E57C2), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Why did I get this score?", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(
                            "Tap \"Explain AI\" for feature attribution, counterfactuals and model formula.",
                            color = Color.Gray, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATED CO2 CARD — number counts up on load
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedCO2Card(report: ESGReport, gridResult: GridIntensityResult?) {
    // Animate CO2 number counting up from 0
    val animatedCO2 by animateFloatAsState(
        targetValue   = report.carbonFootprint.toFloat(),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "co2Count"
    )
    val animatedPurity by animateFloatAsState(
        targetValue   = report.gridPurity / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "purity"
    )

    // Breathing glow on the card border
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue  = 0.1f,
        targetValue   = 0.35f,
        animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label         = "glowAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(GlassSurface)
            .border(1.dp, CarbonGreen.copy(alpha = glowAlpha)),
        contentAlignment = Alignment.Center
    ) {
        // Subtle inner glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(CarbonGreen.copy(0.04f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(40.dp)
        ) {
            Text(
                "%.4f".format(animatedCO2),
                fontSize   = 52.sp,
                fontWeight = FontWeight.Black,
                color      = Color.White
            )
            Text(
                "TOTAL KG CO2e",
                style         = MaterialTheme.typography.labelSmall,
                color         = CarbonGreen,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(20.dp))

            // Animated progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedPurity)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(CarbonGreen.copy(0.7f), CarbonGreen)
                            )
                        )
                )
            }
            Text(
                "Grid Purity: ${report.gridPurity}% (${gridResult?.countryName ?: "Global Avg"})",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color    = Color.Gray
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATED TELEMETRY ROW — slides in with delay
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedTelemetryRow(metric: TelemetryMetric, delayMs: Int = 0) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(350)) + slideInHorizontally { -30 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = GlassSurface.copy(alpha = 0.5f),
            shape    = RoundedCornerShape(20.dp),
            border   = BorderStroke(1.dp, BorderColor)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // Animated colour dot
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(metric.color.copy(0.1f), CircleShape)
                        .border(1.dp, metric.color.copy(0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(metric.icon, null, tint = metric.color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(metric.label,   fontWeight = FontWeight.Bold, color = Color.White)
                    Text(metric.subtext, fontSize   = 12.sp,           color = Color.Gray)
                }
                // Animated value
                val valueAnim by animateFloatAsState(
                    targetValue   = 1f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label         = "val"
                )
                Text(
                    metric.value,
                    fontWeight = FontWeight.Black,
                    color      = metric.color,
                    fontSize   = 18.sp,
                    modifier   = Modifier.graphicsLayer(alpha = valueAnim)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATED PERMISSION SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedPermissionScreen(onAuth: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); visible = true }

    val scale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.8f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label         = "permScale"
    )
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label         = "permAlpha"
    )

    // Icon pulse
    val iconScale by rememberInfiniteTransition(label = "icon").animateFloat(
        initialValue  = 0.95f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label         = "iconPulse"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(40.dp)
            .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pulsing icon with rings
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(iconScale)
                    .background(CarbonGreen.copy(0.06f), CircleShape)
                    .border(1.dp, CarbonGreen.copy(0.2f), CircleShape)
            )
            Icon(
                Icons.Default.AdminPanelSettings,
                null,
                tint     = CarbonGreen,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Enterprise Auth", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "X-Carbon requires Network and Location telemetry to calculate your real-world footprint.",
            textAlign  = TextAlign.Center,
            color      = Color.Gray,
            modifier   = Modifier.padding(vertical = 16.dp),
            lineHeight = 22.sp
        )
        Button(
            onClick = onAuth,
            colors  = ButtonDefaults.buttonColors(CarbonGreen),
            shape   = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("AUTHORIZE SYSTEM ACCESS", color = DeepSpace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATED LOADING SCREEN
// ══════════════════════════════════════════════════════════

@Composable
fun AnimatedLoadingScreen() {
    val rotation by rememberInfiniteTransition(label = "rot").animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label         = "rotAnim"
    )
    val pulse by rememberInfiniteTransition(label = "lp").animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "loadPulse"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color    = CarbonGreen,
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer(rotationZ = rotation)
                )
                Icon(
                    Icons.Default.Public,
                    null,
                    tint     = CarbonGreen.copy(alpha = pulse),
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                "Analysing telemetry…",
                color    = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.graphicsLayer(alpha = pulse)
            )
        }
    }
}

// ── Shared composable (kept for compatibility) ─────────────
@Composable
fun TelemetryRow(metric: TelemetryMetric) = AnimatedTelemetryRow(metric)

@Composable
fun PermissionScreen(onAuth: () -> Unit) = AnimatedPermissionScreen(onAuth)

@Composable
fun LoadingScreen() = AnimatedLoadingScreen()