package com.foneplus.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class ChannelTonePlayer {
    private var track: AudioTrack? = null

    fun play(channel: Channel) {
        stop()
        val sampleRate = 44_100
        val sampleCount = sampleRate * 700 / 1_000
        val samples = ShortArray(sampleCount * 2)
        for (index in 0 until sampleCount) {
            val envelope = minOf(1.0, index / 2_000.0, (sampleCount - index) / 2_000.0)
            val value = (sin(2.0 * PI * 440.0 * index / sampleRate) * 8_000 * envelope).toInt().toShort()
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
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
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
