package com.foneplus.app.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.foneplus.app.R

class FoneplusAudioService : Service() {
    private var engine: AudioEngine? = null
    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            // Handle Bluetooth connection events
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    engine?.stop()
                    updateNotification("Fone Bluetooth desconectado")
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    updateNotification("Fone Bluetooth conectado; pronto para transmitir")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(
            bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
        startForeground(NOTIFICATION_ID, buildNotification("Pronto para transmitir"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TALK -> {
                intentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PTT
                startTalking(
                    intent.getBooleanExtra(EXTRA_DUCK, true),
                    intent.getIntExtra(EXTRA_SPEECH_THRESHOLD, DEFAULT_SPEECH_THRESHOLD)
                )
            }
            ACTION_STOP_TALK -> stopTalking()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        unregisterReceiver(bluetoothReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTalking(shouldDuck: Boolean, speechThreshold: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("Permissao de microfone necessaria")
            return
        }
        if (engine == null) {
            engine = if (intentMode == MODE_HANDS_FREE) {
                HandsFreeAudioEngine(this, shouldDuck, speechThreshold)
            } else {
                AudioEngine(this, shouldDuck)
            }
        }
        val result = engine?.start()
        updateNotification(
            if (result?.isSuccess == true) "Transmitindo pelo fone" else "Falha ao iniciar audio"
        )
    }

    private var intentMode: String = MODE_PTT

    private fun stopTalking() {
        engine?.stop()
        updateNotification("Pronto para transmitir")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Comunicacao Foneplus",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Foneplus")
            .setContentText(message)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    companion object {
        const val ACTION_START_TALK = "com.foneplus.app.action.START_TALK"
        const val ACTION_STOP_TALK = "com.foneplus.app.action.STOP_TALK"
        const val ACTION_STOP_SERVICE = "com.foneplus.app.action.STOP_SERVICE"
        const val EXTRA_DUCK = "extra_duck"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_PTT = "ptt"
        const val MODE_HANDS_FREE = "hands_free"
        private const val CHANNEL_ID = "foneplus_audio"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_SPEECH_THRESHOLD = "extra_speech_threshold"
        private const val DEFAULT_SPEECH_THRESHOLD = 700
    }
}
