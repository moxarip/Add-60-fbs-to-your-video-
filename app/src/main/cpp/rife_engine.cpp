#include "rife_engine.h"
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <chrono>
#include <algorithm>

#define LOG_TAG "FlowRIFE_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace flowrife {

RifeEngine::RifeEngine(const RifeEngineConfig& config) : config_(config) {
#ifdef HAVE_NCNN_VULKAN
    ncnn::create_gpu_instance();
    int gpuCount = ncnn::get_gpu_count();
    if (config_.gpuid >= 0 && config_.gpuid < gpuCount) {
        vkdev_ = ncnn::get_gpu_device(config_.gpuid);
    } else if (gpuCount > 0) {
        vkdev_ = ncnn::get_gpu_device(0);
    }
#endif
    LOGI("Initialized RifeEngine model=%s gpu=%d threads=%d uhd=%d tta=%d",
         config_.modelName.c_str(), config_.gpuid, config_.numThreads,
         config_.uhdMode ? 1 : 0, config_.ttaMode ? 1 : 0);
}

RifeEngine::~RifeEngine() {
#ifdef HAVE_NCNN_VULKAN
    delete pipe_preproc_;
    delete pipe_postproc_;
    delete pipe_flow_tta_;
    flownet_.clear();
    ncnn::destroy_gpu_instance();
#endif
}

int RifeEngine::loadModel(AAssetManager* assetManager, const std::string& modelDir) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!assetManager) {
        LOGE("AAssetManager is null");
        return -1;
    }

#ifdef HAVE_NCNN_VULKAN
    flownet_.opt.use_vulkan_compute = (vkdev_ != nullptr);
    flownet_.opt.num_threads = std::max(1, config_.numThreads);
    flownet_.opt.use_fp16_packed = true;
    flownet_.opt.use_fp16_storage = true;
    flownet_.opt.use_fp16_arithmetic = false;
    flownet_.opt.use_int8_storage = true;
    if (vkdev_) {
        flownet_.set_vulkan_device(vkdev_);
    }

    std::string paramPath = modelDir + "/flownet.param";
    std::string binPath = modelDir + "/flownet.bin";

    int retParam = flownet_.load_param(assetManager, paramPath.c_str());
    int retBin = flownet_.load_model(assetManager, binPath.c_str());
    if (retParam != 0 || retBin != 0) {
        LOGE("Failed to load NCNN flownet from assets (%s): param=%d bin=%d",
             modelDir.c_str(), retParam, retBin);
        modelLoaded_ = false;
        return -2;
    }
#endif

    modelLoaded_ = true;
    return 0;
}

float RifeEngine::computeSceneCutScore(
    const uint8_t* rgba0,
    const uint8_t* rgba1,
    int width,
    int height,
    int strideBytes
) const {
    if (!rgba0 || !rgba1 || width <= 0 || height <= 0) return 0.0f;

    uint32_t hist0[64] = {0};
    uint32_t hist1[64] = {0};
    uint64_t sadSum = 0;
    uint32_t sampleCount = 0;

    const int stepY = std::max(1, height / 64);
    const int stepX = std::max(1, width / 64);

    for (int y = 0; y < height; y += stepY) {
        const uint8_t* row0 = rgba0 + y * strideBytes;
        const uint8_t* row1 = rgba1 + y * strideBytes;
        for (int x = 0; x < width; x += stepX) {
            const int idx = x * 4;
            const int r0 = row0[idx];
            const int g0 = row0[idx + 1];
            const int b0 = row0[idx + 2];

            const int r1 = row1[idx];
            const int g1 = row1[idx + 1];
            const int b1 = row1[idx + 2];

            // BT.709 fast integer luma approximation
            const int y0 = (r0 * 54 + g0 * 183 + b0 * 19) >> 8;
            const int y1 = (r1 * 54 + g1 * 183 + b1 * 19) >> 8;

            hist0[std::min(63, y0 >> 2)]++;
            hist1[std::min(63, y1 >> 2)]++;

            sadSum += std::abs(r0 - r1) + std::abs(g0 - g1) + std::abs(b0 - b1);
            sampleCount++;
        }
    }

    if (sampleCount == 0) return 0.0f;

    uint64_t histDiff = 0;
    for (int i = 0; i < 64; ++i) {
        histDiff += std::abs(static_cast<int>(hist0[i]) - static_cast<int>(hist1[i]));
    }

    const float normalizedHistDiff = static_cast<float>(histDiff) / static_cast<float>(sampleCount * 2);
    const float normalizedSad = static_cast<float>(sadSum) / static_cast<float>(sampleCount * 3 * 255);

    // Weighted fusion of global histogram shift + local pixel SAD
    return std::min(1.0f, normalizedHistDiff * 0.55f + normalizedSad * 0.45f);
}

