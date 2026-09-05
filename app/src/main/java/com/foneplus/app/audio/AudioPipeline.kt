package com.foneplus.app.audio

interface AudioPipeline {
    fun start(): Result<Unit>
    fun stop()
}
