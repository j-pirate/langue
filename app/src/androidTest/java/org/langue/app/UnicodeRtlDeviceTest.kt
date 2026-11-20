package org.langue.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.engine.Entry

@RunWith(AndroidJUnit4::class)
class UnicodeRtlDeviceTest : DeviceUiHarness() {
    @Test fun importedUnicodeTextAndVariantsRetainTheirCharactersAndUseRtlLayout() {
        val repository = (compose.activity.application as LangueApplication).repository
        val entry = Entry("unicode.1", "test.category", "Échantillon 𐐀", "العَرَبِيَّة ١",
            alternatives = listOf("עִבְרִית"), transliteration = "unicode alif")
        val pack = TestPacks.pack(entries = listOf(entry))
        runBlocking { repository.importPack(TestPacks.zip(pack)) }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(pack.categories.single().name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(pack.categories.single().name).performClick()
        compose.onNodeWithText(entry.source).assertIsDisplayed().performClick()
        val target = compose.onAllNodesWithText(entry.target).onLast()
        target.performScrollTo().assertIsDisplayed()
        val layout = mutableListOf<TextLayoutResult>()
        target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layout)) }
        assertEquals(TextDirection.Rtl, layout.single().layoutInput.style.textDirection)
        compose.onNodeWithText(entry.alternatives.single()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(entry.transliteration!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.close)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.search)).performClick()
        // Arabic diacritics are ignored for matching, but remain in the displayed original.
        compose.onNodeWithText(compose.activity.getString(R.string.search_hint)).performTextInput("العربية")
        compose.onNodeWithText(entry.source).assertIsDisplayed()
        compose.onNodeWithText(entry.target).assertIsDisplayed()
    }
}
