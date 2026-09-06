package com.foneplus.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin

class ChannelTonePlayer(private val context: Context) {
    private var track: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun play(channel: Channel) {
        stop()
        val sampleRate = 44_100
        val sampleCount = sampleRate * 700 / 1_000
        val samples = ShortArray(sampleCount * 2)
        for (index in 0 until sampleCount) {
            val envelope = minOf(1.0, index / 2_000.0, (sampleCount - index) / 2_000.0)
            val value = (sin(2.0 * PI * 440.0 * index / sampleRate) * 20_000 * envelope).toInt().toShort()
            val offset = index * 2
            samples[offset] = if (channel == Channel.LEFT) value else 0
            samples[offset + 1] = if (channel == Channel.RIGHT) value else 0
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        
        // Tenta rotear explicitamente para o fone Bluetooth se detectado
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bluetoothDevice = devices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            if (bluetoothDevice != null) {
                created.setPreferredDevice(bluetoothDevice)
            }
        }
        
        track = created
        created.write(samples, 0, samples.size)
        created.play()
    }

    fun stop() {
        track?.let {
            if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
            it.release()
        }
        track = null
    }
}

enum class Channel {
    LEFT,
    RIGHT
}