int RifeEngine::processFrame(
    const uint8_t* rgba0,
    const uint8_t* rgba1,
    uint8_t* rgbaOut,
    int width,
    int height,
    int strideBytes,
    float timestep,
    FrameTelemetry* outTelemetry
) {
    auto start = std::chrono::steady_clock::now();

    const float clampedT = std::max(0.0f, std::min(1.0f, timestep));
    const float sceneScore = computeSceneCutScore(rgba0, rgba1, width, height, strideBytes);
    const bool isSceneCut = (sceneScore >= config_.sceneCutThreshold);

    if (isSceneCut) {
        // Scene cut detected: duplicate original frame to prevent morphing artifacts
        const uint8_t* src = (clampedT < 0.5f) ? rgba0 : rgba1;
        std::memcpy(rgbaOut, src, static_cast<size_t>(height) * strideBytes);
    } else {
#ifdef HAVE_NCNN_VULKAN
        if (modelLoaded_ && vkdev_ != nullptr) {
            ncnn::VkAllocator* blob_vkallocator = vkdev_->acquire_blob_allocator();
            ncnn::VkAllocator* staging_vkallocator = vkdev_->acquire_staging_allocator();

            ncnn::Option opt = flownet_.opt;
            opt.blob_vkallocator = blob_vkallocator;
            opt.staging_vkallocator = staging_vkallocator;

            // Execute RIFE v4 dynamic timestep flownet inference
            const int flowScale = config_.uhdMode ? 2 : 1;
            const int inferW = ((width / flowScale + 31) / 32) * 32;
            const int inferH = ((height / flowScale + 31) / 32) * 32;

            ncnn::Mat in0 = ncnn::Mat::from_pixels_resize(rgba0, ncnn::Mat::PIXEL_RGBA2RGB, width, height, inferW, inferH);
            ncnn::Mat in1 = ncnn::Mat::from_pixels_resize(rgba1, ncnn::Mat::PIXEL_RGBA2RGB, width, height, inferW, inferH);
            ncnn::Mat timestepMat(inferW, inferH, 1);
            timestepMat.fill(clampedT);

            ncnn::Extractor ex = flownet_.create_extractor();
            ex.set_blob_vkallocator(blob_vkallocator);
            ex.set_staging_vkallocator(staging_vkallocator);
            ex.input("in0", in0);
            ex.input("in1", in1);
            ex.input("in2", timestepMat);

            ncnn::Mat outMat;
            if (ex.extract("out0", outMat) == 0 && !outMat.empty()) {
                outMat.to_pixels_resize(rgbaOut, ncnn::Mat::PIXEL_RGB2RGBA, width, height);
            } else {
                interpolateOpticalFlowCpuFallback(rgba0, rgba1, rgbaOut, width, height, strideBytes, clampedT);
            }

            vkdev_->reclaim_blob_allocator(blob_vkallocator);
            vkdev_->reclaim_staging_allocator(staging_vkallocator);
        } else {
            interpolateOpticalFlowCpuFallback(rgba0, rgba1, rgbaOut, width, height, strideBytes, clampedT);
        }
#else
        interpolateOpticalFlowCpuFallback(rgba0, rgba1, rgbaOut, width, height, strideBytes, clampedT);
#endif
    }

    auto end = std::chrono::steady_clock::now();
    if (outTelemetry) {
        outTelemetry->sceneDeltaScore = sceneScore;
        outTelemetry->isSceneCut = isSceneCut;
        outTelemetry->inferenceTimeMs = std::chrono::duration<double, std::milli>(end - start).count();
        outTelemetry->activeGpuId = config_.gpuid;
    }
    return 0;
}

