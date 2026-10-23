#ifndef FLOWRIFE_RIFE_ENGINE_H
#define FLOWRIFE_RIFE_ENGINE_H

#include <stdint.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/asset_manager.h>

#ifdef HAVE_NCNN_VULKAN
#include "net.h"
#include "gpu.h"
#include "layer.h"
#endif

namespace flowrife {

struct RifeEngineConfig {
    std::string modelName = "rife-v4.6";
    int gpuid = 0;
    int numThreads = 4;
    bool uhdMode = false;
    bool ttaMode = false;
    bool ttaTemporalMode = false;
    bool riemannianV4 = true;
    float sceneCutThreshold = 0.32f;
};

struct FrameTelemetry {
    float sceneDeltaScore = 0.0f;
    bool isSceneCut = false;
    double inferenceTimeMs = 0.0;
    int activeGpuId = 0;
};

class RifeEngine {
public:
    explicit RifeEngine(const RifeEngineConfig& config);
    ~RifeEngine();

    int loadModel(AAssetManager* assetManager, const std::string& modelDir);

    /**
     * Computes normalized scene-cut score [0.0, 1.0] using 64-bin Y-channel histogram
     * Jensen-Shannon / L1 divergence combined with strided block SAD.
     */
    float computeSceneCutScore(
        const uint8_t* rgba0,
        const uint8_t* rgba1,
        int width,
        int height,
        int strideBytes
    ) const;

    /**
     * Interpolates an intermediate RGBA8888 frame at arbitrary timestep t in (0.0, 1.0).
     * Automatically falls back to duplicating frame0 (or frame1 if t >= 0.5) when
     * sceneDeltaScore >= config.sceneCutThreshold to prevent morphing artifacts.
     */
    int processFrame(
        const uint8_t* rgba0,
        const uint8_t* rgba1,
        uint8_t* rgbaOut,
        int width,
        int height,
        int strideBytes,
        float timestep,
        FrameTelemetry* outTelemetry
    );

    const RifeEngineConfig& getConfig() const { return config_; }
    void updateConfig(const RifeEngineConfig& newConfig);

    static int getVulkanGpuCount();
    static std::string getVulkanGpuDeviceName(int gpuid);

private:
    void interpolateOpticalFlowCpuFallback(
        const uint8_t* rgba0,
        const uint8_t* rgba1,
        uint8_t* rgbaOut,
        int width,
        int height,
        int strideBytes,
        float timestep
    ) const;

    RifeEngineConfig config_;
    mutable std::mutex mutex_;
    bool modelLoaded_ = false;

#ifdef HAVE_NCNN_VULKAN
    ncnn::Net flownet_;
    ncnn::VulkanDevice* vkdev_ = nullptr;
    ncnn::Pipeline* pipe_preproc_ = nullptr;
    ncnn::Pipeline* pipe_postproc_ = nullptr;
    ncnn::Pipeline* pipe_flow_tta_ = nullptr;
#endif
};

} // namespace flowrife

#endif // FLOWRIFE_RIFE_ENGINE_H
