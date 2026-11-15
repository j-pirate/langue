package org.langue.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches the real Activity and its real empty repository, without a device or a server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptyApplicationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
