package com.example.model

import android.graphics.Bitmap

enum class RifeModel(
    val id: String,
    val displayName: String,
    val arabicBadge: String,
    val description: String,
    val supportsArbitraryTimestep: Boolean,
    val isAnimeOptimized: Boolean,
    val defaultUhdRecommended: Boolean
) {
    RIFE_V4_6(
        id = "rife-v4.6",
        displayName = "RIFE v4.6 (IFNet HD)",
        arabicBadge = "واقعي • الأحدث",
        description = "النموذج القياسي للفيديوهات الواقعية مع دعم الطوابع الزمنية المتغيرة (Dynamic Timestep t∈[0,1]) وتنعيم الحركة السينمائية.",
        supportsArbitraryTimestep = true,
        isAnimeOptimized = false,
        defaultUhdRecommended = false
    ),
    RIFE_ANIME(
        id = "rife-anime",
        displayName = "RIFE-Anime (2D / Cel-Shaded)",
        arabicBadge = "أنمي ورسوم",
        description = "مُخصص للأنمي والرسوم المتحركة ثنائية الأبعاد؛ يحافظ على حدة الخطوط السوداء (Line Art) ويمنع تموه الحواف.",
        supportsArbitraryTimestep = true,
        isAnimeOptimized = true,
        defaultUhdRecommended = false
    ),
    RIFE_V4_15_LITE(
        id = "rife-v4.15-lite",
        displayName = "RIFE v4.15 Lite (Mobile Vulkan)",
        arabicBadge = "فائق السرعة",
        description = "نسخة خفيفة مهيأة لمعالجات الهواتف (Adreno / Mali Vulkan FP16) تمنح سرعة مضاعفة مع استهلاك طاقة أقل.",
        supportsArbitraryTimestep = true,
        isAnimeOptimized = false,
        defaultUhdRecommended = false
    ),
    RIFE_UHD(
        id = "rife-UHD",
        displayName = "RIFE-UHD (4K Flow Scaling)",
        arabicBadge = "دقة 4K / UHD",
        description = "مصمم للفيديوهات ذات الدقة العالية (1440p/4K) مع تحجيم حقول التدفق البصري (Optical Flow Downscaling).",
        supportsArbitraryTimestep = true,
        isAnimeOptimized = false,
        defaultUhdRecommended = true
    ),
    RIFE_V2_3(
        id = "rife-v2.3",
        displayName = "RIFE v2.3 (Classic Optical)",
        arabicBadge = "كلاسيكي دقيق",
        description = "الجيل الثاني الكلاسيكي من RIFE للمضاعفة الثنائية المتكررة (2x/4x/8x) مع وضع TTA.",
        supportsArbitraryTimestep = false,
        isAnimeOptimized = false,
        defaultUhdRecommended = false
    )
}

enum class InterpolationTarget(
    val id: String,
    val labelAr: String,
    val badge: String,
    val fixedMultiplier: Int?,
    val targetFps: Float?
) {
    MULT_2X("2x", "مضاعفة 2x", "2× FPS", 2, null),
    MULT_4X("4x", "مضاعفة 4x", "4× FPS", 4, null),
    MULT_8X("8x", "مضاعفة 8x", "8× FPS", 8, null),
    TARGET_60FPS("60fps", "استهداف 60 إطار/ث", "60 FPS", null, 60f),
    TARGET_120FPS("120fps", "استهداف 120 إطار/ث", "120 FPS", null, 120f)
}

enum class OutputTimingMode(
    val id: String,
    val titleAr: String,
    val subtitleAr: String
) {
    REALTIME_SMOOTH(
        id = "realtime_smooth",
        titleAr = "تنعيم الحركة (High FPS + Audio Passthru)",
        subtitleAr = "الحفاظ على مدة الفيديو الأصلية مع مضاعفة التردد والنسخ المباشر للصوت الأصلي بدون ضغط"
    ),
    SLOW_MOTION(
        id = "slow_motion",
        titleAr = "التصوير البطيء الذكي (AI Slow-Motion)",
        subtitleAr = "تمديد الطوابع الزمنية (PTS Stretch) لصناعة حركة بطيئة سينمائية فائقة النعومة"
    )
}

