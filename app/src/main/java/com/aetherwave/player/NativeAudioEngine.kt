package com.aetherwave.player

class NativeAudioEngine {

    companion object {
        init {
            System.loadLibrary("player")
        }
    }

    private var nativeHandle: Long = 0

    init {
        nativeHandle = createEngine()
    }

    fun release() {
        if (nativeHandle != 0L) {
            destroyEngine(nativeHandle)
            nativeHandle = 0L
        }
    }

    protected fun finalize() {
        release()
    }

    private external fun createEngine(): Long
    private external fun destroyEngine(handle: Long)

    fun start() { if (nativeHandle != 0L) start(nativeHandle) }
    fun stop() { if (nativeHandle != 0L) stop(nativeHandle) }
    fun pause() { if (nativeHandle != 0L) pause(nativeHandle) }
    fun loadTrack(path: String, fd: Int = -1) { if (nativeHandle != 0L) loadTrack(nativeHandle, path, fd) }
    fun getEngineStatus(): String = if (nativeHandle != 0L) getEngineStatus(nativeHandle) else "Engine: Released"
    fun getDuration(): Double = if (nativeHandle != 0L) getDuration(nativeHandle) else 0.0
    fun getCurrentPosition(): Double = if (nativeHandle != 0L) getCurrentPosition(nativeHandle) else 0.0
    fun seekTo(seconds: Float) { if (nativeHandle != 0L) seekTo(nativeHandle, seconds) }
    fun setEffectMode(mode: Int) { if (nativeHandle != 0L) setEffectMode(nativeHandle, mode) }
    fun setEqBandGain(index: Int, gainDb: Float) { if (nativeHandle != 0L) setEqBandGain(nativeHandle, index, gainDb) }
    fun setEqEnabled(enabled: Boolean) { if (nativeHandle != 0L) setEqEnabled(nativeHandle, enabled) }
    fun setVolume(volume: Float) { if (nativeHandle != 0L) setVolume(nativeHandle, volume) }
    fun getAmplitude(): Float = if (nativeHandle != 0L) getAmplitude(nativeHandle) else 0.0f
    fun getInputBitrate(): Int = if (nativeHandle != 0L) getInputBitrate(nativeHandle) else 0
    fun getInputBitDepth(): Int = if (nativeHandle != 0L) getInputBitDepth(nativeHandle) else 0
    fun isDolbyAtmos(): Boolean = if (nativeHandle != 0L) isDolbyAtmos(nativeHandle) else false
    fun getOutputSampleRate(): Int = if (nativeHandle != 0L) getOutputSampleRate(nativeHandle) else 0
    fun getOutputBitrateString(): String = if (nativeHandle != 0L) getOutputBitrateString(nativeHandle) else "0 kbps"
    fun setForceMaxBitrate(force: Boolean) { if (nativeHandle != 0L) setForceMaxBitrate(nativeHandle, force) }
    fun setAudiophileMode(enabled: Boolean) { if (nativeHandle != 0L) setAudiophileMode(nativeHandle, enabled) }

    // Actual native implementations
    private external fun start(handle: Long)
    private external fun stop(handle: Long)
    private external fun pause(handle: Long)
    private external fun loadTrack(handle: Long, path: String, fd: Int)
    private external fun getEngineStatus(handle: Long): String
    private external fun getDuration(handle: Long): Double
    private external fun getCurrentPosition(handle: Long): Double
    private external fun seekTo(handle: Long, seconds: Float)
    private external fun setEffectMode(handle: Long, mode: Int)
    private external fun setEqBandGain(handle: Long, index: Int, gainDb: Float)
    private external fun setEqEnabled(handle: Long, enabled: Boolean)
    private external fun setVolume(handle: Long, volume: Float)
    private external fun getAmplitude(handle: Long): Float
    private external fun getInputBitrate(handle: Long): Int
    private external fun getInputBitDepth(handle: Long): Int
    private external fun isDolbyAtmos(handle: Long): Boolean
    private external fun getOutputSampleRate(handle: Long): Int
    private external fun getOutputBitrateString(handle: Long): String
    private external fun setForceMaxBitrate(handle: Long, force: Boolean)
    private external fun setAudiophileMode(handle: Long, enabled: Boolean)
}

