package com.g5guard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkGeneration {
    _5G, _4G, OTHER, UNKNOWN
}

class NetworkMonitor(private val context: Context) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun observeNetworkType(): Flow<NetworkGeneration> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            trySend(NetworkGeneration.UNKNOWN)
            close()
            return@callbackFlow
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    val networkType = telephonyDisplayInfo.networkType
                    val overrideType = telephonyDisplayInfo.overrideNetworkType

                    val gen = when {
                        overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                        overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> NetworkGeneration._5G
                        networkType == TelephonyManager.NETWORK_TYPE_NR -> NetworkGeneration._5G
                        networkType == TelephonyManager.NETWORK_TYPE_LTE -> NetworkGeneration._4G
                        else -> NetworkGeneration.OTHER
                    }
                    trySend(gen)
                }
            }

            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)

            awaitClose {
                telephonyManager.unregisterTelephonyCallback(callback)
            }
        } else {
            // Fallback for older devices, though 5G APIs are mostly in Android 11+
            // Not fully implementing older listeners to keep clean architecture focus on modern APIs
            trySend(NetworkGeneration.UNKNOWN)
            awaitClose { }
        }
    }.distinctUntilChanged()
}
