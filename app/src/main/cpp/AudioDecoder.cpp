#include "AudioDecoder.h"
#include <android/log.h>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

void Biquad::calculatePeaking(float f0, float fs, float dbGain, float Q) {
    float A = std::pow(10.0f, dbGain / 40.0f);
    float w0 = 2.0f * M_PI * f0 / fs;
    float cosw0 = std::cos(w0);
    float alpha = std::sin(w0) / (2.0f * Q);

    float a0 = 1.0f + alpha / A;
    b0 = (1.0f + alpha * A) / a0;
    b1 = (-2.0f * cosw0) / a0;
    b2 = (1.0f - alpha * A) / a0;
    a1 = (-2.0f * cosw0) / a0;
    a2 = (1.0f - alpha / A) / a0;
}

void Biquad::calculateLowPass(float f0, float fs, float Q) {
    float w0 = 2.0f * M_PI * f0 / fs;
    float cosw0 = std::cos(w0);
    float alpha = std::sin(w0) / (2.0f * Q);

    float a0 = 1.0f + alpha;
    b0 = (1.0f - cosw0) / 2.0f / a0;
    b1 = (1.0f - cosw0) / a0;
    b2 = (1.0f - cosw0) / 2.0f / a0;
    a1 = (-2.0f * cosw0) / a0;
    a2 = (1.0f - alpha) / a0;
}

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AetherDecoder", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "AetherDecoder", __VA_ARGS__)

static int custom_read_packet(void *opaque, uint8_t *buf, int buf_size) {
    FILE* fileHandle = static_cast<FILE*>(opaque);
    size_t bytesRead = fread(buf, 1, buf_size, fileHandle);
    if (bytesRead == 0) {
        if (feof(fileHandle)) return AVERROR_EOF;
        if (ferror(fileHandle)) return AVERROR(EIO);
        return 0;
    }
    return bytesRead;
}

static int64_t custom_seek_packet(void *opaque, int64_t offset, int whence) {
    FILE* fileHandle = static_cast<FILE*>(opaque);
    if (whence == AVSEEK_SIZE) {
        long current = ftell(fileHandle);
        fseek(fileHandle, 0, SEEK_END);
        long size = ftell(fileHandle);
        fseek(fileHandle, current, SEEK_SET);
        return size;
    }
    
    whence &= ~AVSEEK_FORCE; 
    
    if (fseek(fileHandle, offset, whence) != 0) {
        return -1;
    }
    return ftell(fileHandle);
}

extern std::string g_statusMessage;

AudioDecoder::AudioDecoder() : sampleRate(48000), channels(2), karaokeInitialized(false), lastKaraokeSampleRate(0), eqEnabled(true) {
    for (int i = 0; i < 5; i++) {
        eqBands[i] = 0.0f;
        currentGains[i] = 0.0f;
    }
}

AudioDecoder::~AudioDecoder() {
    stopDecoding();
}

void AudioDecoder::seekTo(float seconds) {
    std::lock_guard<std::mutex> lock(bufferMutex);
    audioBuffer.clear();
    seekTarget = seconds;
    seekRequest = true;
}

void AudioDecoder::setEffectMode(int mode) {
    currentEffectMode = mode;
    pendingEffectChange = true;
}

void AudioDecoder::setEqBandGain(int index, float gain) {
    if (index >= 0 && index < 5) {
        eqBands[index].store(gain);
        pendingEqUpdate.store(true);
    }
}

bool AudioDecoder::loadTrack(const std::string& path, int targetSampleRate, int targetChannels, int fd) {
    stopDecoding();
    currentPath = path;
    currentFd = fd;
    sampleRate = targetSampleRate;
    channels = targetChannels;
    
    std::lock_guard<std::mutex> lock(bufferMutex);
    audioBuffer.clear();
    reachedEofFlag = false;
    
    return true;
}

void AudioDecoder::startDecoding() {
    if (isDecoding) return;
    isDecoding = true;
    decodeThread = std::thread(&AudioDecoder::decodeLoop, this);
}

