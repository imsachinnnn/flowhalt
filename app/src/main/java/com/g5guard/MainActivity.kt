package com.g5guard

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private var hasPermissions by mutableStateOf(false)
    
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // VPN permission result
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppNavigation(
                        onRequestVpn = { requestVpnPermission() },
                        onStartGuard = { startGuardService(it) },
                        onStopGuard = { stopGuardService() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION, // Often needed along phone state for network inference
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        }
    }

    private fun startGuardService(method: String) {
        val intent = Intent(this, GuardService::class.java).apply {
            action = GuardService.ACTION_START
            putExtra(GuardService.EXTRA_METHOD, method)
        }
        startService(intent)
    }

    private fun stopGuardService() {
        val intent = Intent(this, GuardService::class.java).apply {
            action = GuardService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun MainAppNavigation(
    onRequestVpn: () -> Unit,
    onStartGuard: (String) -> Unit,
    onStopGuard: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3500) // 3.5 seconds splash logic
        showSplash = false
    }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = {
            fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
        },
        label = "SplashToDashboard"
    ) { isSplash ->
        if (isSplash) {
            FlowHaltSplashScreen()
        } else {
            FlowHaltDashboard(
                networkStateFlow = GuardService.networkState,
                isRunningFlow = GuardService.isRunning,
                onStartGuard = onStartGuard,
                onStopGuard = onStopGuard,
                onRequestVpn = onRequestVpn
            )
        }
    }
}

@Composable
fun FlowHaltSplashScreen() {
    var startFlowHaltAnim by remember { mutableStateOf(false) }
    var startZenythAnim by remember { mutableStateOf(false) }

    val logoAlpha by animateFloatAsState(
        targetValue = if (startFlowHaltAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    
    val logoOffset by animateFloatAsState(
        targetValue = if (startFlowHaltAnim) 0f else 50f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "logoOffset"
    )

    val zenythAlpha by animateFloatAsState(
        targetValue = if (startZenythAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 500),
        label = "zenythAlpha"
    )

    LaunchedEffect(Unit) {
        delay(300)
        startFlowHaltAnim = true
        delay(1200)
        startZenythAnim = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_flowhalt_logo),
                contentDescription = "FlowHalt Logo",
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        translationY = logoOffset
                    }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = zenythAlpha }
            ) {
                Text(
                    text = "by ",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_zenyth_logo),
                    contentDescription = "Zenyth Logo",
                    modifier = Modifier.height(36.dp)
                )
            }
        }
    }
}

@Composable
fun FlowHaltDashboard(
    networkStateFlow: StateFlow<NetworkGeneration>,
    isRunningFlow: StateFlow<Boolean>,
    onStartGuard: (String) -> Unit,
    onStopGuard: () -> Unit,
    onRequestVpn: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("A") }
    val isRunning by isRunningFlow.collectAsState()
    val networkState by networkStateFlow.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // App Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FlowHalt",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Intelligent Overage Protection",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = { showAboutDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Info, 
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Premium Status Indicator
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isRunning) 8.dp else 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CONNECTION STATUS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val statusColor = when (networkState) {
                    NetworkGeneration._5G -> Color(0xFF00E676)
                    NetworkGeneration._4G -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                
                Text(
                    text = networkState.name.replace("_", ""),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Method Selector (Selectable Cards)
        Text(
            text = "PROTECTION METHOD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        MethodSelectionCard(
            title = "Method A",
            subtitle = "Settings Hack (Manual Switch)",
            isSelected = selectedMethod == "A",
            enabled = !isRunning,
            onClick = { selectedMethod = "A" }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        MethodSelectionCard(
            title = "Method B",
            subtitle = "Local VPN Kill-Switch",
            isSelected = selectedMethod == "B",
            enabled = !isRunning,
            onClick = {
                selectedMethod = "B"
                onRequestVpn()
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Main Action Button
        Button(
            onClick = { if (isRunning) onStopGuard() else onStartGuard(selectedMethod) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
        ) {
            Text(
                text = if (isRunning) "HALT PROTECTION" else "ACTIVATE SHIELD",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }

    if (showAboutDialog) {
        AboutZenythDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun AboutZenythDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "About FlowHalt", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(text = "How it Works:")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "FlowHalt constantly monitors your cellular network type. The moment it detects a drop from 5G to 4G, it automatically pauses your data using your selected method, ensuring you never accidentally consume slow or metered 4G data instead of unlimited 5G data.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Developed by Zenyth",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "A one-man startup.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { uriHandler.openUri("https://zenyth-in.vercel.app/") },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Visit website: zenyth-in.vercel.app")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MethodSelectionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null, // Handled by Surface
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = subtitle, fontSize = 14.sp, color = contentColor.copy(alpha = 0.7f))
            }
        }
    }
}
