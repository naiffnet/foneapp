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
import kotlin.math.max

class AudioEngine(
    private val context: Context,
    private val shouldDuck: Boolean
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

        val sampleRate = 16_000
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
        val inputBuffer = max(
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            sampleRate / 2
        )
        val outputBuffer = max(
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            sampleRate
        )

        val input = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(inputFormat)
            .setBufferSizeInBytes(inputBuffer)
            .build()
        // Sem isso, uma vez que configureCommunicationDevice() abaixo troca o
        // dispositivo de comunicacao ativo para o Bluetooth SCO, o Android passa
        // a capturar a fonte VOICE_COMMUNICATION a partir do microfone do fone,
        // nao do celular — o oposto do que o modo PTT precisa (motorista falando
        // pelo microfone do proprio aparelho).
        selectBuiltInMic(input)
        val output = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(outputFormat)
            .setBufferSizeInBytes(outputBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        configureCommunicationDevice()
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
