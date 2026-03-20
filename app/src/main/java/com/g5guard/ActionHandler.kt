package com.g5guard

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

object ActionHandler {

    fun executeMethodA(context: Context) {
        // Show a toast message
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "5G Dropped to 4G! Please switch off Data NOW!", Toast.LENGTH_LONG).show()
        }

        // Launch Mobile Network Settings
        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if specific activity not found
            val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}