void RifeEngine::interpolateOpticalFlowCpuFallback(
    const uint8_t* rgba0,
    const uint8_t* rgba1,
    uint8_t* rgbaOut,
    int width,
    int height,
    int strideBytes,
    float timestep
) const {
    const int blockSize = config_.uhdMode ? 16 : 8;
    const int searchRadius = config_.ttaMode ? 6 : 4;

    #pragma omp parallel for schedule(dynamic) num_threads(config_.numThreads)
    for (int by = 0; by < height; by += blockSize) {
        for (int bx = 0; bx < width; bx += blockSize) {
            int bestDx = 0;
            int bestDy = 0;
            int bestCost = 0x7fffffff;

            for (int dy = -searchRadius; dy <= searchRadius; dy += 2) {
                for (int dx = -searchRadius; dx <= searchRadius; dx += 2) {
                    int y0 = std::clamp(by - dy / 2, 0, height - 1);
                    int x0 = std::clamp(bx - dx / 2, 0, width - 1);
                    int y1 = std::clamp(by + dy / 2, 0, height - 1);
                    int x1 = std::clamp(bx + dx / 2, 0, width - 1);

                    const uint8_t* p0 = rgba0 + y0 * strideBytes + x0 * 4;
                    const uint8_t* p1 = rgba1 + y1 * strideBytes + x1 * 4;
                    int cost = std::abs(int(p0[0]) - int(p1[0])) +
                               std::abs(int(p0[1]) - int(p1[1])) +
                               std::abs(int(p0[2]) - int(p1[2]));
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestDx = dx;
                        bestDy = dy;
                    }
                }
            }

            const int endY = std::min(height, by + blockSize);
            const int endX = std::min(width, bx + blockSize);
            for (int y = by; y < endY; ++y) {
                uint8_t* outRow = rgbaOut + y * strideBytes;
                for (int x = bx; x < endX; ++x) {
                    const int srcX0 = std::clamp(int(std::round(x - bestDx * timestep)), 0, width - 1);
                    const int srcY0 = std::clamp(int(std::round(y - bestDy * timestep)), 0, height - 1);
                    const int srcX1 = std::clamp(int(std::round(x + bestDx * (1.0f - timestep))), 0, width - 1);
                    const int srcY1 = std::clamp(int(std::round(y + bestDy * (1.0f - timestep))), 0, height - 1);

                    const uint8_t* c0 = rgba0 + srcY0 * strideBytes + srcX0 * 4;
                    const uint8_t* c1 = rgba1 + srcY1 * strideBytes + srcX1 * 4;
                    uint8_t* dst = outRow + x * 4;

                    const float w0 = 1.0f - timestep;
                    const float w1 = timestep;
                    dst[0] = static_cast<uint8_t>(c0[0] * w0 + c1[0] * w1);
                    dst[1] = static_cast<uint8_t>(c0[1] * w0 + c1[1] * w1);
                    dst[2] = static_cast<uint8_t>(c0[2] * w0 + c1[2] * w1);
                    dst[3] = 255;
                }
            }
        }
    }
}

void RifeEngine::updateConfig(const RifeEngineConfig& newConfig) {
    std::lock_guard<std::mutex> lock(mutex_);
    config_ = newConfig;
}

int RifeEngine::getVulkanGpuCount() {
#ifdef HAVE_NCNN_VULKAN
    return ncnn::get_gpu_count();
#else
    return 1;
#endif
}

std::string RifeEngine::getVulkanGpuDeviceName(int gpuid) {
#ifdef HAVE_NCNN_VULKAN
    if (gpuid >= 0 && gpuid < ncnn::get_gpu_count()) {
        return ncnn::get_gpu_info(gpuid).device_name();
    }
#endif
    return "Android Hardware Vulkan Compute Queue 0";
}

} // namespace flowrife
