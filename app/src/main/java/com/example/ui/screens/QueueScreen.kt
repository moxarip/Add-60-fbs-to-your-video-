package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.VideoMediaStoreExporter
import com.example.model.InterpolationJob
import com.example.model.JobStatus
import com.example.ui.theme.AmberSceneCut
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonThermal
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
fun QueueScreen(
    jobs: List<InterpolationJob>,
    onStartNewDemoJob: () -> Unit,
    onCancelOrRemoveJob: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "طابور المهام والقياس اللحظي (Streaming Queue)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "CacheDir Staging ➔ MediaMuxer ➔ MediaStore (Movies/FlowRIFE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = VulkanCyan
                )
            }
            if (jobs.any { it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED }) {
                IconButton(
                    onClick = onClearCompleted,
                    modifier = Modifier.testTag("clear_completed_jobs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "مسح المكتملة",
                        tint = TextSecondary
                    )
                }
            }
        }

        if (jobs.isEmpty()) {
            EmptyQueueCard(onStartNewDemoJob = onStartNewDemoJob)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    JobTelemetryCard(
                        job = job,
                        onCancelOrRemove = { onCancelOrRemoveJob(job.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueCard(
    onStartNewDemoJob: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QueuePlayNext,
                contentDescription = null,
                tint = VulkanCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "طابور المعالجة فارغ حالياً",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "ابدأ مهمة جديدة من شاشة الاستوديو ليتم تصدير الفيديو النهائي مباشرة إلى المعرض العام في مجلد Movies/FlowRIFE.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Button(
                onClick = onStartNewDemoJob,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VulkanCyan,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_queue_start_button")
            ) {
                Icon(imageVector = Icons.Default.PlayCircleFilled, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تشغيل مهمة اختبارية فورية (24fps ➔ 48fps)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun JobTelemetryCard(
    job: InterpolationJob,
    onCancelOrRemove: () -> Unit
) {
    val context = LocalContext.current
    val progress = if (job.estimatedTotalOutputFrames > 0) {
        (job.generatedOutputFrames.toFloat() / job.estimatedTotalOutputFrames.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val borderColor = when (job.status) {
        JobStatus.RUNNING -> VulkanCyan
        JobStatus.COMPLETED -> EmeraldSync
        JobStatus.PAUSED_THERMAL -> AmberSceneCut
        JobStatus.FAILED, JobStatus.CANCELLED -> CrimsonThermal
        JobStatus.QUEUED -> BorderSubtle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("job_card_${job.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BadgePill(text = "#${job.id}", color = VulkanCyan)
                        BadgePill(text = job.status.labelAr, color = borderColor)
                        BadgePill(text = job.settings.model.id, color = OpticalViolet)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = job.source.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onCancelOrRemove,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cancel_job_${job.id}"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CrimsonThermal.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "إلغاء أو حذف",
                        tint = CrimsonThermal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (job.status == JobStatus.RUNNING) "إيقاف" else "حذف",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonThermal
                    )
                }
            }

            // Progress Bar + Percentage
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الإطارات المولّدة: ${job.generatedOutputFrames} / ${job.estimatedTotalOutputFrames} (${job.effectiveTargetFps.toInt()} FPS)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = VulkanCyan
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = borderColor,
                    trackColor = ObsidianCardElevated
                )
            }

            // Real-time Speedometer (FPS) + ETA + PTS + Scene Cuts Grid
            Surface(
                color = ObsidianCardElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LiveMetricBox(
                            icon = Icons.Default.Speed,
                            label = "سرعة المعالجة (FPS)",
                            value = String.format(Locale.US, "%.1f fps", job.processingFps),
                            tint = VulkanCyan
                        )
                        LiveMetricBox(
                            icon = Icons.Default.Timer,
                            label = "الوقت المتبقي (ETA)",
                            value = if (job.status == JobStatus.COMPLETED) "0s (مكتمل)" else "${job.etaSeconds} ثانية",
                            tint = EmeraldSync
                        )
                        LiveMetricBox(
                            icon = Icons.Default.ContentCut,
                            label = "قطع المشاهد المتفادى",
                            value = "${job.sceneCutsAvoided} لقطة",
                            tint = AmberSceneCut
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LiveMetricBox(
                            icon = Icons.Default.GraphicEq,
                            label = "الطابع الزمني (PTS)",
                            value = "${job.lastCalculatedPtsUs / 1000} ms",
                            tint = OpticalViolet
                        )
                        LiveMetricBox(
                            icon = Icons.Default.Memory,
                            label = "ذاكرة التدفق (RAM)",
                            value = "${job.currentRamUsedMb} MB (Peak ${job.peakRamUsedMb})",
                            tint = VulkanCyan
                        )
                        LiveMetricBox(
                            icon = Icons.Default.Thermostat,
                            label = "حرارة الجهاز",
                            value = String.format(Locale.US, "%.1f°C", job.batteryTempCelsius),
                            tint = if (job.batteryTempCelsius >= 41f) AmberSceneCut else EmeraldSync
                        )
                    }
                }
            }

            // Scene Cut Events Log (if any scene cuts were detected and guarded)
            if (job.sceneCutEvents.isNotEmpty()) {
                Surface(
                    color = AmberSceneCut.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberSceneCut.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "سجل حماية قطع المشاهد (Scene Cut Morphing Guard):",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberSceneCut
                        )
                        job.sceneCutEvents.take(3).forEach { cut ->
                            Text(
                                text = "• عند الإطار #${cut.frameIndex} (${cut.timestampMs}ms): التغير اللحظي ${(cut.deltaScore * 100).toInt()}% ≥ ${(cut.threshold * 100).toInt()}% ⬅ تم نسخ الإطار الأصلي لمنع التشوه.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Completed MediaStore Export Banner + Play & Share Action Buttons
            if (job.status == JobStatus.COMPLETED && (job.publicDisplayPath != null || job.outputFilePath != null)) {
                val fullPath = job.publicDisplayPath ?: job.outputFilePath.orEmpty()
                val fileNameOnly = fullPath.substringAfterLast('/')
                val mediaUriStr = job.exportedMediaStoreUri ?: fullPath

                Surface(
                    color = EmeraldSync.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldSync.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSync
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تم تصدير الفيديو إلى المعرض العام بنجاح (MediaStore API)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSync
                                )
                                Text(
                                    text = "الحجم: ${job.outputFileSizeBytes / 1024} KB • حزم الصوت المنسوخة: ${job.audioPacketsCopied} packet • MIME: video/mp4",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Full Public Path Box in Movies/FlowRIFE
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = VulkanCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "المسار العام في المعرض ومدير الملفات:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = fullPath,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = VulkanCyan
                                    )
                                }
                            }
                        }

                        // Action Buttons Row: "تشغيل ومعاينة" (ACTION_VIEW) + "مشاركة" (ACTION_SEND)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    VideoMediaStoreExporter.openVideoInSystemPlayer(
                                        context = context,
                                        mediaStoreUriString = mediaUriStr
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("play_exported_video_${job.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldSync,
                                    contentColor = ObsidianBg
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "تشغيل ومعاينة",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تشغيل ومعاينة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    VideoMediaStoreExporter.shareVideoToApps(
                                        context = context,
                                        mediaStoreUriString = mediaUriStr,
                                        fileName = fileNameOnly
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_exported_video_${job.id}"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, VulkanCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "مشاركة",
                                    tint = VulkanCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مشاركة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VulkanCyan
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
private fun LiveMetricBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = tint
        )
    }
}
