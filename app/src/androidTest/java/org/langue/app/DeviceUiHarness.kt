package org.langue.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

/** Each real-device UI scenario owns the default application repository and clears only its packs. */
abstract class DeviceUiHarness {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun clearApplicationPacks() {
        val repository = (compose.activity.application as LangueApplication).repository
        runBlocking {
            repository.initialize()
            repository.packs.first().forEach { repository.deletePack(it.manifest.id) }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.empty_content))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun removeTestContent() {
        val repository = (compose.activity.application as LangueApplication).repository
        runBlocking { repository.packs.first().forEach { repository.deletePack(it.manifest.id) } }
    }
}
