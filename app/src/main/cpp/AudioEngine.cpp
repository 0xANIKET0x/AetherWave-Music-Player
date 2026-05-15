#include <jni.h>
#include <oboe/Oboe.h>
#include <cstring>
#include <memory>
#include <string>
#include <atomic>
#include <thread>
#include "AudioDecoder.h"

// Re-add global status message required by AudioDecoder.cpp
std::string g_statusMessage = "Engine: Initialized";

class AetherPlayer : public oboe::AudioStreamCallback {
public:
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        float *floatData = static_cast<float *>(audioData);
        int framesRead = decoder.readFrames(floatData, numFrames);
        
        int channels = audioStream->getChannelCount();
        int totalSamples = framesRead * channels;

        // --- Start RMS Calculation for Audio Reactivity ---
        float sumSquare = 0.0f;
        for (int i = 0; i < totalSamples; i++) {
            sumSquare += floatData[i] * floatData[i];
        }
        float rms = (totalSamples > 0) ? sqrtf(sumSquare / totalSamples) : 0.0f;
        mCurrentAmplitude.store(rms);
        // --- End RMS ---

        // Apply volume gain natively for smooth crossfades
        float vol = mVolume.load();
        if (vol != 1.0f) {
            for (int i = 0; i < totalSamples; i++) {
                floatData[i] *= vol;
            }
        }

