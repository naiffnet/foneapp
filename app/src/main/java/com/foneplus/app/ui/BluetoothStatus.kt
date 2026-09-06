package com.foneplus.app.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

fun bluetoothStatus(context: Context): String {
    return try {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val btDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        
        when {
            btDevice != null -> "Conectado: ${btDevice.productName.takeIf { it.isNotEmpty() } ?: "Fone Bluetooth"}"
            else -> "Fone Bluetooth não conectado"
        }
    } catch (_: SecurityException) {
        "Permissão Bluetooth necessária"
    }
}
