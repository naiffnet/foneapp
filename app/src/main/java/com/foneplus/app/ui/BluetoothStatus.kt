package com.foneplus.app.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

fun bluetoothStatus(context: Context): String {
    return try {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice
        } else {
            null
        }
        when {
            device?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Conectado: ${device.productName}"
            audioManager.isBluetoothScoOn -> "Conectado via Bluetooth"
            else -> "Fone Bluetooth não conectado"
        }
    } catch (_: SecurityException) {
        "Permissão Bluetooth necessária"
    }
}
