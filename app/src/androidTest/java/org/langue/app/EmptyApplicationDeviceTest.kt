package org.langue.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Launches the real empty Activity on an Android device or emulator, without a server. */
@RunWith(AndroidJUnit4::class)
class EmptyApplicationDeviceTest : DeviceUiHarness() {

    @Test fun blankApplicationLaunchesAndAllThreeTabsWork() {
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.empty_content)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.categories)).assertDoesNotExist()
        compose.onNodeWithText(activity.getString(R.string.quiz)).performClick()
        compose.onNodeWithText(activity.getString(R.string.practice_heading)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.empty_content)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.no_languages)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.vocabulary)).performClick()
        compose.onNodeWithText(activity.getString(R.string.empty_content)).assertIsDisplayed()
    }
}
