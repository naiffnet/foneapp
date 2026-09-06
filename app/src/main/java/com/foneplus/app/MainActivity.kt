package com.foneplus.app

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private var micTestJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoneplusTheme {
                val channelTonePlayer = remember { ChannelTonePlayer(this@MainActivity) }
                val scope = rememberCoroutineScope()
                var micLevel by remember { mutableFloatStateOf(0f) }
                var activeMicName by remember { mutableStateOf("") }
                var availableMics by remember { mutableStateOf(emptyList<String>()) }
                var selectedMicIndex by remember { mutableIntStateOf(0) }
                var isMicTesting by remember { mutableStateOf(false) }
                
                val audioManager = remember { getSystemService(AudioManager::class.java) }
                
                // Atualiza lista de microfones periodicamente ou quando o teste inicia
                fun refreshMics() {
                    val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    availableMics = inputs.map { 
                        val type = when(it.type) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT-Call"
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT-Music"
                            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Onboard"
                            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cabo"
                            else -> "Outro"
                        }
                        "[$type] ${it.productName}" 
                    }.distinct()
                }
                
                DisposableEffect(Unit) {
                    onDispose { 
                        channelTonePlayer.stop()
                        stopMicTest()
                    }
                }
                var setupComplete by remember {
                    mutableStateOf(
                        getSharedPreferences("foneplus", MODE_PRIVATE)
                            .getBoolean("setup_complete", false)
                    )
                }
                var showSettings by remember { mutableStateOf(false) }
                var role by remember {
                    mutableStateOf(
                        getSharedPreferences("foneplus", MODE_PRIVATE)
                            .getString("role", "driver") ?: "driver"
                    )
                }
                var sideDistribution by remember {
                    mutableStateOf(
                        getSharedPreferences("foneplus", MODE_PRIVATE)
                            .getString("side_distribution", "L-R") ?: "L-R"
                    )
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
                var pendingMicTest by remember { mutableStateOf(false) }
                fun beginMicTest() {
                    refreshMics()
                    isMicTesting = true
                    micTestJob = scope.launch(Dispatchers.IO) {
                        runMicTest(selectedMicIndex) { level, name ->
                            micLevel = level
                            activeMicName = name
                        }
                    }
                }
                val requestPermissions = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted ->
                    if (granted[Manifest.permission.RECORD_AUDIO] == true) {
                        if (pendingTalk) {
                            startAudioService(role, sideDistribution, duckingEnabled, handsFreeEnabled, speechSensitivity.toInt())
                            isTalking = true
                        }
                        if (pendingMicTest) {
                            beginMicTest()
                        }
                    }
                    pendingTalk = false
                    pendingMicTest = false
                }

                LaunchedEffect(setupComplete) {
                    if (!setupComplete) {
                        val permissions = buildList {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                add(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }.toTypedArray()
                        if (permissions.isNotEmpty()) {
                            requestPermissions.launch(permissions)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!setupComplete) {
                        SetupScreen(
                            onOpenBluetoothSettings = {
                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            },
                            onPlayChannel = { channel ->
                                Log.d("FoneplusUI", "Botão de teste de canal clicado: $channel")
                                channelTonePlayer.play(channel)
                            },
                            onStopAudio = {
                                channelTonePlayer.stop()
                            },
                            micLevel = micLevel,
                            activeMicName = activeMicName,
                            availableMics = availableMics,
                            selectedMicIndex = selectedMicIndex,
                            onMicSelected = { selectedMicIndex = it },
                            isMicTesting = isMicTesting,
                            onToggleMicTest = { testing ->
                                if (testing) {
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        beginMicTest()
                                    } else {
                                        pendingMicTest = true
                                        requestPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    }
                                } else {
                                    isMicTesting = false
                                    stopMicTest()
                                    micLevel = 0f
                                    activeMicName = ""
                                }
                            },
                            onComplete = { selectedRole, selectedDist ->
                                getSharedPreferences("foneplus", MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("setup_complete", true)
                                    .putString("role", selectedRole)
                                    .putString("side_distribution", selectedDist)
                                    .apply()
                                role = selectedRole
                                sideDistribution = selectedDist
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

    private suspend fun waitForScoConnected(timeoutMs: Long) {
        // audioManager.startBluetoothSco() e assincrono: o link de verdade so fica pronto
        // quando o Android dispara ACTION_SCO_AUDIO_STATE_UPDATED com estado CONNECTED.
        // Um delay fixo pode ser curto demais (silencio no teste) ou longo demais
        // (demora sem necessidade). Aqui esperamos o evento real, com um teto de seguranca.
        val connected = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: Intent?) {
                        val state = intent?.getIntExtra(
                            AudioManager.EXTRA_SCO_AUDIO_STATE,
                            AudioManager.SCO_AUDIO_STATE_ERROR
                        )
                        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                            if (cont.isActive) cont.resume(true) {}
                            try { unregisterReceiver(this) } catch (_: Exception) {}
                        } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR || state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                            if (cont.isActive) cont.resume(false) {}
                            try { unregisterReceiver(this) } catch (_: Exception) {}
                        }
                    }
                }
                registerReceiver(receiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
                cont.invokeOnCancellation { try { unregisterReceiver(receiver) } catch (_: Exception) {} }
            }
        } ?: false
        Log.d("MainActivity", "SCO conectado=$connected (aguardado via broadcast)")
        if (!connected) {
            // Fallback de seguranca: se o evento nunca chegou (alguns OEMs sao inconsistentes),
            // damos uma folga extra antes de desistir e gravar mesmo assim.
            delay(500)
        }
    }

    private fun stopMicTest() {
        micTestJob?.cancel()
        micTestJob = null
    }

    private suspend fun runMicTest(targetIndex: Int, onLevel: (Float, String) -> Unit) {
        val audioManager = getSystemService(AudioManager::class.java)
        val sampleRate = 16_000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufferSize <= 0) return
        
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val targetDevice = if (targetIndex in inputs.indices) inputs[targetIndex] else null
        
        var debugInfo = targetDevice?.productName?.toString() ?: "Desconhecido"

        // Se for Bluetooth, tenta ativar o SCO e espera a conexao real (nao um tempo fixo)
        if (targetDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                waitForScoConnected(timeoutMs = 4000)
            } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao ativar SCO", e)
            }
        }

        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) return
            
            // Força o uso do dispositivo selecionado
            if (targetDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                record.setPreferredDevice(targetDevice)
            }
            
            record.startRecording()
            val buffer = ShortArray(bufferSize)
            
            while (micTestJob?.isActive == true) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = sqrt(sum / read)
                    val level = (rms / 5000.0).coerceIn(0.0, 1.0).toFloat()
                    
                    var activeName = debugInfo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        record.routedDevice?.let { 
                            activeName = "${if (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) "FONE" else "CELULAR"}: ${it.productName}"
                        }
                    }
                    
                    onLevel(level, activeName)
                }
                delay(50)
            }
            record.stop()
            record.release()
        } finally {
            if (targetDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
        }
    }

    private fun startAudioService(role: String, sideDist: String, shouldDuck: Boolean, handsFree: Boolean, speechThreshold: Int) {
        val intent = Intent(this, FoneplusAudioService::class.java).apply {
            action = FoneplusAudioService.ACTION_START_TALK
            putExtra(FoneplusAudioService.EXTRA_ROLE, role)
            putExtra(FoneplusAudioService.EXTRA_SIDE_DISTRIBUTION, sideDist)
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