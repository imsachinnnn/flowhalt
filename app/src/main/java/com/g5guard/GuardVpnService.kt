package com.g5guard

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class GuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START_VPN = "com.g5guard.START_VPN"
        const val ACTION_STOP_VPN = "com.g5guard.STOP_VPN"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> startVpn()
            ACTION_STOP_VPN -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("FlowHaltBlackHole")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0) // Route all IPv4 traffic
            .addRoute("::", 0) // Route all IPv6 traffic
            // We do not set any underlying network, effectively blackholing traffic
        
        try {
            vpnInterface = builder.establish()
            Log.d("GuardVpnService", "VPN established (Traffic Blocked)")
        } catch (e: Exception) {
            Log.e("GuardVpnService", "Failed to establish VPN", e)
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d("GuardVpnService", "VPN stopped (Traffic Allowed)")
        } catch (e: Exception) {
            Log.e("GuardVpnService", "Failed to close VPN interface", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
