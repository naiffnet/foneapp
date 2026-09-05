package com.foneplus.app

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.foneplus.app.audio.FoneplusAudioService
import com.foneplus.app.audio.ChannelTonePlayer
import com.foneplus.app.audio.ExperimentalAudioCapability
import com.foneplus.app.ui.FoneplusTheme
import com.foneplus.app.ui.MainScreen
import com.foneplus.app.ui.SetupScreen
import com.foneplus.app.ui.SettingsScreen
import com.foneplus.app.ui.bluetoothStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoneplusTheme {
                val channelTonePlayer = remember { ChannelTonePlayer() }
                DisposableEffect(Unit) {
                    onDispose { channelTonePlayer.stop() }
                }
                var setupComplete by remember {
                    mutableStateOf(
                        getSharedPreferences("foneplus", MODE_PRIVATE)
                            .getBoolean("setup_complete", false)
                    )
                }
                var showSettings by remember { mutableStateOf(false) }
                val role = remember {
                    getSharedPreferences("foneplus", MODE_PRIVATE)
                        .getString("role", "driver") ?: "driver"
                }
                var keepRunning by remember {
                    mutableStateOf(
                        getSharedPreferences("foneplus", MODE_PRIVATE)
                            .getBoolean("keep_running", true)
                    )
                }
                var connectionStatus by remember { mutableStateOf(bluetoothStatus(this@MainActivity)) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            connectionStatus = bluetoothStatus(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                var isTalking by remember { mutableStateOf(false) }
                var duckingEnabled by remember { mutableStateOf(true) }
                var handsFreeEnabled by remember { mutableStateOf(false) }
                var speechSensitivity by remember { mutableStateOf(700f) }
                val leAudioSupported = remember {
                    ExperimentalAudioCapability.supportsLeAudio(this@MainActivity)
                }
                var pendingTalk by remember { mutableStateOf(false) }
                val requestPermissions = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted ->
                    if (granted[Manifest.permission.RECORD_AUDIO] == true && pendingTalk) {
                        startAudioService(duckingEnabled, handsFreeEnabled, speechSensitivity.toInt())
                        isTalking = true
                    }
                    pendingTalk = false
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!setupComplete) {
                        SetupScreen(
                            onOpenBluetoothSettings = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            },
                            onPlayChannel = { channel ->
                                channelTonePlayer.play(channel)
                            },
                            onComplete = { role ->
                                getSharedPreferences("foneplus", MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("setup_complete", true)
                                    .putString("role", role)
                                    .apply()
                                setupComplete = true
                            }
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            role = role,
                            keepRunning = keepRunning,
                            onKeepRunningChange = {
                                keepRunning = it
                                getSharedPreferences("foneplus", MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("keep_running", it)
                                    .apply()
                            },
                            onOpenBluetoothSettings = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            },
                            onRerunSetup = {
                                showSettings = false
                                setupComplete = false
                            },
                            onBack = { showSettings = false }
                        )
                    } else {
                        MainScreen(
                            connectionStatus = connectionStatus,
                            isTalking = isTalking,
                            duckingEnabled = duckingEnabled,
                            onDuckingChange = { duckingEnabled = it },
                            handsFreeEnabled = handsFreeEnabled,
                            onHandsFreeChange = { handsFreeEnabled = it },
                            speechSensitivity = speechSensitivity,
                            onSpeechSensitivityChange = { speechSensitivity = it },
                            leAudioSupported = leAudioSupported,
                            onOpenSettings = { showSettings = true },
                            onTalkingChange = { wantsToTalk ->
                                if (wantsToTalk) {
                                    pendingTalk = true
                                    val permissions = buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                            add(Manifest.permission.BLUETOOTH_CONNECT)
                                        }
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }.toTypedArray()
                                    requestPermissions.launch(permissions)
                                } else {
                                    stopAudioService()
                                    isTalking = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startAudioService(shouldDuck: Boolean, handsFree: Boolean, speechThreshold: Int) {
        val intent = Intent(this, FoneplusAudioService::class.java).apply {
            action = FoneplusAudioService.ACTION_START_TALK
            putExtra(FoneplusAudioService.EXTRA_DUCK, shouldDuck)
            putExtra(
                FoneplusAudioService.EXTRA_MODE,
                if (handsFree) FoneplusAudioService.MODE_HANDS_FREE else FoneplusAudioService.MODE_PTT
            )
            putExtra(FoneplusAudioService.EXTRA_SPEECH_THRESHOLD, speechThreshold)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAudioService() {
        val intent = Intent(this, FoneplusAudioService::class.java).apply {
            action = FoneplusAudioService.ACTION_STOP_TALK
        }
        startService(intent)
    }
}