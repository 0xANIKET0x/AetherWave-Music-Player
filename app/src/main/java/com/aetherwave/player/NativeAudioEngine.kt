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

    fun start() = start(nativeHandle)
    fun stop() = stop(nativeHandle)
    fun pause() = pause(nativeHandle)
    fun loadTrack(path: String, fd: Int = -1) = loadTrack(nativeHandle, path, fd)
    fun getEngineStatus(): String = getEngineStatus(nativeHandle)
    fun getDuration(): Double = getDuration(nativeHandle)
    fun getCurrentPosition(): Double = getCurrentPosition(nativeHandle)
    fun seekTo(seconds: Float) = seekTo(nativeHandle, seconds)
    fun setEffectMode(mode: Int) = setEffectMode(nativeHandle, mode)
    fun setEqBandGain(index: Int, gainDb: Float) = setEqBandGain(nativeHandle, index, gainDb)
    fun setEqEnabled(enabled: Boolean) = setEqEnabled(nativeHandle, enabled)
    fun setVolume(volume: Float) = setVolume(nativeHandle, volume)
    fun getAmplitude(): Float = getAmplitude(nativeHandle)
    fun getInputBitrate(): Int = getInputBitrate(nativeHandle)
    fun getInputBitDepth(): Int = getInputBitDepth(nativeHandle)
    fun isDolbyAtmos(): Boolean = isDolbyAtmos(nativeHandle)
    fun getOutputSampleRate(): Int = getOutputSampleRate(nativeHandle)
    fun getOutputBitrateString(): String = getOutputBitrateString(nativeHandle)
    fun setForceMaxBitrate(force: Boolean) = setForceMaxBitrate(nativeHandle, force)

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
}
