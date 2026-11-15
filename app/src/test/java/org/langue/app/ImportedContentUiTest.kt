package org.langue.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ImportedContentUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realImportedPackFeedsCategoryDetailsSearchQuizAndResetConfirmation() {
        val activity = compose.activity
        val repository = (activity.application as LangueApplication).repository
        val fixture = TestPacks.pack()
        runBlocking { repository.importPack(TestPacks.zip(fixture)) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(fixture.categories.single().name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(fixture.categories.single().name).performClick()
        compose.onNodeWithText(fixture.entries.first().source).performClick()
        compose.onAllNodesWithText(fixture.entries.first().target).onLast().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(fixture.entries.first().alternatives.single()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(fixture.entries.first().transliteration!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.close)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(activity.getString(R.string.back)).performClick()
        compose.onNodeWithContentDescription(activity.getString(R.string.search)).performClick()
        compose.onNodeWithText(activity.getString(R.string.search_hint)).performTextInput("beta")
        compose.onNodeWithText(fixture.entries[1].source).assertIsDisplayed()
        compose.onNodeWithText(fixture.entries.first().source).assertDoesNotExist()
        compose.onNodeWithText(activity.getString(R.string.quiz)).performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            fixture.entries.any { compose.onAllNodesWithText(it.source).fetchSemanticsNodes().isNotEmpty() }
        }
        val question = fixture.entries.single {
            compose.onAllNodesWithText(it.source).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(question.target).performClick()
        compose.onNodeWithText(activity.getString(R.string.next_question)).performScrollTo().assertIsDisplayed()
        runBlocking {
            withTimeout(10_000) { repository.progress.first { it.correct == 1 } }
            assertEquals(setOf("item.1"), repository.viewedEntryIds.first())
        }
        compose.onNodeWithText(activity.getString(R.string.settings)).performClick()
        compose.onNodeWithText(activity.getString(R.string.reset_progress)).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(R.string.reset_title)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.cancel)).performClick()
        runBlocking { assertEquals(1, repository.progress.first().bestStreak) }
        compose.onNodeWithText(activity.getString(R.string.reset_progress)).performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(R.string.reset)).performClick()
        runBlocking { withTimeout(10_000) { repository.progress.first { it.answered == 0 } } }
    }
}
