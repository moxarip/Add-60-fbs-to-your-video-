cmake_minimum_required(VERSION 3.22.1)
project("rife_ncnn_jni" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -fvisibility=hidden -fexceptions")

# Enable ARM NEON / FP16 vectorization on arm64-v8a for fast YUV/RGB & SAD scene-cut kernels
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")
endif()

# OpenMP support for multi-threaded NCNN CPU fallback and scene-cut histogram analysis
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS} -fopenmp -static-openmp")
endif()

# Optional prebuilt ncnn-android-vulkan SDK integration
# Place ncnn-android-vulkan-<abi> under app/src/main/cpp/ncnn-android-vulkan/${ANDROID_ABI}
set(NCNN_INSTALL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn-android-vulkan/${ANDROID_ABI}")
if(EXISTS "${NCNN_INSTALL_DIR}/lib/cmake/ncnn/ncnnConfig.cmake")
    set(ncnn_DIR "${NCNN_INSTALL_DIR}/lib/cmake/ncnn")
    find_package(ncnn REQUIRED)
    add_definitions(-DHAVE_NCNN_VULKAN=1)
endif()

# Find Android NDK system libraries
find_library(ANDROID_LIB android)
find_library(LOG_LIB log)
find_library(JNIGRAPHICS_LIB jnigraphics)
find_library(MEDIANDK_LIB mediandk)
find_library(VULKAN_LIB vulkan)

add_library(
    rife_ncnn_jni
    SHARED
    rife_engine.cpp
    rife_ncnn_vulkan_jni.cpp
)

target_include_directories(
    rife_ncnn_jni
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(TARGET ncnn)
    target_link_libraries(
        rife_ncnn_jni
        PRIVATE
        ncnn
        ${VULKAN_LIB}
        ${ANDROID_LIB}
        ${LOG_LIB}
        ${JNIGRAPHICS_LIB}
        ${MEDIANDK_LIB}
    )
else()
    target_link_libraries(
        rife_ncnn_jni
        PRIVATE
        ${VULKAN_LIB}
        ${ANDROID_LIB}
        ${LOG_LIB}
        ${JNIGRAPHICS_LIB}
        ${MEDIANDK_LIB}
    )
endif()
