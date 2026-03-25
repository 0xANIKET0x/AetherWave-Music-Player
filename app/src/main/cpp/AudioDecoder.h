#pragma once

#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <cstdio>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswresample/swresample.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersrc.h>
#include <libavfilter/buffersink.h>
#include <libavutil/opt.h>
}

// Master Hack: Native Biquad IIR Filter
struct Biquad {
    float b0=1, b1=0, b2=0, a1=0, a2=0;
    float x1=0, x2=0, y1=0, y2=0;

    void calculatePeaking(float f0, float fs, float dbGain, float Q);
    void calculateLowPass(float f0, float fs, float Q);
    inline float process(float x) {
        float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x;
        y2 = y1; y1 = y;
        return y;
    }
};

class AudioDecoder {
public:
    AudioDecoder();
    ~AudioDecoder();

    bool loadTrack(const std::string& path, int targetSampleRate, int targetChannels, int fd = -1);
    void startDecoding();
    void stopDecoding();
    
    int readFrames(float* targetBuffer, int numFrames);
    double getDuration() const { return totalDurationSeconds; }
    void seekTo(float seconds);
    float getAmplitude() { return currentAmplitude.exchange(0.0f); }
    float getSpeedFactor() const { return currentSpeedFactor.load(); }
    
    void setEffectMode(int mode);
    std::atomic<int> inputBitrate{0};
    std::atomic<int> inputBitDepth{0};
    std::atomic<bool> isDolbyAtmos{false};
    
    void setEqBandGain(int index, float gain);
    void setEqEnabled(bool enabled) { eqEnabled.store(enabled); }
    void setForceMaxBitrate(bool force) { forceMaxBitrate.store(force); }
    std::atomic<bool> forceMaxBitrate{false};
    std::atomic<bool> eqEnabled{true};
    std::atomic<float> currentAmplitude{0.0f};
    
    int currentFd = -1;
    std::atomic<bool> reachedEofFlag{false};

    bool isBufferEmpty() {
        std::lock_guard<std::mutex> lock(bufferMutex);
        return audioBuffer.empty();
    }

private:
    std::atomic<float> eqBands[5];
    std::atomic<bool> pendingEqUpdate{false};
    void decodeLoop();
    bool initFilterGraph(AVCodecContext* codecCtx, AVRational time_base);

    std::string currentPath;
    int sampleRate;
    int channels;

    std::atomic<bool> isDecoding{false};
    std::thread decodeThread;

    std::mutex bufferMutex;
    std::vector<float> audioBuffer;

    double totalDurationSeconds = 0.0;
    
    std::atomic<bool> seekRequest{false};
    std::atomic<float> seekTarget{0.0f};

    std::atomic<int> currentEffectMode{0};
    std::atomic<bool> pendingEffectChange{false};
    std::atomic<float> currentSpeedFactor{1.0f};

    AVFilterGraph* filterGraph = nullptr;
    AVFilterContext* bufferSrcCtx = nullptr;
    AVFilterContext* bufferSinkCtx = nullptr;

    // DSP Filter States (Moved from static to members to prevent crosstalk)
    Biquad eqFilters[2][5];
    float currentGains[5] = {0,0,0,0,0};
    
    Biquad bassFilter;
    Biquad vocalCut1, vocalCut2;
    bool karaokeInitialized = false;
    int lastKaraokeSampleRate = 0;
};
