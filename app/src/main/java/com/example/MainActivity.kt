package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.JobStatus
import com.example.ui.screens.EngineSettingsScreen
import com.example.ui.screens.NdkCodeScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VulkanCyan
import com.example.viewmodel.FlowRifeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FlowRifeApp()
            }
        }
    }
}

@Composable
fun FlowRifeApp(
    viewModel: FlowRifeViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val gpuDevices by viewModel.gpuDevices.collectAsStateWithLifecycle()
    val currentSource by viewModel.currentSource.collectAsStateWithLifecycle()
    val framePreview by viewModel.framePreview.collectAsStateWithLifecycle()
    val inspectedFrameIndex by viewModel.inspectedFrameIndex.collectAsStateWithLifecycle()
    val isGeneratingPreview by viewModel.isGeneratingPreview.collectAsStateWithLifecycle()
    val jobQueue by viewModel.jobQueue.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    // Zero-permission Android Photo Picker for Videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoPickedFromGallery(uri)
        }
    }

    // BackHandler on secondary tabs returns to Studio tab
    if (selectedTab != 0) {
        BackHandler {
            viewModel.selectTab(0)
        }
    }

    val runningOrQueuedCount = jobQueue.count {
        it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = stringResource(R.string.tab_studio)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_studio)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBg,
                        selectedTextColor = VulkanCyan,
                        indicatorColor = VulkanCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_studio")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (runningOrQueuedCount > 0) {
                                    Badge(containerColor = VulkanCyan, contentColor = ObsidianBg) {
                                        Text("$runningOrQueuedCount")
                                    }
                                } else if (jobQueue.isNotEmpty()) {
                                    Badge(containerColor = EmeraldSync, contentColor = ObsidianBg) {
                                        Text("${jobQueue.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueuePlayNext,
                                contentDescription = stringResource(R.string.tab_queue)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.tab_queue)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBg,
                        selectedTextColor = VulkanCyan,
                        indicatorColor = VulkanCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_queue")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.tab_engine)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_engine)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBg,
                        selectedTextColor = VulkanCyan,
                        indicatorColor = VulkanCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_engine")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = stringResource(R.string.tab_native)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_native)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianBg,
                        selectedTextColor = VulkanCyan,
                        indicatorColor = VulkanCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_native")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBanner != null) {
                statusBanner?.let { msg ->
                    Surface(
                        color = ObsidianCardElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VulkanCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.dismissBanner() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> StudioScreen(
                        source = currentSource,
                        settings = settings,
                        gpuDevices = gpuDevices,
                        framePreview = framePreview,
                        inspectedFrameIndex = inspectedFrameIndex,
                        isGeneratingPreview = isGeneratingPreview,
                        onPickVideoClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onGenerateDemoVideoClick = {
                            viewModel.generateDemoBenchmarkVideo(autoInspectSceneCut = true)
                        },
                        onInspectFrameChange = { viewModel.setInspectedFrameIndex(it) },
                        onJumpToSceneCut = { viewModel.jumpToSceneCutBoundary() },
                        onJumpToSmoothMotion = { viewModel.jumpToSmoothMotionFrame() },
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onStartJobClick = { viewModel.enqueueAndStartJob() }
                    )
                    1 -> QueueScreen(
                        jobs = jobQueue,
                        onStartNewDemoJob = { viewModel.enqueueAndStartJob() },
                        onCancelOrRemoveJob = { viewModel.cancelOrRemoveJob(it) },
                        onClearCompleted = { viewModel.clearCompletedJobs() }
                    )
                    2 -> EngineSettingsScreen(
                        settings = settings,
                        gpuDevices = gpuDevices,
                        onUpdateSettings = { viewModel.updateSettings(it) }
                    )
                    3 -> NdkCodeScreen(
                        onCodeCopied = {
                            viewModel.dismissBanner()
                        }
                    )
                }
            }
        }
    }
}
