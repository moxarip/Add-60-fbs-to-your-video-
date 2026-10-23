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
    fun extractorSubFramePtsCalculation_preservesFull30SecondDurationAt120Fps() {
        // 30-second video @ 40fps -> 1200 frames, target 120fps -> multiplier = 3x
        val sourceFps = 40f
        val mult = VideoInterpolationPipeline.computeEffectiveMultiplier(
            sourceFps,
            InterpolationTarget.TARGET_120FPS
        )
        assertEquals(3, mult)

        // Last pair of frames in a 30.0-second video: 29,975,000 us -> 30,000,000 us
        val ptsStartUs = 29_975_000L
        val ptsEndUs = 30_000_000L

        val sub0 = VideoInterpolationPipeline.computeExtractorSubFramePtsUs(
            ptsStartUs = ptsStartUs,
            ptsEndUs = ptsEndUs,
            subFrameIndex = 0,
            multiplier = mult,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )
        val sub1 = VideoInterpolationPipeline.computeExtractorSubFramePtsUs(
            ptsStartUs = ptsStartUs,
            ptsEndUs = ptsEndUs,
            subFrameIndex = 1,
            multiplier = mult,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )
        val sub2 = VideoInterpolationPipeline.computeExtractorSubFramePtsUs(
            ptsStartUs = ptsStartUs,
            ptsEndUs = ptsEndUs,
            subFrameIndex = 2,
            multiplier = mult,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )
        val finalFramePts = VideoInterpolationPipeline.computeExtractorSubFramePtsUs(
            ptsStartUs = ptsEndUs,
            ptsEndUs = ptsEndUs,
            subFrameIndex = 0,
            multiplier = mult,
            timingMode = OutputTimingMode.REALTIME_SMOOTH
        )

        assertEquals(29_975_000L, sub0)
        assertEquals(29_983_333L, sub1)
        assertEquals(29_991_666L, sub2)
        // Final frame timestamp stays at 30.0 seconds (30,000,000 us), never shrinking to 10s!
        assertEquals(30_000_000L, finalFramePts)
    }
}
