package com.g5guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GuardService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.g5guard.START"
        const val ACTION_STOP = "com.g5guard.STOP"
        const val EXTRA_METHOD = "extra_method" // A or B
        
        // Static state for UI observation
        private val _networkState = MutableStateFlow(NetworkGeneration.UNKNOWN)
        val networkState: StateFlow<NetworkGeneration> = _networkState.asStateFlow()
        
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private lateinit var networkMonitor: NetworkMonitor
    private var currentMethod = "A"
    private val channelId = "FLOWHALT_CHANNEL"
    private val alertChannelId = "FLOWHALT_ALERT_CHANNEL"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        networkMonitor = NetworkMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                currentMethod = intent.getStringExtra(EXTRA_METHOD) ?: "A"
                startForegroundService()
            }
            ACTION_STOP -> stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        if (_isRunning.value) return
        
        val notification = createNotification("Monitoring 5G Network...")
        startForeground(1, notification)
        _isRunning.value = true

        networkMonitor.observeNetworkType()
            .onEach { networkType ->
                _networkState.value = networkType
                handleNetworkChange(networkType)
                updateNotificationContent("Network: ${networkType.name}")
            }
            .launchIn(lifecycleScope)
    }

    private fun handleNetworkChange(networkType: NetworkGeneration) {
        if (networkType == NetworkGeneration._4G) {
            sendAlertNotification("Network Shifted to 4G", "Data flow halted to prevent overage.")
            if (currentMethod == "A") {
                ActionHandler.executeMethodA(this)
            } else if (currentMethod == "B") {
                startVpnBlackhole()
            }
        } else if (networkType == NetworkGeneration._5G) {
            sendAlertNotification("Network Restored to 5G", "Data flow resumed automatically.")
            if (currentMethod == "B") {
                stopVpnBlackhole()
            }
        }
    }

    private fun startVpnBlackhole() {
        val intent = VpnService.prepare(this)
        if (intent == null) {
            // Permission already granted
            val vpnIntent = Intent(this, GuardVpnService::class.java).apply {
                action = GuardVpnService.ACTION_START_VPN
            }
            startService(vpnIntent)
        } else {
            // Need permission from UI, which we should have requested in MainActivity ideally.
            // For now, if permission is missing, VPN blackhole cannot start directly from service.
            // A Toast or intent could be sent.
        }
    }

    private fun stopVpnBlackhole() {
        val vpnIntent = Intent(this, GuardVpnService::class.java).apply {
            action = GuardVpnService.ACTION_STOP_VPN
        }
        startService(vpnIntent)
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        stopVpnBlackhole() // ensure we don't leave VPN on
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val configChannel = NotificationChannel(
                channelId,
                "FlowHalt Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                alertChannelId,
                "FlowHalt Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(configChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FlowHalt Active")
            .setContentText(contentText)
            // Using a default icon for now
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()
    }

    private fun updateNotificationContent(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, createNotification(contentText))
    }

    private fun sendAlertNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, alertChannelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), alertNotification)
    }
}
