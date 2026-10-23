#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include "rife_engine.h"

using namespace flowrife;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_engine_RifeNativeBridge_nativeCreateEngine(
    JNIEnv* env,
    jobject /* thiz */,
    jstring modelName,
    jint gpuid,
    jint numThreads,
    jboolean uhdMode,
    jboolean ttaMode,
    jboolean ttaTemporalMode,
    jfloat sceneCutThreshold
) {
    const char* modelChars = env->GetStringUTFChars(modelName, nullptr);
    RifeEngineConfig config;
    config.modelName = modelChars ? modelChars : "rife-v4.6";
    config.gpuid = gpuid;
    config.numThreads = numThreads;
    config.uhdMode = (uhdMode == JNI_TRUE);
    config.ttaMode = (ttaMode == JNI_TRUE);
    config.ttaTemporalMode = (ttaTemporalMode == JNI_TRUE);
    config.sceneCutThreshold = sceneCutThreshold;
    if (modelChars) {
        env->ReleaseStringUTFChars(modelName, modelChars);
    }

    auto* engine = new RifeEngine(config);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jint JNICALL
Java_com_example_engine_RifeNativeBridge_nativeLoadModel(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject assetManagerObj,
    jstring modelDir
) {
    auto* engine = reinterpret_cast<RifeEngine*>(handle);
    if (!engine) return -1;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManagerObj);
    const char* dirChars = env->GetStringUTFChars(modelDir, nullptr);
    std::string dirStr = dirChars ? dirChars : "rife-v4.6";
    if (dirChars) {
        env->ReleaseStringUTFChars(modelDir, dirChars);
    }

    return engine->loadModel(mgr, dirStr);
}

JNIEXPORT jfloat JNICALL
Java_com_example_engine_RifeNativeBridge_nativeInterpolateBitmaps(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject bitmap0,
    jobject bitmap1,
    jobject bitmapOut,
    jfloat timestep
) {
    auto* engine = reinterpret_cast<RifeEngine*>(handle);
    if (!engine) return -1.0f;

    AndroidBitmapInfo info0, info1, infoOut;
    if (AndroidBitmap_getInfo(env, bitmap0, &info0) < 0 ||
        AndroidBitmap_getInfo(env, bitmap1, &info1) < 0 ||
        AndroidBitmap_getInfo(env, bitmapOut, &infoOut) < 0) {
        return -2.0f;
    }

    void* pixels0 = nullptr;
    void* pixels1 = nullptr;
    void* pixelsOut = nullptr;

    if (AndroidBitmap_lockPixels(env, bitmap0, &pixels0) < 0) return -3.0f;
    if (AndroidBitmap_lockPixels(env, bitmap1, &pixels1) < 0) {
        AndroidBitmap_unlockPixels(env, bitmap0);
        return -4.0f;
    }
    if (AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut) < 0) {
        AndroidBitmap_unlockPixels(env, bitmap1);
        AndroidBitmap_unlockPixels(env, bitmap0);
        return -5.0f;
    }

    FrameTelemetry telemetry{};
    engine->processFrame(
        static_cast<const uint8_t*>(pixels0),
        static_cast<const uint8_t*>(pixels1),
        static_cast<uint8_t*>(pixelsOut),
        static_cast<int>(info0.width),
        static_cast<int>(info0.height),
        static_cast<int>(info0.stride),
        timestep,
        &telemetry
    );

    AndroidBitmap_unlockPixels(env, bitmapOut);
    AndroidBitmap_unlockPixels(env, bitmap1);
    AndroidBitmap_unlockPixels(env, bitmap0);

    return telemetry.sceneDeltaScore;
}

JNIEXPORT void JNICALL
Java_com_example_engine_RifeNativeBridge_nativeDestroyEngine(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto* engine = reinterpret_cast<RifeEngine*>(handle);
    delete engine;
}

JNIEXPORT jint JNICALL
Java_com_example_engine_RifeNativeBridge_nativeGetVulkanGpuCount(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return RifeEngine::getVulkanGpuCount();
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_RifeNativeBridge_nativeGetVulkanGpuName(
    JNIEnv* env,
    jobject /* thiz */,
    jint gpuid
) {
    std::string name = RifeEngine::getVulkanGpuDeviceName(gpuid);
    return env->NewStringUTF(name.c_str());
}

} // extern "C"