        if (framesRead < numFrames) {
            int remainingSamples = (numFrames - framesRead) * channels;
            memset(floatData + totalSamples, 0, sizeof(float) * remainingSamples);
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override {
        if (error == oboe::Result::ErrorDisconnected) {
            // The audio device was disconnected. We must recreate the stream.
            // Android 8.1+ will automatically reroute if we just open a new stream.
            // Since this runs on an Oboe thread, we must spawn a new thread to avoid deadlock.
            std::thread([this]() {
                start();
            }).detach();
        }
    }

    void start() {
        if (stream) {
            oboe::StreamState state = stream->getState();
            if (state == oboe::StreamState::Started || state == oboe::StreamState::Starting) {
                return; 
            }
            if (state != oboe::StreamState::Disconnected && state != oboe::StreamState::Closed) {
                oboe::Result res = stream->requestStart();
                if (res == oboe::Result::OK) {
                    mStatusMessage = "Engine: Resumed Playback.";
                    return;
                }
            }
        }
        
        stop(); 
        mTimeOffsetSeconds = 0.0;
        
        oboe::AudioStreamBuilder builder;
        builder.setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setCallback(this);

        if (mAudiophileMode.load()) {
            // Audiophile: probe native rate, exclusive DAC, fidelity priority
            int nativeRate = decoder.probeSampleRate(currentPath, currentFd);
            builder.setSampleRate(nativeRate)
                   ->setFormatConversionAllowed(true)
                   ->setSharingMode(oboe::SharingMode::Exclusive)
                   ->setPerformanceMode(oboe::PerformanceMode::None);
        } else {
            // Standard: shared mixer, low latency, battery-friendly
            builder.setFormatConversionAllowed(false)
                   ->setSharingMode(oboe::SharingMode::Shared)
                   ->setPerformanceMode(oboe::PerformanceMode::LowLatency);
        }

        oboe::Result result = builder.openStream(stream);
        if (result != oboe::Result::OK || !stream) {
            mStatusMessage = std::string("Engine Err: openStream failed. ") + oboe::convertToText(result);
            return;
        }
        
        mStatusMessage = "Engine: Stream Opened! Starting Decoder...";

        decoder.loadTrack(currentPath, stream->getSampleRate(), stream->getChannelCount(), currentFd);
        decoder.startDecoding();
        
        if (mPendingSeek > 0.0f) {
            decoder.seekTo(mPendingSeek);
            
            // Adjust mTimeOffsetSeconds so getCurrentPosition is correct immediately
            int32_t rate = stream->getSampleRate();
            float speed = decoder.getSpeedFactor();
            if (rate > 0) {
                mTimeOffsetSeconds = static_cast<double>(mPendingSeek) - (static_cast<double>(stream->getFramesRead()) / rate) * speed;
            }
            
            mPendingSeek = 0.0f;
        }
        
        oboe::Result startResult = stream->requestStart();
        if (startResult != oboe::Result::OK) {
            mStatusMessage = std::string("Engine Err: requestStart failed. ") + oboe::convertToText(startResult);
        }
    }

    void stop() {
        decoder.stopDecoding();
        if (stream) {
            stream->requestStop();
            stream->close();
            stream.reset();
        }
        mStatusMessage = "Engine: Stopped";
    }

    void pause() {
        if (stream) {
            stream->requestPause();
            mStatusMessage = "Engine: Paused";
        }
    }

    void loadTrack(const std::string& path, int fd) {
        currentPath = path;
        currentFd = fd;
        mPendingSeek = 0.0f;
        mStatusMessage = "Engine: Track staged. Waiting for Play command.";
    }

    double getDuration() {
        return decoder.getDuration();
    }

    std::string getEngineStatus() {
        if (decoder.reachedEofFlag.load() && decoder.isBufferEmpty()) {
            return "EOF: Native Buffer Drained";
        }
        return mStatusMessage;
    }

    double getCurrentPosition() {
        if (mPendingSeek > 0.0f) {
            return static_cast<double>(mPendingSeek);
        }
        if (stream) {
            int64_t frames = stream->getFramesRead();
            int32_t rate = stream->getSampleRate();
            float speed = decoder.getSpeedFactor();
            if (rate > 0) {
                // Return original timeline position by scaling the output seconds
                return mTimeOffsetSeconds + (static_cast<double>(frames) / rate) * speed;
            }
        }
        return 0.0;
    }

    void seekTo(float seconds) {
        if (!stream) {
            mPendingSeek = seconds;
            return;
        }
        
        int64_t frames = stream->getFramesRead();
        int32_t rate = stream->getSampleRate();
        float speed = decoder.getSpeedFactor();
        if (rate > 0) {
            mTimeOffsetSeconds = static_cast<double>(seconds) - (static_cast<double>(frames) / rate) * speed;
        }

        decoder.seekTo(seconds);

        // DO NOT pause, flush, or restart the Oboe stream here.
        // Oboe LowLatency buffers are tiny (milliseconds). 
        // Rapid stream state changes (Pause/Flush/Start) over dozens of taps will stall the audio pipeline.
        // The decoder will automatically clear its buffer and supply the new audio frames.
        mStatusMessage = "Engine: Seek applied.";
    }

    void setEffectMode(int mode) { decoder.setEffectMode(mode); }
    void setEqBandGain(int index, float gain) { decoder.setEqBandGain(index, gain); }
    void setEqEnabled(bool enabled) { decoder.setEqEnabled(enabled); }
    void setForceMaxBitrate(bool force) { decoder.setForceMaxBitrate(force); }
    void setAudiophileMode(bool enabled) {
        mAudiophileMode.store(enabled);
        decoder.setAudiophileMode(enabled);
    }
    void setVolume(float volume) { mVolume.store(volume); }
    float getAmplitude() { return mCurrentAmplitude.load(); }

    int getInputBitrate() { return decoder.inputBitrate.load(); }
    int getInputBitDepth() { return decoder.inputBitDepth.load(); }
    bool isDolbyAtmos() { return decoder.isDolbyAtmos.load(); }
    int getOutputSampleRate() { return stream ? stream->getSampleRate() : 48000; }
    
    int getOutputBitrate() {
        int rate = stream ? stream->getSampleRate() : 48000;
        int ch = stream ? stream->getChannelCount() : 2;
        return (rate * ch * 32) / 1000;
    }

private:
    std::shared_ptr<oboe::AudioStream> stream;
    AudioDecoder decoder;
    std::string currentPath;
    int currentFd = -1;
    std::string mStatusMessage = "Engine: Initialized";
    double mTimeOffsetSeconds = 0.0;
    std::atomic<float> mVolume{1.0f};
    std::atomic<float> mCurrentAmplitude{0.0f};
    std::atomic<bool> mAudiophileMode{false};
    float mPendingSeek = 0.0f;
};

extern "C" {

inline AetherPlayer* getEngine(jlong handle) {
    return reinterpret_cast<AetherPlayer*>(handle);
}

JNIEXPORT jlong JNICALL
Java_com_aetherwave_player_NativeAudioEngine_createEngine(JNIEnv *env, jobject thiz) {
    AetherPlayer* p = new AetherPlayer();
    return reinterpret_cast<jlong>(p);
}

JNIEXPORT void JNICALL
Java_com_aetherwave_player_NativeAudioEngine_destroyEngine(JNIEnv *env, jobject thiz, jlong handle) {
    AetherPlayer* p = getEngine(handle);
    if (p) {
        p->stop();
        delete p;
    }
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_start(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) p->start();
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_stop(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) p->stop();
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_pause(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) p->pause();
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_loadTrack(JNIEnv *env, jobject thiz, jlong handle, jstring path, jint fd) {
    if (auto p = getEngine(handle)) {
        const char *pathChars = env->GetStringUTFChars(path, nullptr);
        p->loadTrack(std::string(pathChars), fd);
        env->ReleaseStringUTFChars(path, pathChars);
    }
}

JNIEXPORT jstring JNICALL Java_com_aetherwave_player_NativeAudioEngine_getEngineStatus(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) {
        return env->NewStringUTF(p->getEngineStatus().c_str());
    }
    return env->NewStringUTF("Engine: Invalid Handle");
}

JNIEXPORT jdouble JNICALL Java_com_aetherwave_player_NativeAudioEngine_getDuration(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->getDuration();
    return 0.0;
}

JNIEXPORT jdouble JNICALL Java_com_aetherwave_player_NativeAudioEngine_getCurrentPosition(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->getCurrentPosition();
    return 0.0;
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_seekTo(JNIEnv *env, jobject thiz, jlong handle, jfloat seconds) {
    if (auto p = getEngine(handle)) p->seekTo(seconds);
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_setEffectMode(JNIEnv *env, jobject thiz, jlong handle, jint mode) {
    if (auto p = getEngine(handle)) p->setEffectMode(mode);
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_setEqBandGain(JNIEnv *env, jobject thiz, jlong handle, jint index, jfloat gain) {
    if (auto p = getEngine(handle)) p->setEqBandGain(index, gain);
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_setEqEnabled(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    if (auto p = getEngine(handle)) p->setEqEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_setVolume(JNIEnv *env, jobject thiz, jlong handle, jfloat volume) {
    if (auto p = getEngine(handle)) p->setVolume(volume);
}

JNIEXPORT jfloat JNICALL Java_com_aetherwave_player_NativeAudioEngine_getAmplitude(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->getAmplitude();
    return 0.0f;
}

JNIEXPORT jint JNICALL Java_com_aetherwave_player_NativeAudioEngine_getInputBitrate(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->getInputBitrate();
    return 0;
}

JNIEXPORT jint JNICALL Java_com_aetherwave_player_NativeAudioEngine_getInputBitDepth(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->getInputBitDepth();
    return 0;
}

JNIEXPORT jboolean JNICALL Java_com_aetherwave_player_NativeAudioEngine_isDolbyAtmos(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->isDolbyAtmos();
    return false;
}

JNIEXPORT jint JNICALL Java_com_aetherwave_player_NativeAudioEngine_getOutputSampleRate(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) return p->getOutputSampleRate();
    return 0;
}

JNIEXPORT jstring JNICALL Java_com_aetherwave_player_NativeAudioEngine_getOutputBitrateString(JNIEnv *env, jobject thiz, jlong handle) {
    if (auto p = getEngine(handle)) {
        std::string text = std::to_string(p->getOutputBitrate()) + " kbps";
        return env->NewStringUTF(text.c_str());
    }
    return env->NewStringUTF("0 kbps");
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_setForceMaxBitrate(JNIEnv *env, jobject thiz, jlong handle, jboolean force) {
    if (auto p = getEngine(handle)) p->setForceMaxBitrate(force);
}

JNIEXPORT void JNICALL Java_com_aetherwave_player_NativeAudioEngine_setAudiophileMode(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    if (auto p = getEngine(handle)) p->setAudiophileMode(enabled);
}

}
