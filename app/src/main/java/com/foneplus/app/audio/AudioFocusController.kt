package com.foneplus.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class AudioFocusController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var granted = false

    fun begin() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || granted) return
        val focusRequest = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        ).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        ).build()
        request = focusRequest
        granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun end() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && granted) {
            request?.let(audioManager::abandonAudioFocusRequest)
        }
        request = null
        granted = false
    }
}