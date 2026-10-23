package com.example

import com.example.engine.RifeOpticalFlowInterpolator
import com.example.engine.VideoInterpolationPipeline
import com.example.model.EngineSettings
import com.example.model.InterpolationTarget
import com.example.model.OutputTimingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun sceneCutDetection_preventsMorphingOnHardCut_andInterpolatesSmoothFrames() = runBlocking {
        val width = 64
        val height = 64
        val darkSceneFrame0 = IntArray(width * height) { 0xFF101828.toInt() }
        val darkSceneFrame1 = IntArray(width * height) { idx ->
            if (idx % 8 == 0) 0xFF142238.toInt() else 0xFF101828.toInt()
        }
        val brightCutFrame = IntArray(width * height) { 0xFFFFBE55.toInt() }

        val smoothScore = RifeOpticalFlowInterpolator.computeSceneCutScore(
            darkSceneFrame0, darkSceneFrame1, width, height
        )
        val cutScore = RifeOpticalFlowInterpolator.computeSceneCutScore(
            darkSceneFrame0, brightCutFrame, width, height
        )

        assertTrue("Smooth motion should have low scene delta (<0.25)", smoothScore < 0.25f)
        assertTrue("Hard scene cut should have high scene delta (>0.50)", cutScore > 0.50f)

        val settings = EngineSettings(sceneCutDetectionEnabled = true, sceneCutThreshold = 0.30f)

        val smoothOut = RifeOpticalFlowInterpolator.interpolateFrame(
            pixels0 = darkSceneFrame0,
            pixels1 = darkSceneFrame1,
            width = width,
            height = height,
            timestep = 0.5f,
            settings = settings
        )
        assertFalse(smoothOut.isSceneCut)

        val cutOut = RifeOpticalFlowInterpolator.interpolateFrame(
            pixels0 = darkSceneFrame0,
            pixels1 = brightCutFrame,
            width = width,
            height = height,
            timestep = 0.25f,
            settings = settings
        )
        assertTrue("Scene cut must be flagged", cutOut.isSceneCut)
        assertArrayEquals(
            "Scene cut guard must duplicate original frame0 without morphing",
            darkSceneFrame0,
            cutOut.pixels
        )
    }

    @Test
    fun strictPtsCalculation_maintainsExactAudioSyncAndMonotonicity() {
        val baseDelta24FpsUs = 41_666L // 24 fps = 41,666 us per frame

        // 4x Realtime Smooth mode: 4 subframes fit inside 1 original frame duration
        val pts0 = VideoInterpolationPipeline.computeStrictOutputPtsUs(
            inputPairIndex = 10,
            subFrameIndex = 0,
            multiplier = 4,
            outputFrameSequenceIndex = 40,
            baseFrameDeltaUs = baseDelta24FpsUs,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )
        val pts2 = VideoInterpolationPipeline.computeStrictOutputPtsUs(
            inputPairIndex = 10,
            subFrameIndex = 2,
            multiplier = 4,
            outputFrameSequenceIndex = 42,
            baseFrameDeltaUs = baseDelta24FpsUs,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )
        val ptsNextFrame = VideoInterpolationPipeline.computeStrictOutputPtsUs(
            inputPairIndex = 11,
            subFrameIndex = 0,
            multiplier = 4,
            outputFrameSequenceIndex = 44,
            baseFrameDeltaUs = baseDelta24FpsUs,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )

        assertEquals(416_660L, pts0)
        assertTrue(pts2 > pts0 && pts2 < ptsNextFrame)
        assertEquals(458_326L, ptsNextFrame)

        // Target FPS effective multiplier checks
        assertEquals(3, VideoInterpolationPipeline.computeEffectiveMultiplier(24f, InterpolationTarget.TARGET_60FPS))
        assertEquals(5, VideoInterpolationPipeline.computeEffectiveMultiplier(24f, InterpolationTarget.TARGET_120FPS))
    }
}
