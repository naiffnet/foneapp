package com.foneplus.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build

class AudioEngine(
    private val context: Context,
    private val shouldDuck: Boolean,
    private val role: String
) : AudioPipeline {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var worker: Thread? = null
    private var focusGranted = false
    private var focusRequest: android.media.AudioFocusRequest? = null

    override fun start(): Result<Unit> {
        if (worker != null) return Result.success(Unit)

        return try {
            startInternal()
            Result.success(Unit)
        } catch (error: Exception) {
            stop()
            Result.failure(error)
        }
    }

    private fun startInternal() {
        // Para Motorista (Driver), usamos 44.1kHz Estéreo para manter a qualidade e seletividade.
        // Para Passageiro, usamos 16kHz que é o limite padrão do Bluetooth SCO.
        val sampleRate = if (role == "driver") 44_100 else 16_000
        val inputFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val outputFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        
        val inputBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val outputBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)

        val input = AudioRecord.Builder()
            .setAudioSource(if (role == "driver") MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(inputFormat)
            .setBufferSizeInBytes(inputBuffer)
            .build()
        
        if (role == "driver") {
            selectBuiltInMic(input)
        }

        val output = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (role == "driver") AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(if (role == "driver") AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(outputFormat)
            .setBufferSizeInBytes(outputBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        // Só ativamos o SCO (Bluetooth de chamada) se for o Passageiro,
        // pois ele precisa do microfone do fone.
        if (role == "passenger") {
            configureCommunicationDevice()
        }

        if (shouldDuck) requestAudioFocus()
        recorder = input
        player = output
        input.startRecording()
        output.play()

        worker = Thread {
            val mono = ShortArray(320)
            val stereo = ShortArray(mono.size * 2)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val read = input.read(mono, 0, mono.size)
                    if (read <= 0) continue
                    for (index in 0 until read) {
                        val sample = mono[index]
                        stereo[index * 2] = sample
                        stereo[index * 2 + 1] = 0
                    }
                    output.write(stereo, 0, read * 2)
                }
            } finally {
                input.stopSafely()
                output.stopSafely()
            }
        }.also {
            it.name = "foneplus-audio"
            it.start()
        }
    }

    override fun stop() {
        worker?.interrupt()
        worker = null
        recorder?.release()
        player?.release()
        recorder = null
        player = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
        }
        abandonAudioFocus()
    }

    private fun selectBuiltInMic(record: AudioRecord) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        if (device != null) {
            record.setPreferredDevice(device)
        }
    }

    private fun configureCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetooth = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            if (bluetooth != null) {
                audioManager.setCommunicationDevice(bluetooth)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
        }
    }

    private fun requestAudioFocus() {
        val request = android.media.AudioFocusRequest.Builder(
            android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        ).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        ).build()
            focusRequest = request
        focusGranted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (focusGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        }
        focusGranted = false
        focusRequest = null
    }

    private fun AudioRecord.stopSafely() {
        if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
    }

    private fun AudioTrack.stopSafely() {
        if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
    }
}
