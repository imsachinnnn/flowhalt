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
import androidx.compose.ui.text.font.FontWeight
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
                    FlowHaltDashboard(
                        networkStateFlow = GuardService.networkState,
                        isRunningFlow = GuardService.isRunning,
                        onStartGuard = { method ->
                            startGuardService(method)
                        },
                        onStopGuard = {
                            stopGuardService()
                        },
                        onRequestVpn = {
                            requestVpnPermission()
                        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // App Header
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
