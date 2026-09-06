package com.foneplus.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import kotlin.math.abs
import kotlin.math.max

class HandsFreeAudioEngine(
    private val context: Context,
    private val shouldDuck: Boolean,
    private val speechThreshold: Int,
    private val passengerMicId: String? = null
) : AudioPipeline {
    // Nota: O modo mãos-livres ainda precisa ser atualizado para suportar o role
    // e evitar SCO quando for Driver. Por enquanto, vamos apenas compatibilizar o construtor
    // se for chamado, mas o foco está no PTT.
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var phoneRecord: AudioRecord? = null
    private var bluetoothRecord: AudioRecord? = null
    private var player: AudioTrack? = null
    private var worker: Thread? = null
    private var echoCancelers = emptyList<AcousticEchoCanceler>()
    private var suppressors = emptyList<NoiseSuppressor>()
    private var focusController: AudioFocusController? = null

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
        val monoFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val stereoFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val inputBuffer = max(
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            sampleRate / 2
        )
        val outputBuffer = max(
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
            sampleRate
        )
        val phone = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(monoFormat)
            .setBufferSizeInBytes(inputBuffer)
            .build()
        val bluetooth = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(monoFormat)
            .setBufferSizeInBytes(inputBuffer)
            .build()
        selectInputDevice(phone, AudioDeviceInfo.TYPE_BUILTIN_MIC)
        val chosenPassengerMic = MicOptions.findDevice(context, passengerMicId)
        if (chosenPassengerMic != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetooth.setPreferredDevice(chosenPassengerMic)
        } else {
            selectInputDevice(bluetooth, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        }
        configureCommunicationDevice()
        focusController = AudioFocusController(context).also {
        }

        val output = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(stereoFormat)
            .setBufferSizeInBytes(outputBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        phoneRecord = phone
        bluetoothRecord = bluetooth
        player = output
        echoCancelers = listOfNotNull(
            AcousticEchoCanceler.create(phone.audioSessionId),
            AcousticEchoCanceler.create(bluetooth.audioSessionId)
        ).onEach { it.enabled = true }
        suppressors = listOfNotNull(
            NoiseSuppressor.create(phone.audioSessionId),
            NoiseSuppressor.create(bluetooth.audioSessionId)
        ).onEach { it.enabled = true }
        phone.startRecording()
        bluetooth.startRecording()
        output.play()

        worker = Thread {
            val driver = ShortArray(320)
            val passenger = ShortArray(320)
            val stereo = ShortArray(640)
            var silentFrames = 0
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val driverRead = phone.read(driver, 0, driver.size)
                    val passengerRead = bluetooth.read(passenger, 0, passenger.size)
                    val count = minOf(driverRead, passengerRead)
                    if (count <= 0) continue
                    var peak = 0
                    for (index in 0 until count) {
                        stereo[index * 2] = driver[index]
                        stereo[index * 2 + 1] = passenger[index]
                        peak = max(peak, max(abs(driver[index].toInt()), abs(passenger[index].toInt())))
                    }
                    if (shouldDuck && peak > speechThreshold) {
                        focusController?.begin()
                        silentFrames = 0
                    } else if (shouldDuck) {
                        silentFrames++
                        if (silentFrames >= SILENT_FRAMES_TO_RELEASE) {
                            focusController?.end()
                        }
                    }
                    output.write(stereo, 0, count * 2)
                }
            } finally {
                phone.stopSafely()
                bluetooth.stopSafely()
                output.stopSafely()
            }
        }.also {
            it.name = "foneplus-handsfree-audio"
            it.start()
        }
    }

    override fun stop() {
        worker?.interrupt()
        worker = null
        echoCancelers.forEach { it.release() }
        suppressors.forEach { it.release() }
        echoCancelers = emptyList()
        suppressors = emptyList()
        phoneRecord?.release()
        bluetoothRecord?.release()
        player?.release()
        phoneRecord = null
        bluetoothRecord = null
        player = null
        focusController?.end()
        focusController = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    private fun selectInputDevice(record: AudioRecord, type: Int) {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == type }
        if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            record.setPreferredDevice(device)
        }
    }

    private fun configureCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }?.let(audioManager::setCommunicationDevice)
        } else {
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
        }
    }

    private fun AudioRecord.stopSafely() {
        if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
    }

    private fun AudioTrack.stopSafely() {
        if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
    }

    companion object {
        private const val SILENT_FRAMES_TO_RELEASE = 25
    }
}