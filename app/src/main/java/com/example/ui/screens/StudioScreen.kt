package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.VideoInterpolationPipeline
import com.example.model.EngineSettings
import com.example.model.FrameInspectionPreview
import com.example.model.InterpolationTarget
import com.example.model.OutputTimingMode
import com.example.model.RifeModel
import com.example.model.VideoSourceMetadata
import com.example.model.VulkanGpuDevice
import com.example.ui.theme.AmberSceneCut
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.OpticalViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VulkanCyan
import java.util.Locale

@Composable
fun StudioScreen(
    source: VideoSourceMetadata?,
    settings: EngineSettings,
    gpuDevices: List<VulkanGpuDevice>,
    framePreview: FrameInspectionPreview?,
    inspectedFrameIndex: Int,
    isGeneratingPreview: Boolean,
    onPickVideoClick: () -> Unit,
    onGenerateDemoVideoClick: () -> Unit,
    onInspectFrameChange: (Int) -> Unit,
    onJumpToSceneCut: () -> Unit,
    onJumpToSmoothMotion: () -> Unit,
    onUpdateSettings: ((EngineSettings) -> EngineSettings) -> Unit,
    onStartJobClick: () -> Unit
) {
    val activeGpu = gpuDevices.firstOrNull { it.id == settings.selectedGpuId } ?: gpuDevices.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Vulkan Compute Studio Header
        StudioHeroHeader(
            activeGpuName = activeGpu?.name ?: "Vulkan Compute GPU 0",
            model = settings.model,
            uhdEnabled = settings.uhdMode,
            ttaEnabled = settings.ttaMode
        )

        // 2. Source Video Card + Import / Real MP4 Benchmark Generator
        VideoSourceCard(
            source = source,
            settings = settings,
            onPickVideoClick = onPickVideoClick,
            onGenerateDemoVideoClick = onGenerateDemoVideoClick
        )

        // 3. Interactive Optical Flow & Scene Cut Inspector
        if (source != null) {
            SceneCutAndFlowInspectorCard(
                source = source,
                settings = settings,
                preview = framePreview,
                inspectedFrameIndex = inspectedFrameIndex,
                isLoading = isGeneratingPreview,
                onFrameIndexChanged = onInspectFrameChange,
                onJumpToSceneCut = onJumpToSceneCut,
                onJumpToSmoothMotion = onJumpToSmoothMotion
            )
        }

        // 4. RIFE Model & Target Multiplier Selector
        InterpolationQuickConfigCard(
            settings = settings,
            onUpdateSettings = onUpdateSettings
        )

        // 5. Primary Launch Action Button
        Button(
            onClick = onStartJobClick,
            enabled = source != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_interpolation_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VulkanCyan,
                contentColor = ObsidianBg
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Start Interpolation",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val mult = if (source != null) {
                VideoInterpolationPipeline.computeEffectiveMultiplier(source.sourceFps, settings.target)
            } else 2
            Text(
                text = "بدء مضاعفة الإطارات (${settings.model.id} • ${mult}x) في الطابور",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StudioHeroHeader(
    activeGpuName: String,
    model: RifeModel,
    uhdEnabled: Boolean,
    ttaEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, VulkanCyan.copy(alpha = 0.35f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Custom Canvas Optical Flow Vector Background
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {
                val w = size.width
                val h = size.height
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B1528),
                            Color(0xFF141E38),
                            Color(0xFF0A1120)
                        ),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                )
                // Draw subtle optical flow grid lines
                val step = 44.dp.toPx()
                var x = step
                while (x < w) {
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x - 24f, h),
                        strokeWidth = 1.5f
                    )
                    x += step
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.3f),
                        radius = h * 0.9f
                    ),
                    center = Offset(w * 0.85f, h * 0.3f),
                    radius = h * 0.9f
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = VulkanCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, VulkanCyan.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = VulkanCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "NCNN VULKAN GPU PIPELINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = VulkanCyan
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (uhdEnabled) {
                            BadgePill(text = "UHD 4K", color = OpticalViolet)
                        }
                        if (ttaEnabled) {
                            BadgePill(text = "TTA×8", color = EmeraldSync)
                        }
                        BadgePill(text = model.arabicBadge, color = VulkanCyan)
                    }
                }

                Text(
                    text = "Flowframes × RIFE NCNN Vulkan",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "مضاعفة إطارات الفيديو وصناعة الـ Slow-Motion بالذكاء الاصطناعي مع كشف قطع المشاهد ومزامنة الصوت الصارمة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "GPU: $activeGpuName",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSync,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoSourceCard(
    source: VideoSourceMetadata?,
    settings: EngineSettings,
    onPickVideoClick: () -> Unit,
    onGenerateDemoVideoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = VulkanCyan
                    )
                    Text(
                        text = "مصدر الفيديو (Input Video Stream)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                if (source != null) {
                    val mult = VideoInterpolationPipeline.computeEffectiveMultiplier(source.sourceFps, settings.target)
                    val outFps = when (settings.target) {
                        InterpolationTarget.TARGET_60FPS -> 60f
                        InterpolationTarget.TARGET_120FPS -> 120f
                        else -> source.sourceFps * mult
                    }
                    BadgePill(
                        text = "${source.sourceFps.toInt()} FPS ➔ ${outFps.toInt()} FPS",
                        color = EmeraldSync
                    )
                }
            }

            if (source != null) {
                Surface(
                    color = ObsidianCardElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = source.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = VulkanCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TelemetryStatItem("الدقة", "${source.width}×${source.height}")
                            TelemetryStatItem("الإطارات", "${source.totalFrames} إطار")
                            TelemetryStatItem("التردد الأصلي", "${source.sourceFps.toInt()} fps")
                            TelemetryStatItem(
                                "مسار الصوت",
                                if (source.hasAudioTrack) "AAC Passthru" else "بدون صوت"
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickVideoClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpticalViolet,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "اختيار فيديو",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onGenerateDemoVideoClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("generate_demo_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VulkanCyan.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = VulkanCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "فيديو اختباري (24fps)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VulkanCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneCutAndFlowInspectorCard(
    source: VideoSourceMetadata,
    settings: EngineSettings,
    preview: FrameInspectionPreview?,
    inspectedFrameIndex: Int,
    isLoading: Boolean,
    onFrameIndexChanged: (Int) -> Unit,
    onJumpToSceneCut: () -> Unit,
    onJumpToSmoothMotion: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(
            1.dp,
            if (preview?.isSceneCutTriggered == true) AmberSceneCut else BorderSubtle
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = if (preview?.isSceneCutTriggered == true) AmberSceneCut else VulkanCyan
                    )
                    Column {
                        Text(
                            text = "معاينة التدفق البصري وكشف قطع المشاهد",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Optical Flow & Scene Cut Morphing Guard (الإطار $inspectedFrameIndex ➔ ${inspectedFrameIndex + 1})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Quick Jump Buttons to test Smooth Motion vs Scene Cut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onJumpToSmoothMotion,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("jump_smooth_motion_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VulkanCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "لقطة حركة مستمرة (#10)",
                        style = MaterialTheme.typography.labelSmall,
                        color = VulkanCyan
                    )
                }

                OutlinedButton(
                    onClick = onJumpToSceneCut,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("jump_scene_cut_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberSceneCut.copy(alpha = 0.7f))
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = AmberSceneCut,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "اختبار قطع المشهد (#${(source.totalFrames / 2) - 1})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberSceneCut
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = VulkanCyan
                )
            }

            if (preview != null) {
                // 2x2 Visual Frame Grid: Frame[t], Interpolated[t+0.5], Frame[t+1], Optical Flow Heatmap
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewFrameTile(
                        title = "الإطار الأصلي [${preview.frameIndex}]",
                        badge = "Source t=0.0",
                        bitmap = preview.frame0Bitmap,
                        badgeColor = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    PreviewFrameTile(
                        title = if (preview.isSceneCutTriggered) {
                            "حماية قطع المشهد (مُكرر)"
                        } else {
                            "إطار RIFE المولّد [t=0.5]"
                        },
                        badge = if (preview.isSceneCutTriggered) "SCENE CUT GUARD" else "AI INTERPOLATED",
                        bitmap = preview.interpolatedMidBitmap,
                        badgeColor = if (preview.isSceneCutTriggered) AmberSceneCut else VulkanCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewFrameTile(
                        title = "الإطار التالي [${preview.frameIndex + 1}]",
                        badge = "Source t=1.0",
                        bitmap = preview.frame1Bitmap,
                        badgeColor = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    PreviewFrameTile(
                        title = "خريطة التدفق البصري (Flow)",
                        badge = String.format(Locale.US, "%.1f ms", preview.computeLatencyMs),
                        bitmap = preview.opticalFlowHeatmap,
                        badgeColor = OpticalViolet,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Scene Cut Score Telemetry Bar
                val deltaPct = (preview.sceneDeltaScore * 100f).toInt()
                val threshPct = (settings.sceneCutThreshold * 100f).toInt()
                Surface(
                    color = if (preview.isSceneCutTriggered) {
                        AmberSceneCut.copy(alpha = 0.14f)
                    } else {
                        EmeraldSync.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (preview.isSceneCutTriggered) AmberSceneCut else EmeraldSync.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (preview.isSceneCutTriggered) {
                                Icons.Default.WarningAmber
                            } else {
                                Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = if (preview.isSceneCutTriggered) AmberSceneCut else EmeraldSync
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (preview.isSceneCutTriggered) {
                                    "تم رصد قطع مشهد مفاجئ ($deltaPct% ≥ الحد $threshPct%) — تم منع توليد إطار مشوه!"
                                } else {
                                    "حركة متصلة طبيعية (نسبة التغير اللحظي $deltaPct% < الحد $threshPct%) — توليد ذكي نشط"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (preview.isSceneCutTriggered) AmberSceneCut else EmeraldSync
                            )
                            Text(
                                text = "خوارزمية L1 Luminance Histogram + Block SAD تمنع الذوبان البصري (Morphing Artifacts) عند انتقال الكاميرا.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Frame Scrubber Slider
            if (source.totalFrames > 2) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "شريط فحص الإطارات (Frame Scrubber)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${inspectedFrameIndex} / ${source.totalFrames - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VulkanCyan
                        )
                    }
                    Slider(
                        value = inspectedFrameIndex.toFloat(),
                        onValueChange = { onFrameIndexChanged(it.toInt()) },
                        valueRange = 0f..(source.totalFrames - 2).coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = VulkanCyan,
                            activeTrackColor = VulkanCyan
                        ),
                        modifier = Modifier.testTag("frame_scrubber_slider")
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewFrameTile(
    title: String,
    badge: String,
    bitmap: android.graphics.Bitmap,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianCardElevated)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                color = ObsidianBg.copy(alpha = 0.78f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InterpolationQuickConfigCard(
    settings: EngineSettings,
    onUpdateSettings: ((EngineSettings) -> EngineSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Model Selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = VulkanCyan
                )
                Text(
                    text = "نموذج الذكاء الاصطناعي (RIFE NCNN Model)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RifeModel.entries.forEach { model ->
                    val selected = settings.model == model
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdateSettings {
                                it.copy(
                                    model = model,
                                    uhdMode = if (model.defaultUhdRecommended) true else it.uhdMode
                                )
                            }
                        },
                        label = {
                            Text(
                                text = "${model.id} (${model.arabicBadge})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VulkanCyan.copy(alpha = 0.22f),
                            selectedLabelColor = VulkanCyan
                        ),
                        modifier = Modifier.testTag("model_chip_${model.id}")
                    )
                }
            }

            Text(
                text = settings.model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Target Multiplier / FPS Selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = OpticalViolet
                )
                Text(
                    text = "معامل المضاعفة أو التردد المستهدف (Multiplier / Target FPS)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InterpolationTarget.entries.forEach { target ->
                    val selected = settings.target == target
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(target = target) } },
                        label = {
                            Text(
                                text = "${target.labelAr} (${target.badge})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OpticalViolet.copy(alpha = 0.25f),
                            selectedLabelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("target_chip_${target.id}")
                    )
                }
            }

            // Output Timing Mode (Smooth High-FPS with Audio Passthru vs AI Slow-Motion)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SlowMotionVideo,
                    contentDescription = null,
                    tint = EmeraldSync
                )
                Text(
                    text = "نمط الإخراج الزمني والصوت (Timing & Audio Sync)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputTimingMode.entries.forEach { mode ->
                    val isSelected = settings.timingMode == mode
                    Surface(
                        color = if (isSelected) ObsidianCardElevated else ObsidianBg.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) EmeraldSync else BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onUpdateSettings { it.copy(timingMode = mode) } }
                            .testTag("timing_mode_${mode.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) EmeraldSync else BorderSubtle)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.titleAr,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) EmeraldSync else TextPrimary
                                )
                                Text(
                                    text = mode.subtitleAr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            if (mode == OutputTimingMode.REALTIME_SMOOTH) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = EmeraldSync
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgePill(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun TelemetryStatItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
    }
}
