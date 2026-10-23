package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_andNavigatesAcrossStudioQueueSettingsAndNdkTabs() {
        composeTestRule.waitForIdle()

        // Verify Studio tab elements
        composeTestRule.onNodeWithTag("pick_video_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("generate_demo_button").performScrollTo().assertIsDisplayed()

        // Navigate to Engine Settings tab and toggle UHD & TTA modes
        composeTestRule.onNodeWithTag("nav_tab_engine").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_uhd_mode").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("switch_tta_mode").performScrollTo().assertIsDisplayed().performClick()

        // Navigate to NDK C++ Code tab
        composeTestRule.onNodeWithTag("nav_tab_native").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("copy_ndk_code_button").assertIsDisplayed()

        // Navigate to Queue tab
        composeTestRule.onNodeWithTag("nav_tab_queue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_queue_start_button").assertIsDisplayed()
    }
}
