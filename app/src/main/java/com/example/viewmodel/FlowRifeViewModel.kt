package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.RifeNativeBridge
import com.example.engine.SyntheticVideoGenerator
import com.example.engine.VideoInterpolationPipeline
import com.example.model.EngineSettings
import com.example.model.FrameInspectionPreview
import com.example.model.InterpolationJob
import com.example.model.InterpolationTarget
import com.example.model.JobStatus
import com.example.model.OutputTimingMode
import com.example.model.RifeModel
import com.example.model.VideoSourceMetadata
import com.example.model.VulkanGpuDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private val Application.dataStore by preferencesDataStore(name = "flowrife_engine_prefs")

class FlowRifeViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _settings = MutableStateFlow(EngineSettings())
    val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    private val _gpuDevices = MutableStateFlow<List<VulkanGpuDevice>>(emptyList())
    val gpuDevices: StateFlow<List<VulkanGpuDevice>> = _gpuDevices.asStateFlow()

    private val _currentSource = MutableStateFlow<VideoSourceMetadata?>(null)
    val currentSource: StateFlow<VideoSourceMetadata?> = _currentSource.asStateFlow()

    private val _inspectedFrameIndex = MutableStateFlow(10)
    val inspectedFrameIndex: StateFlow<Int> = _inspectedFrameIndex.asStateFlow()

    private val _framePreview = MutableStateFlow<FrameInspectionPreview?>(null)
    val framePreview: StateFlow<FrameInspectionPreview?> = _framePreview.asStateFlow()

    private val _isGeneratingPreview = MutableStateFlow(false)
    val isGeneratingPreview: StateFlow<Boolean> = _isGeneratingPreview.asStateFlow()

    private val _jobQueue = MutableStateFlow<List<InterpolationJob>>(emptyList())
    val jobQueue: StateFlow<List<InterpolationJob>> = _jobQueue.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var activeQueueWorkerJob: Job? = null

    init {
        viewModelScope.launch {
            loadPersistedSettings()
            val gpus = RifeNativeBridge.enumerateVulkanDevices(getApplication())
            _gpuDevices.value = gpus
            generateDemoBenchmarkVideo(autoInspectSceneCut = false)
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun updateSettings(transform: (EngineSettings) -> EngineSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        viewModelScope.launch {
            persistSettings(updated)
            refreshFramePreview()
        }
    }

    fun onVideoPickedFromGallery(uri: Uri) {
        viewModelScope.launch {
            _isGeneratingPreview.value = true
            val meta = VideoInterpolationPipeline.inspectVideoUri(getApplication(), uri)
            _currentSource.value = meta
            _inspectedFrameIndex.value = (meta.totalFrames / 3).coerceAtLeast(0)
            refreshFramePreview()
            _statusBanner.value = "تم استيراد الفيديو: ${meta.displayName} (${meta.width}×${meta.height} @ ${meta.sourceFps.toInt()}fps)"
        }
    }

    fun generateDemoBenchmarkVideo(autoInspectSceneCut: Boolean = true) {
        viewModelScope.launch {
            _isGeneratingPreview.value = true
            val meta = SyntheticVideoGenerator.generateTestVideoMp4(getApplication())
            _currentSource.value = meta
            // Frame 23 -> 24 is the exact Scene Cut boundary in our 48-frame benchmark video
            _inspectedFrameIndex.value = if (autoInspectSceneCut) 23 else 10
            refreshFramePreview()
            if (autoInspectSceneCut) {
                _statusBanner.value = "تم توليد فيديو الاختبار الحقيقي (MP4 H.264) وضبط المعاينة عند قطع المشهد (الإطار 23←24)"
            }
        }
    }

    fun setInspectedFrameIndex(newIndex: Int) {
        val src = _currentSource.value ?: return
        val clamped = newIndex.coerceIn(0, (src.totalFrames - 2).coerceAtLeast(0))
        _inspectedFrameIndex.value = clamped
        viewModelScope.launch {
            refreshFramePreview()
        }
    }

    fun jumpToSceneCutBoundary() {
        val src = _currentSource.value ?: return
        val cutIdx = ((src.totalFrames / 2) - 1).coerceAtLeast(0)
        setInspectedFrameIndex(cutIdx)
    }

    fun jumpToSmoothMotionFrame() {
        setInspectedFrameIndex(10)
    }

    private suspend fun refreshFramePreview() {
        val src = _currentSource.value ?: return
        _isGeneratingPreview.value = true
        try {
            val preview = VideoInterpolationPipeline.buildFrameInspectionPreview(
                context = getApplication(),
                source = src,
                settings = _settings.value,
                frameIndex = _inspectedFrameIndex.value
            )
            _framePreview.value = preview
        } finally {
            _isGeneratingPreview.value = false
        }
    }

    fun enqueueAndStartJob() {
        val src = _currentSource.value ?: return
        val cfg = _settings.value
        val mult = VideoInterpolationPipeline.computeEffectiveMultiplier(src.sourceFps, cfg.target)
        val effFps = when (cfg.target) {
            InterpolationTarget.TARGET_60FPS -> 60f
            InterpolationTarget.TARGET_120FPS -> 120f
            else -> src.sourceFps * mult
        }

        val newJob = InterpolationJob(
            id = UUID.randomUUID().toString().take(8).uppercase(),
            source = src,
            settings = cfg,
            status = JobStatus.QUEUED,
            totalInputFrames = src.totalFrames,
            estimatedTotalOutputFrames = (src.totalFrames - 1) * mult + 1,
            effectiveTargetFps = effFps
        )

        _jobQueue.update { listOf(newJob) + it }
        _selectedTab.value = 1 // Navigate to Queue & Live Telemetry tab
        processNextQueuedJobIfIdle()
    }

    fun cancelOrRemoveJob(jobId: String) {
        val target = _jobQueue.value.firstOrNull { it.id == jobId } ?: return
        if (target.status == JobStatus.RUNNING || target.status == JobStatus.PAUSED_THERMAL) {
            activeQueueWorkerJob?.cancel()
            _jobQueue.update { list ->
                list.map { if (it.id == jobId) it.copy(status = JobStatus.CANCELLED) else it }
            }
            processNextQueuedJobIfIdle()
        } else {
            _jobQueue.update { list -> list.filterNot { it.id == jobId } }
        }
    }

    fun clearCompletedJobs() {
        _jobQueue.update { list ->
            list.filter { it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED }
        }
    }

    private fun processNextQueuedJobIfIdle() {
        if (activeQueueWorkerJob?.isActive == true) return
        val nextQueued = _jobQueue.value.firstOrNull { it.status == JobStatus.QUEUED } ?: return

        activeQueueWorkerJob = viewModelScope.launch {
            try {
                val finalJob = VideoInterpolationPipeline.executeStreamingJob(
                    context = getApplication(),
                    initialJob = nextQueued.copy(status = JobStatus.RUNNING),
                    onTelemetryUpdate = { updated ->
                        _jobQueue.update { list ->
                            list.map { if (it.id == updated.id) updated else it }
                        }
                    }
                )
                _jobQueue.update { list ->
                    list.map { if (it.id == finalJob.id) finalJob else it }
                }
            } catch (_: Throwable) {
                _jobQueue.update { list ->
                    list.map {
                        if (it.id == nextQueued.id && it.status == JobStatus.RUNNING) {
                            it.copy(status = JobStatus.CANCELLED)
                        } else it
                    }
                }
            } finally {
                activeQueueWorkerJob = null
                processNextQueuedJobIfIdle()
            }
        }
    }

    private suspend fun loadPersistedSettings() {
        try {
            val prefs = getApplication<Application>().dataStore.data.first()
            val modelId = prefs[KEY_MODEL] ?: RifeModel.RIFE_V4_6.id
            val targetId = prefs[KEY_TARGET] ?: InterpolationTarget.MULT_2X.id
            val timingId = prefs[KEY_TIMING] ?: OutputTimingMode.REALTIME_SMOOTH.id

            _settings.value = EngineSettings(
                model = RifeModel.entries.firstOrNull { it.id == modelId } ?: RifeModel.RIFE_V4_6,
                target = InterpolationTarget.entries.firstOrNull { it.id == targetId } ?: InterpolationTarget.MULT_2X,
                timingMode = OutputTimingMode.entries.firstOrNull { it.id == timingId } ?: OutputTimingMode.REALTIME_SMOOTH,
                uhdMode = prefs[KEY_UHD] ?: false,
                ttaMode = prefs[KEY_TTA] ?: false,
                ttaTemporalMode = prefs[KEY_TTA_TEMPORAL] ?: false,
                numThreads = prefs[KEY_THREADS] ?: 4,
                selectedGpuId = prefs[KEY_GPU_ID] ?: 0,
                sceneCutDetectionEnabled = prefs[KEY_SCENE_CUT_ENABLED] ?: true,
                sceneCutThreshold = prefs[KEY_SCENE_CUT_THRESHOLD] ?: 0.30f,
                audioPassthruEnabled = prefs[KEY_AUDIO_PASSTHRU] ?: true,
                strictPtsRecalculation = prefs[KEY_STRICT_PTS] ?: true,
                thermalThrottleGuard = prefs[KEY_THERMAL_GUARD] ?: true
            )
        } catch (_: Throwable) {
        }
    }

    private suspend fun persistSettings(s: EngineSettings) {
        try {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_MODEL] = s.model.id
                prefs[KEY_TARGET] = s.target.id
                prefs[KEY_TIMING] = s.timingMode.id
                prefs[KEY_UHD] = s.uhdMode
                prefs[KEY_TTA] = s.ttaMode
                prefs[KEY_TTA_TEMPORAL] = s.ttaTemporalMode
                prefs[KEY_THREADS] = s.numThreads
                prefs[KEY_GPU_ID] = s.selectedGpuId
                prefs[KEY_SCENE_CUT_ENABLED] = s.sceneCutDetectionEnabled
                prefs[KEY_SCENE_CUT_THRESHOLD] = s.sceneCutThreshold
                prefs[KEY_AUDIO_PASSTHRU] = s.audioPassthruEnabled
                prefs[KEY_STRICT_PTS] = s.strictPtsRecalculation
                prefs[KEY_THERMAL_GUARD] = s.thermalThrottleGuard
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private val KEY_MODEL = stringPreferencesKey("rife_model")
        private val KEY_TARGET = stringPreferencesKey("interp_target")
        private val KEY_TIMING = stringPreferencesKey("timing_mode")
        private val KEY_UHD = booleanPreferencesKey("uhd_mode")
        private val KEY_TTA = booleanPreferencesKey("tta_mode")
        private val KEY_TTA_TEMPORAL = booleanPreferencesKey("tta_temporal")
        private val KEY_THREADS = intPreferencesKey("num_threads")
        private val KEY_GPU_ID = intPreferencesKey("gpu_id")
        private val KEY_SCENE_CUT_ENABLED = booleanPreferencesKey("scene_cut_enabled")
        private val KEY_SCENE_CUT_THRESHOLD = floatPreferencesKey("scene_cut_threshold")
        private val KEY_AUDIO_PASSTHRU = booleanPreferencesKey("audio_passthru")
        private val KEY_STRICT_PTS = booleanPreferencesKey("strict_pts")
        private val KEY_THERMAL_GUARD = booleanPreferencesKey("thermal_guard")
    }
}
