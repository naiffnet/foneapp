package com.foneplus.app.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build

object ExperimentalAudioCapability {
    fun supportsLeAudio(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val adapter = context.getSystemService(BluetoothAdapter::class.java) ?: return false
        return adapter.isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED
    }
}