data class VulkanGpuDevice(
    val id: Int,
    val name: String,
    val vendor: String,
    val apiVersion: String,
    val computeQueueCount: Int,
    val supportsFp16: Boolean,
    val maxWorkgroupSize: Int
)

data class EngineSettings(
    val model: RifeModel = RifeModel.RIFE_V4_6,
    val target: InterpolationTarget = InterpolationTarget.MULT_2X,
    val timingMode: OutputTimingMode = OutputTimingMode.REALTIME_SMOOTH,
    val uhdMode: Boolean = false,
    val ttaMode: Boolean = false,
    val ttaTemporalMode: Boolean = false,
    val numThreads: Int = 4,
    val selectedGpuId: Int = 0,
    val sceneCutDetectionEnabled: Boolean = true,
    val sceneCutThreshold: Float = 0.30f,
    val audioPassthruEnabled: Boolean = true,
    val strictPtsRecalculation: Boolean = true,
    val thermalThrottleGuard: Boolean = true,
    val thermalCooldownMs: Long = 8L,
    val maxStreamingQueueDepth: Int = 2
)

data class VideoSourceMetadata(
    val uriString: String,
    val filePath: String?,
    val displayName: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val bitrate: Int = 8_000_000,
    val durationMs: Long,
    val sourceFps: Float,
    val totalFrames: Int,
    val hasAudioTrack: Boolean,
    val audioMimeType: String,
    val audioSampleRate: Int,
    val audioChannelCount: Int,
    val fileSizeBytes: Long,
    val isSyntheticDemo: Boolean = false
)

data class SceneCutEvent(
    val frameIndex: Int,
    val timestampMs: Long,
    val deltaScore: Float,
    val threshold: Float
)

data class FrameInspectionPreview(
    val frameIndex: Int,
    val frame0Bitmap: Bitmap,
    val frame1Bitmap: Bitmap,
    val interpolatedMidBitmap: Bitmap,
    val opticalFlowHeatmap: Bitmap,
    val sceneDeltaScore: Float,
    val isSceneCutTriggered: Boolean,
    val computeLatencyMs: Double
)

enum class JobStatus(val labelAr: String) {
    QUEUED("في الطابور"),
    RUNNING("قيد المعالجة (Streaming)"),
    PAUSED_THERMAL("تهدئة حرارية مؤقتة"),
    COMPLETED("مكتمل بنجاح"),
    CANCELLED("تم الإلغاء"),
    FAILED("فشل")
}

data class InterpolationJob(
    val id: String,
    val source: VideoSourceMetadata,
    val settings: EngineSettings,
    val status: JobStatus = JobStatus.QUEUED,
    val currentInputFrame: Int = 0,
    val totalInputFrames: Int = source.totalFrames,
    val generatedOutputFrames: Int = 0,
    val estimatedTotalOutputFrames: Int = source.totalFrames * 2,
    val effectiveTargetFps: Float = source.sourceFps * 2f,
    val processingFps: Float = 0f,
    val etaSeconds: Int = 0,
    val elapsedMs: Long = 0L,
    val sceneCutsAvoided: Int = 0,
    val sceneCutEvents: List<SceneCutEvent> = emptyList(),
    val lastSceneDeltaScore: Float = 0f,
    val audioPacketsCopied: Int = 0,
    val lastCalculatedPtsUs: Long = 0L,
    val currentRamUsedMb: Int = 0,
    val peakRamUsedMb: Int = 0,
    val batteryTempCelsius: Float = 33.5f,
    val thermalStatusLabel: String = "Nominal (مستقر)",
    val outputFilePath: String? = null,
    val exportedMediaStoreUri: String? = null,
    val publicDisplayPath: String? = null,
    val outputFileSizeBytes: Long = 0L,
    val errorMessage: String? = null
)
