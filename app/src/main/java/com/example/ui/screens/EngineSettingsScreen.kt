package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.EngineSettings
import com.example.model.VulkanGpuDevice
import com.example.ui.theme.AmberSceneCut
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.OpticalViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VulkanCyan

@Composable
fun EngineSettingsScreen(
    settings: EngineSettings,
    gpuDevices: List<VulkanGpuDevice>,
    onUpdateSettings: ((EngineSettings) -> EngineSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "إعدادات المحرك المتقدمة (RIFE NCNN Vulkan & Flowframes)",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        // 1. Vulkan GPU Device & Threads Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = null,
                        tint = VulkanCyan
                    )
                    Text(
                        text = "معالج الرسوميات وخيوط المعالجة (Vulkan GPU & Threads)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                gpuDevices.forEach { gpu ->
                    val selected = settings.selectedGpuId == gpu.id
                    Surface(
                        color = if (selected) ObsidianCardElevated else ObsidianCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (selected) VulkanCyan else BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onUpdateSettings { it.copy(selectedGpuId = gpu.id) } }
                            .testTag("gpu_device_${gpu.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onUpdateSettings { it.copy(selectedGpuId = gpu.id) } },
                                colors = RadioButtonDefaults.colors(selectedColor = VulkanCyan)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gpu.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${gpu.apiVersion} • Queues: ${gpu.computeQueueCount} • FP16: ${if (gpu.supportsFp16) "Enabled" else "No"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VulkanCyan
                                )
                            }
                        }
                    }
                }

                // Threads Slider (1..8)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "عدد خيوط المعالجة (NCNN Compute Threads -j)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${settings.numThreads} Threads",
                            style = MaterialTheme.typography.labelLarge,
                            color = VulkanCyan
                        )
                    }
                    Slider(
                        value = settings.numThreads.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(numThreads = v.toInt().coerceIn(1, 8)) } },
                        valueRange = 1f..8f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = VulkanCyan,
                            activeTrackColor = VulkanCyan
                        ),
                        modifier = Modifier.testTag("threads_slider")
                    )
                }
            }
        }

        // 2. UHD Mode & TTA Mode Card (rife-ncnn-vulkan native flags)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = OpticalViolet
                    )
                    Text(
                        text = "وسائط الدقة الفائقة (UHD Mode & TTA Mode)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                SettingSwitchRow(
                    title = "وضع الدقة الفائقة (UHD Mode • -u)",
                    subtitle = "تحجيم مصفوفة التدفق البصري (Optical Flow Scale 0.5x) للفيديوهات عالية الدقة (1440p/4K) لتسريع الـ GPU ومنع تشبع الذاكرة.",
                    checked = settings.uhdMode,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(uhdMode = v) } },
                    testTag = "switch_uhd_mode"
                )

                SettingSwitchRow(
                    title = "وضع الدقة العالية (TTA Spatial Mode • -x)",
                    subtitle = "تفعيل Test-Time Augmentation عبر عكس الإطارات أفقياً ودمج حقول الحركة لتقليل الهالات البصرية (Ghosting).",
                    checked = settings.ttaMode,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(ttaMode = v) } },
                    testTag = "switch_tta_mode"
                )

                SettingSwitchRow(
                    title = "وضع TTA الزمني (TTA Temporal Mode • -z)",
                    subtitle = "تقدير التدفق البصري الثنائي العكسي (Forward + Backward Ensemble) لمضاعفة دقة تتبع الحواف السريعة.",
                    checked = settings.ttaTemporalMode,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(ttaTemporalMode = v) } },
                    testTag = "switch_tta_temporal"
                )
            }
        }

        // 3. Scene Cut Detection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, AmberSceneCut.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = AmberSceneCut
                    )
                    Text(
                        text = "كشف قطع المشاهد (Scene Cut Detection)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                SettingSwitchRow(
                    title = "منع تشويه الانتقالات المفاجئة (Morphing Guard)",
                    subtitle = "عند رصد تغير لحظي يتجاوز الحد المسموح، يمتنع المحرك عن توليد إطار ذكاء اصطناعي مشوه ويكتفي بتكرار الإطار الأصلي.",
                    checked = settings.sceneCutDetectionEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(sceneCutDetectionEnabled = v) } },
                    testTag = "switch_scene_cut"
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "حساسية كشف قطع المشهد (Scene Delta Threshold)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(settings.sceneCutThreshold * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberSceneCut
                        )
                    }
                    Slider(
                        value = settings.sceneCutThreshold,
                        onValueChange = { v -> onUpdateSettings { it.copy(sceneCutThreshold = v) } },
                        valueRange = 0.12f..0.65f,
                        colors = SliderDefaults.colors(
                            thumbColor = AmberSceneCut,
                            activeTrackColor = AmberSceneCut
                        ),
                        modifier = Modifier.testTag("scene_cut_threshold_slider")
                    )
                }
            }
        }

        // 4. Strict Audio Sync & OOM / Thermal Guard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            border = BorderStroke(1.dp, EmeraldSync.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = EmeraldSync
                    )
                    Text(
                        text = "المزامنة الصوتية الصارمة وإدارة الذاكرة والحرارة",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                SettingSwitchRow(
                    title = "نسخ الصوت المباشر بدون ضغط (Lossless Audio Passthru)",
                    subtitle = "تمرير حزم الصوت الأصلية (AAC/Opus) مباشرة عبر MediaMuxer بدون إعادة ترميز للحفاظ على النقاء التام.",
                    checked = settings.audioPassthruEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(audioPassthruEnabled = v) } },
                    testTag = "switch_audio_passthru"
                )

                SettingSwitchRow(
                    title = "إعادة حساب الطوابع الزمنية الصارمة (Strict Microsecond PTS)",
                    subtitle = "حساب Presentation TimeStamp بالميكروثانية لكل إطار مولّد لمنع أي انزياح صوتي (Audio/Video Desync).",
                    checked = settings.strictPtsRecalculation,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(strictPtsRecalculation = v) } },
                    testTag = "switch_strict_pts"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = VulkanCyan
                    )
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = VulkanCyan
                    )
                    Text(
                        text = "نظام التدفق المستمر (2-Frame Streaming Ring Buffer)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = VulkanCyan
                    )
                }

                SettingSwitchRow(
                    title = "حماية الأداء الحراري والرام (OOM & Thermal Pacing Guard)",
                    subtitle = "إبقاء إطارين فقط في الرام في كل لحظة لمنع انهيار الذاكرة (OOM Crash) وتنظيم طاقة الـ GPU عند ارتفاع الحرارة.",
                    checked = settings.thermalThrottleGuard,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(thermalThrottleGuard = v) } },
                    testTag = "switch_thermal_guard"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        color = ObsidianCardElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VulkanCyan,
                    checkedTrackColor = VulkanCyan.copy(alpha = 0.35f)
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
