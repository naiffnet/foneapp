package com.foneplus.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

class ChannelTonePlayer(private val context: Context) {
    private var playThread: Thread? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val TAG = "ChannelTonePlayer"
    }

    fun play(channel: Channel) {
        stop()
        Log.d(TAG, "Iniciando tom no canal: $channel")
        
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao limpar modo de áudio", e)
        }

        val thread = object : Thread("TonePlayerThread") {
            override fun run() {
                val sampleRate = 44_100
                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                    .setBufferSizeInBytes(minBufSize.coerceAtLeast(1024 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                try {
                    track.play()
                    val samples = ShortArray(2048)
                    var phase = 0.0
                    val phaseInc = 2.0 * Math.PI * 440.0 / sampleRate

                    while (!isInterrupted) {
                        for (i in 0 until 1024) {
                            val value = (Math.sin(phase) * 20000).toInt().toShort()
                            phase += phaseInc
                            val offset = i * 2
                            if (channel == Channel.LEFT) {
                                samples[offset] = value
                                samples[offset + 1] = 0
                            } else {
                                samples[offset] = 0
                                samples[offset + 1] = value
                            }
                        }
                        val result = track.write(samples, 0, samples.size)
                        if (result <= 0) break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro na thread", e)
                } finally {
                    try {
                        track.stop()
                        track.release()
                    } catch (_: Exception) {}
                }
            }
        }
        playThread = thread
        thread.start()
    }

    fun stop() {
        playThread?.interrupt()
        playThread = null
    }
}

enum class Channel {
    LEFT,
    RIGHT
}
