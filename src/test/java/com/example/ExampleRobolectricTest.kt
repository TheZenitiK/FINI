package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FINI", appName)
  }

  @Test
  fun `main activity shows onboarding then updated home and settings screens`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("onboarding_skip_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasTestTag("onboarding_screen")).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("FINI").assertIsDisplayed()
    composeTestRule.onNodeWithText("VPN").assertDoesNotExist()
    composeTestRule.onNodeWithTag("hero_connect_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("tab_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_client_spoof_item").assertIsDisplayed()

    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_experimental_item"))
    composeTestRule.onNodeWithTag("settings_experimental_item").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_experimental_item").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("switch_experimental_mode").assertIsDisplayed()
    composeTestRule.onNodeWithTag("experimental_xray_core_item").assertIsDisplayed()
    composeTestRule.onNodeWithTag("experimental_logs_item").assertIsDisplayed()
  }
}