void AudioDecoder::stopDecoding() {
    isDecoding = false;
    if (decodeThread.joinable()) {
        decodeThread.join();
    }
}

int AudioDecoder::readFrames(float* targetBuffer, int numFrames) {
    std::lock_guard<std::mutex> lock(bufferMutex);
    
    int samplesNeeded = numFrames * channels;
    if (audioBuffer.size() >= static_cast<size_t>(samplesNeeded)) {
        std::copy(audioBuffer.begin(), audioBuffer.begin() + samplesNeeded, targetBuffer);
        audioBuffer.erase(audioBuffer.begin(), audioBuffer.begin() + samplesNeeded);
        return numFrames;
    } else {
        int framesAvailable = audioBuffer.size() / channels;
        int samplesAvailable = framesAvailable * channels;
        if (samplesAvailable > 0) {
            std::copy(audioBuffer.begin(), audioBuffer.begin() + samplesAvailable, targetBuffer);
            audioBuffer.erase(audioBuffer.begin(), audioBuffer.begin() + samplesAvailable);
        }
        return framesAvailable;
    }
}

bool AudioDecoder::initFilterGraph(AVCodecContext* codecCtx, AVRational time_base) {
    auto attemptBuild = [&](const std::string& dsp_string, bool use_eq) -> bool {
        if (filterGraph) {
            avfilter_graph_free(&filterGraph);
        }
        
        filterGraph = avfilter_graph_alloc();
        if (!filterGraph) { g_statusMessage = "DSP: filterGraph alloc failed"; return false; }

        const AVFilter* abuffer = avfilter_get_by_name("abuffer");
        const AVFilter* abuffersink = avfilter_get_by_name("abuffersink");
        
        if (!abuffer || !abuffersink) { g_statusMessage = "DSP: abuffer/sink missing from binary"; return false; }

        bufferSrcCtx = avfilter_graph_alloc_filter(filterGraph, abuffer, "in");
        if (!bufferSrcCtx) { g_statusMessage = "DSP: bufferSrcCtx alloc failed"; return false; }

        AVBufferSrcParameters* par = av_buffersrc_parameters_alloc();
        if (!par) { g_statusMessage = "DSP: src_parameters alloc failed"; return false; }
        
        int sr = codecCtx->sample_rate > 0 ? codecCtx->sample_rate : 48000;
        par->sample_rate = sr;
        par->time_base = {1, sr};
        par->format = codecCtx->sample_fmt;
        par->ch_layout = codecCtx->ch_layout;
        
        if (par->ch_layout.nb_channels == 0) {
            av_channel_layout_default(&par->ch_layout, 2);
        }

        if (av_buffersrc_parameters_set(bufferSrcCtx, par) < 0) {
            g_statusMessage = "DSP: src_parameters_set failed";
            av_free(par);
            return false;
        }
        av_free(par);
        
        if (avfilter_init_str(bufferSrcCtx, nullptr) < 0) { g_statusMessage = "DSP: bufferSrc init_str failed"; return false; }

        bufferSinkCtx = avfilter_graph_alloc_filter(filterGraph, abuffersink, "out");
        if (!bufferSinkCtx) { g_statusMessage = "DSP: bufferSinkCtx alloc failed"; return false; }
        
        if (avfilter_init_str(bufferSinkCtx, nullptr) < 0) { g_statusMessage = "DSP: bufferSink init_str failed"; return false; }

        std::string full_descr;
        if (use_eq) {
            std::string eqStr = "firequalizer=gain_entry='entry(60," + std::to_string(eqBands[0].load()) + ");"
                                "entry(230," + std::to_string(eqBands[1].load()) + ");"
                                "entry(910," + std::to_string(eqBands[2].load()) + ");"
                                "entry(3600," + std::to_string(eqBands[3].load()) + ");"
                                "entry(14000," + std::to_string(eqBands[4].load()) + ")'";
            full_descr = eqStr;
            if (!dsp_string.empty()) full_descr += "," + dsp_string;
        } else {
            full_descr = dsp_string;
        }

        if (full_descr.empty()) {
            if (avfilter_link(bufferSrcCtx, 0, bufferSinkCtx, 0) < 0) {
                g_statusMessage = "DSP: native avfilter_link failed"; 
                return false;
            }
        } else {
            AVFilterInOut* outputs = avfilter_inout_alloc();
            AVFilterInOut* inputs  = avfilter_inout_alloc();

            outputs->name       = av_strdup("in");
            outputs->filter_ctx = bufferSrcCtx;
            outputs->pad_idx    = 0;
            outputs->next       = nullptr;

            inputs->name       = av_strdup("out");
            inputs->filter_ctx = bufferSinkCtx;
            inputs->pad_idx    = 0;
            inputs->next       = nullptr;

            int ret = avfilter_graph_parse_ptr(filterGraph, full_descr.c_str(), &inputs, &outputs, nullptr);
            avfilter_inout_free(&inputs);
            avfilter_inout_free(&outputs);

            if (ret < 0) { 
                g_statusMessage = "DSP: graph_parse_ptr failed on: " + full_descr; 
                return false; 
            }
        }

        if (avfilter_graph_config(filterGraph, nullptr) < 0) { g_statusMessage = "DSP: graph_config failed"; return false; }

        return true;
    };

    std::string primary_dsp;
    switch (currentEffectMode.load()) {
        case 1: primary_dsp = "aecho=0.8:0.88:60:0.4"; break;
        case 2: primary_dsp = "anull"; break;
        case 3: primary_dsp = "lowpass=f=3000,highpass=f=200"; break;
        case 4: primary_dsp = "extrastereo=m=2.5,apulsator=hz=0.125,areverb"; break;
        case 5: primary_dsp = "anull"; break; // Instrumental Focus handled natively below
        default: primary_dsp = "anull"; break;
    }

    if (attemptBuild(primary_dsp, true)) return true;
    if (attemptBuild("anull", true)) return true;
    if (attemptBuild("", true)) return true;
    
    // Fallback tiers where EQ filter is fundamentally absent from the compiled .so binaries
    if (attemptBuild(primary_dsp, false)) {
        g_statusMessage = "DSP Warn: EQ Filter missing from binary"; return true; 
    }
    if (attemptBuild("anull", false)) {
        g_statusMessage = "DSP Warn: EQ and Effect Missing. Core passthrough."; return true;
    }
    if (attemptBuild("", false)) {
        g_statusMessage = "DSP Warn: All Filters Missing. Hardware bypass active."; return true; 
    }
    
    return false;
}

void AudioDecoder::decodeLoop() {
    FILE* fileHandle = nullptr;
    if (currentFd >= 0) {
        fileHandle = fdopen(currentFd, "rb");
    } else {
        fileHandle = fopen(currentPath.c_str(), "rb");
    }

    if (!fileHandle) {
        g_statusMessage = (currentFd >= 0) ? "FFmpeg Err: Could not open FD via fdopen" : "FFmpeg Err: Could not open file via fopen";
        return;
    }

    const int avio_buffer_size = 32768;
    unsigned char* avio_buffer = (unsigned char*)av_malloc(avio_buffer_size);
    AVIOContext* avioContext = avio_alloc_context(avio_buffer, avio_buffer_size,
                                                  0, fileHandle,
                                                  &custom_read_packet, nullptr, &custom_seek_packet);
    if (!avioContext) {
        g_statusMessage = "FFmpeg Err: allocate AVIOContext failed";
        fclose(fileHandle);
        return;
    }

    AVFormatContext* formatContext = avformat_alloc_context();
    formatContext->pb = avioContext;

    if (avformat_open_input(&formatContext, nullptr, nullptr, nullptr) < 0) {
        g_statusMessage = "FFmpeg Err: avformat_open_input failed";
        av_free(avioContext->buffer);
        avio_context_free(&avioContext);
        fclose(fileHandle);
        return;
    }

    if (avformat_find_stream_info(formatContext, nullptr) < 0) {
        g_statusMessage = "FFmpeg Err: Stream info not found";
        avformat_close_input(&formatContext);
        av_free(avioContext->buffer);
        avio_context_free(&avioContext);
        fclose(fileHandle);
        return;
    }

    if (formatContext->duration != AV_NOPTS_VALUE) {
        totalDurationSeconds = static_cast<double>(formatContext->duration) / AV_TIME_BASE;
    } else {
        totalDurationSeconds = 0.0;
    }
    
    long br = formatContext->bit_rate;
    inputBitrate.store(br);

    int audioStreamIndex = -1;
    AVCodecParameters* codecParameters = nullptr;
    const AVCodec* codec = nullptr;

    for (unsigned int i = 0; i < formatContext->nb_streams; i++) {
        if (formatContext->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioStreamIndex = i;
            codecParameters = formatContext->streams[i]->codecpar;
            codec = avcodec_find_decoder(codecParameters->codec_id);
            break;
        }
    }

    if (audioStreamIndex == -1 || !codec) {
        g_statusMessage = "FFmpeg Err: Unsupported audio codec";
        avformat_close_input(&formatContext);
        av_free(avioContext->buffer);
        avio_context_free(&avioContext);
        fclose(fileHandle);
        return;
    }

    if (br <= 0 && codecParameters->bit_rate > 0) {
        inputBitrate.store(codecParameters->bit_rate);
    }

    AVCodecContext* codecContext = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(codecContext, codecParameters);

    if (avcodec_open2(codecContext, codec, nullptr) < 0) {
        g_statusMessage = "FFmpeg Err: avcodec_open2 failed";
        avcodec_free_context(&codecContext);
        avformat_close_input(&formatContext);
        av_free(avioContext->buffer);
        avio_context_free(&avioContext);
        fclose(fileHandle);
        return;
    }
    
    int bits = codecContext->bits_per_raw_sample;
    if (bits <= 0 && codecParameters->bits_per_raw_sample > 0) bits = codecParameters->bits_per_raw_sample;
    if (bits <= 0) bits = av_get_bytes_per_sample(codecContext->sample_fmt) * 8;
    if (bits <= 0) bits = 16; // Minimum sane fallback constraint
    inputBitDepth.store(bits);

    // Detect Dolby Atmos
    bool atmosDetected = false;
    std::string codecName = codec->name;
    if (codecName.find("eac3") != std::string::npos || codecName.find("truehd") != std::string::npos) {
        // Technically Atmos is often contained in EAC3 (DD+) or TrueHD
        atmosDetected = true; 
    }
    // Also check metadata tags for explicit "Atmos" string
    AVDictionaryEntry *tag = av_dict_get(formatContext->metadata, "title", nullptr, AV_DICT_IGNORE_SUFFIX);
    if (tag && std::string(tag->value).find("Atmos") != std::string::npos) atmosDetected = true;
    
    isDolbyAtmos.store(atmosDetected);

    if (!initFilterGraph(codecContext, formatContext->streams[audioStreamIndex]->time_base)) {
        // Do not overwrite g_statusMessage! It contains the specific string trace.
        avcodec_free_context(&codecContext);
        avformat_close_input(&formatContext);
        av_free(avioContext->buffer);
        avio_context_free(&avioContext);
        fclose(fileHandle);
        return;
    }

    SwrContext* swrCtx = nullptr;
    auto rebuildSwr = [&]() {
        if (swrCtx) swr_free(&swrCtx);
        swrCtx = swr_alloc();
        if (!swrCtx) return false;

        double speedFactor = 1.0;
        int mode = currentEffectMode.load();
        if (mode == 1) speedFactor = 0.85; // Slowed
        if (mode == 2) speedFactor = 1.25; // Nightcore
        if (mode == 3) speedFactor = 0.90; // Lofi approximation

        currentSpeedFactor.store(static_cast<float>(speedFactor));

        av_opt_set_int(swrCtx, "in_sample_rate", static_cast<int>(codecContext->sample_rate * speedFactor), 0);
        av_opt_set_sample_fmt(swrCtx, "in_sample_fmt", codecContext->sample_fmt, 0);
        av_opt_set_int(swrCtx, "out_sample_rate", sampleRate, 0);
        av_opt_set_sample_fmt(swrCtx, "out_sample_fmt", AV_SAMPLE_FMT_FLT, 0);
        av_opt_set_chlayout(swrCtx, "in_chlayout", &codecContext->ch_layout, 0);
        
        AVChannelLayout outChLayout;
        av_channel_layout_default(&outChLayout, 2);
        av_opt_set_chlayout(swrCtx, "out_chlayout", &outChLayout, 0);

        return swr_init(swrCtx) >= 0;
    };

    if (!rebuildSwr()) {
        g_statusMessage = "DSP Fatal: SwrContext init failed";
        avcodec_free_context(&codecContext);
        avformat_close_input(&formatContext);
        av_free(avioContext->buffer);
        avio_context_free(&avioContext);
        fclose(fileHandle);
        return;
    }

    g_statusMessage = "DSP Engine: Ready";

    AVPacket* packet = av_packet_alloc();
    AVFrame* frame = av_frame_alloc();
    AVFrame* filt_frame = av_frame_alloc();
    bool reachedEof = false;
    int64_t next_pts = 0;
    int successfullyPushedFrames = 0;
    
    bool flushingFilters = false;
    while (isDecoding) {
        if (seekRequest.exchange(false)) {
            AVRational tb = formatContext->streams[audioStreamIndex]->time_base;
            int64_t targetTimestamp = static_cast<int64_t>(seekTarget.load() / av_q2d(tb));
            
            if (av_seek_frame(formatContext, audioStreamIndex, targetTimestamp, AVSEEK_FLAG_BACKWARD) < 0) {
                avformat_seek_file(formatContext, audioStreamIndex, INT64_MIN, targetTimestamp, INT64_MAX, 0);
            }
            
            avcodec_flush_buffers(codecContext);
            initFilterGraph(codecContext, tb);
            
            if (!rebuildSwr()) {
                LOGE("DSP: rebuildSwr failed during seek. swrCtx may be NULL.");
            }
            
            std::lock_guard<std::mutex> lock(bufferMutex);
            audioBuffer.clear();
            next_pts = targetTimestamp; // Sync next_pts to avoid jumps
            flushingFilters = false; // Reset flush on seek
        }

        if (pendingEffectChange.exchange(false)) {
            if (currentEffectMode.load() == 5) {
                LOGD("Activating Instrumental Focus: 180-phase center cancellation applied.");
            }
            initFilterGraph(codecContext, formatContext->streams[audioStreamIndex]->time_base);
            
            if (!rebuildSwr()) {
                LOGE("DSP: rebuildSwr failed during effect change.");
            }
            
            std::lock_guard<std::mutex> lock(bufferMutex);
            audioBuffer.clear();
            flushingFilters = false;
        }

        if (pendingEqUpdate.exchange(false)) {}

        if (!flushingFilters) {
            int ret = av_read_frame(formatContext, packet);
            if (ret == AVERROR_EOF) {
                // Signal EOF to filter graph
                (void)av_buffersrc_add_frame(bufferSrcCtx, nullptr);
                flushingFilters = true;
            } else if (ret < 0) {
                break; 
            }
        }

        if (flushingFilters || packet->stream_index == audioStreamIndex) {
            int ret = 0;
            if (!flushingFilters) {
                ret = avcodec_send_packet(codecContext, packet);
            }
            
            while (ret >= 0) {
                ret = avcodec_receive_frame(codecContext, frame);
                if (ret == AVERROR(EAGAIN)) {
                    break;
                } else if (ret == AVERROR_EOF) {
                    // Codec is flushed, but filter graph might still have data
                    break;
                } else if (ret < 0) {
                    break; // Error
                }
                
                frame->pts = next_pts;
                next_pts += frame->nb_samples;
                
                (void)av_buffersrc_add_frame(bufferSrcCtx, frame);

                while (true) {
                    int ret_sink = av_buffersink_get_frame(bufferSinkCtx, filt_frame);
                    if (ret_sink == AVERROR(EAGAIN)) break;
                    if (ret_sink == AVERROR_EOF) {
                        if (flushingFilters) reachedEof = true;
                        break;
                    } else if (ret_sink < 0) {
                        break;
                    }

                    int outSamples = filt_frame->nb_samples;
                    int maxOutSamples = 0;
                    if (swrCtx) {
                        maxOutSamples = swr_get_out_samples(swrCtx, outSamples);
                    }
                    if (maxOutSamples <= 0) maxOutSamples = outSamples * 2;
                    
                    std::vector<float> pcmData(maxOutSamples * channels);
                    uint8_t* out_data[1] = { (uint8_t*)pcmData.data() };
                    const uint8_t* in_data[8];
                    for (int i = 0; i < 8; i++) in_data[i] = filt_frame->data[i];
                    
                    int converted_samples = -1;
                    if (swrCtx) {
                        converted_samples = swr_convert(swrCtx, out_data, maxOutSamples, in_data, outSamples);
                    }
                    
                    av_frame_unref(filt_frame);
                    
                    if (converted_samples < 0) break;
                    
                    pcmData.resize(converted_samples * channels);

                    // Master Hack 2: Native C++ 3D Spatial Audio & 8D Panning
                    if (currentEffectMode.load() == 4 && channels == 2) {
                        static double panPhase = 0.0;
                        double frequency = 0.125; 
                        double phaseIncrement = (2.0 * M_PI * frequency) / sampleRate;
                        
                        for (int i = 0; i < converted_samples; i++) {
                            float origL = pcmData[i * 2];
                            float origR = pcmData[i * 2 + 1];
                            float mid = (origL + origR) * 0.5f;
                            float side = (origL - origR) * 0.5f;
                            side *= 1.2f;
                            origL = mid + side;
                            origR = mid - side;
                            float raw_lfo = (std::sin(panPhase) + 1.0f) * 0.5f;
                            float p = 0.2f + (raw_lfo * 0.6f); 
                            float leftGain = std::cos(p * (M_PI / 2.0f));
                            float rightGain = std::sin(p * (M_PI / 2.0f));
                            pcmData[i * 2]     = origL * leftGain;
                            pcmData[i * 2 + 1] = origR * rightGain;
                            panPhase += phaseIncrement;
                            if (panPhase > 2.0 * M_PI) panPhase -= 2.0 * M_PI;
                        }
                    }

                    // Master Hack 4: Native C++ Instrumental Focus (Vocal Suppressor)
                     if (currentEffectMode.load() == 5 && channels == 2) {
                        for (int i = 0; i < converted_samples; i++) {
                            float sampleL = pcmData[i * 2];
                            float sampleR = pcmData[i * 2 + 1];
                            float canceled = (sampleL - sampleR) * 0.5f;
                            pcmData[i * 2] = canceled;
                            pcmData[i * 2 + 1] = canceled;
                        }
                    }

                    // Master Hack 3: Native Stutter-Free Equalizer 
                    static float freqs[5] = {60.0f, 230.0f, 910.0f, 3600.0f, 14000.0f};
                    bool eqChanged = false;
                    for (int b = 0; b < 5; b++) {
                        float targetGain = eqBands[b].load();
                        if (targetGain != currentGains[b]) {
                            currentGains[b] = targetGain;
                            eqChanged = true;
                        }
                    }
                    if (eqChanged) {
                        for (int b = 0; b < 5; b++) {
                            eqFilters[0][b].calculatePeaking(freqs[b], sampleRate, currentGains[b], 1.5f);
                            eqFilters[1][b].calculatePeaking(freqs[b], sampleRate, currentGains[b], 1.5f);
                        }
                    }

                    bool needsEq = eqEnabled.load();
                    if (needsEq) {
                        bool anyGain = false;
                        for (int b = 0; b < 5; b++) if (currentGains[b] != 0.0f) anyGain = true;
                        if (!anyGain) needsEq = false;
                    }

                    if (needsEq && channels == 2) {
                        for (int i = 0; i < converted_samples; i++) {
                            float sampleL = pcmData[i * 2];
                            float sampleR = pcmData[i * 2 + 1];
                            for (int b = 0; b < 5; b++) {
                                sampleL = eqFilters[0][b].process(sampleL);
                                sampleR = eqFilters[1][b].process(sampleR);
                            }
                            if (sampleL > 1.0f) sampleL = 1.0f; else if (sampleL < -1.0f) sampleL = -1.0f;
                            if (sampleR > 1.0f) sampleR = 1.0f; else if (sampleR < -1.0f) sampleR = -1.0f;
                            pcmData[i * 2] = sampleL;
                            pcmData[i * 2 + 1] = sampleR;
                        }
                    }

                    // Peak Amplitude Tracking
                    float maxBatchAmp = 0.0f;
                    for (int i = 0; i < converted_samples * channels; i++) {
                        float absVal = std::abs(pcmData[i]);
                        if (absVal > maxBatchAmp) maxBatchAmp = absVal;
                    }
                    float prevAmp = currentAmplitude.load();
                    if (maxBatchAmp > prevAmp) currentAmplitude.store(maxBatchAmp);

                    while (isDecoding) {
                        if (seekRequest.load()) break;
                        bool bufferFull = false;
                        {
                            std::lock_guard<std::mutex> lock(bufferMutex);
                            if (audioBuffer.size() > static_cast<size_t>(sampleRate * channels * 2)) bufferFull = true;
                        }
                        if (bufferFull) std::this_thread::sleep_for(std::chrono::milliseconds(10));
                        else break;
                    }

                    if (isDecoding && !seekRequest.load()) {
                        std::lock_guard<std::mutex> lock(bufferMutex);
                        audioBuffer.insert(audioBuffer.end(), pcmData.begin(), pcmData.end());
                    }
                }
            }

            // --- Robust Flush Sweep ---
            if (flushingFilters && !reachedEof) {
                while (true) {
                    int ret_sink = av_buffersink_get_frame(bufferSinkCtx, filt_frame);
                    if (ret_sink == AVERROR(EAGAIN)) break;
                    if (ret_sink == AVERROR_EOF) {
                        reachedEof = true;
                        break;
                    }
                    
                    // Duplicate minimal PCM processing here for flushed frames
                    int outSamples = filt_frame->nb_samples;
                    int maxOutSamples = swrCtx ? swr_get_out_samples(swrCtx, outSamples) : outSamples * 2;
                    if (maxOutSamples <= 0) maxOutSamples = outSamples * 2;
                    std::vector<float> pcmData(maxOutSamples * channels);
                    uint8_t* out_data[1] = { (uint8_t*)pcmData.data() };
                    const uint8_t* in_data[8];
                    for (int i = 0; i < 8; i++) in_data[i] = filt_frame->data[i];
                    int converted_samples = swrCtx ? swr_convert(swrCtx, out_data, maxOutSamples, in_data, outSamples) : -1;
                    av_frame_unref(filt_frame);
                    if (converted_samples > 0) {
                        pcmData.resize(converted_samples * channels);
                        std::lock_guard<std::mutex> lock(bufferMutex);
                        audioBuffer.insert(audioBuffer.end(), pcmData.begin(), pcmData.end());
                    } else break;
                }
            }
        }
        
        if (reachedEof) {
            reachedEofFlag = true;
            g_statusMessage = "DSP: Reached EOF organically and flushed.";
            break;
        }
        if (!flushingFilters) av_packet_unref(packet);
    }

    av_frame_free(&filt_frame);
    av_frame_free(&frame);
    av_packet_free(&packet);
    
    if (swrCtx) swr_free(&swrCtx);
    if (filterGraph) {
        avfilter_graph_free(&filterGraph);
    }
    avcodec_free_context(&codecContext);
    avformat_close_input(&formatContext);
    av_free(avioContext->buffer);
    avio_context_free(&avioContext);
    fclose(fileHandle);
}
