package org.langue.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.data.AppRepository
import org.langue.app.data.QuizStats
import org.langue.engine.VocabularySearch
import org.langue.engine.QuizEngine
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryIntegrationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var databaseName: String
    private lateinit var scope: CoroutineScope
    private lateinit var repository: AppRepository
    private val reopenedRepositories = mutableListOf<AppRepository>()
    private lateinit var preferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        root = File(context.filesDir, "test-$id").also { it.mkdirs() }
        databaseName = "test-$id.db"
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        preferences = PreferenceDataStoreFactory.create(scope = scope) { File(root, "prefs.preferences_pb") }
        repository = createRepository()
    }

    private fun createRepository() = AppRepository(context, databaseName, File(root, "packs"), preferences)

    @After fun tearDown() {
        reopenedRepositories.forEach { it.close() }
        repository.close()
        scope.cancel()
        context.deleteDatabase(databaseName)
        root.deleteRecursively()
    }

    @Test fun firstLaunchHasNoInventedContent() = runBlocking {
        repository.initialize()
        assertTrue(repository.packs.first().isEmpty())
        assertNull(repository.activePack.first())
        assertEquals(QuizStats(), repository.progress.first())
        assertTrue(repository.viewedEntryIds.first().isEmpty())
    }

    @Test fun importedContentDrivesSearchQuizAndLocalAudio() = runBlocking {
        val expected = TestPacks.pack()
        val installed = repository.importPack(TestPacks.zip(expected))
        assertEquals(expected, installed)
        assertEquals(expected.categories, repository.activePack.first()!!.categories)
        assertEquals("item.2", VocabularySearch.search(installed, "beta").single().id)
        assertEquals("item.1", VocabularySearch.search(installed, "بديل").single().id)
        assertEquals(3, QuizEngine.next(installed.entries)!!.options.size)
        val audio = repository.audioFile(installed.manifest.id, installed.entries.first())
        assertTrue(audio?.isFile == true)
        assertNull(repository.audioFile(installed.manifest.id, installed.entries[1]))
        assertNull(repository.audioFile(installed.manifest.id,
            installed.entries.first().copy(audio = "../../../outside.wav")))
    }

    @Test fun largePackIsLoadedWithoutAWholePackCursorWindowRow() = runBlocking {
        // Above Android's common 2 MiB CursorWindow budget, but within schema limits.
        val entries = (1..600).map { index ->
            org.langue.engine.Entry("large.$index", "test.category",
                "s".repeat(1900) + index, "t".repeat(1900) + index)
        }
        val large = TestPacks.pack(entries = entries)
        repository.importPack(TestPacks.zip(large))
        val reopened = createRepository().also { reopenedRepositories += it }
        reopened.initialize()
        val loaded = reopened.activePack.first()!!
        assertEquals(600, loaded.entries.size)
        assertEquals(entries.last(), loaded.entries.last())
    }

    @Test fun viewedStateAndBestStreakSurviveRepositoryRecreation() = runBlocking {
        repository.importPack(TestPacks.zip(TestPacks.pack()))
        repository.markViewed("item.1")
        repository.recordAnswer(true)
        repository.recordAnswer(true)
        repository.recordAnswer(false)
        val reopened = createRepository().also { reopenedRepositories += it }
        reopened.initialize()
        val stats = withTimeout(10_000) { reopened.progress.first { it.answered == 3 } }
        assertEquals(QuizStats(currentStreak = 0, bestStreak = 2, answered = 3, correct = 2), stats)
        assertEquals(setOf("item.1"), reopened.viewedEntryIds.first())
    }

    @Test fun newerPackPreservesStableProgressAndRemovesDeletedEntryState() = runBlocking {
        val original = TestPacks.pack()
        repository.importPack(TestPacks.zip(original))
        repository.markViewed("item.1")
        repository.markViewed("item.2")
        repository.recordAnswer(true)
        repository.importPack(TestPacks.zip(TestPacks.pack(version = 2,
            entries = original.entries.filter { it.id != "item.2" })))
        assertEquals(2, repository.activePack.first()!!.manifest.version)
        assertEquals(setOf("item.1"), repository.viewedEntryIds.first())
        assertEquals(1, repository.progress.first().bestStreak)
        assertEquals(1, File(root, "packs").listFiles()!!.size)
    }

    @Test fun malformedArchivesDowngradesAndLanguageChangesLeaveInstalledDataUntouched() = runBlocking {
        repository.importPack(TestPacks.zip(TestPacks.pack()))
        val before = repository.exportProgress()
        expectFailure { repository.importPack(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        expectFailure { repository.importPack(TestPacks.zip(TestPacks.pack())) }
        expectFailure { repository.importPack(TestPacks.zip(TestPacks.pack(version = 2, targetTag = "en"))) }
        assertEquals(1, repository.packs.first().single().manifest.version)
        assertEquals(before, repository.exportProgress())
        assertEquals(1, File(root, "packs").listFiles()!!.size)
    }

    @Test fun backupRestoresProgressAndRejectsInvalidChangesAtomically() = runBlocking {
        repository.importPack(TestPacks.zip(TestPacks.pack()))
        repository.markViewed("item.1")
        repository.recordAnswer(true)
        val backup = repository.exportProgress()
        repository.resetProgress()
        assertEquals(QuizStats(), repository.progress.first())
        assertTrue(repository.viewedEntryIds.first().isEmpty())
        repository.restoreProgress(backup)
        assertEquals(1, repository.progress.first().bestStreak)
        assertEquals(setOf("item.1"), repository.viewedEntryIds.first())
        expectFailure { repository.restoreProgress(backup.replace("test.pack", "absent.pack")) }
        expectFailure { repository.restoreProgress(backup.replace("\"answered\": 1", "\"answered\": 0")) }
        assertEquals(backup, repository.exportProgress())
    }

    @Test fun packsKeepIndependentStatisticsAndDeletionRemovesOnlySelectedContent() = runBlocking {
        repository.importPack(TestPacks.zip(TestPacks.pack(id = "test.first")))
        repository.recordAnswer(true)
        repository.importPack(TestPacks.zip(TestPacks.pack(id = "test.second")))
        repository.recordAnswer(false)
        assertEquals(QuizStats(answered = 1), repository.progress.first())
        repository.selectPack("test.first")
        assertEquals(1, repository.progress.first().bestStreak)
        repository.deletePack("test.first")
        assertEquals("test.second", repository.activePack.first()!!.manifest.id)
        assertEquals(QuizStats(answered = 1), repository.progress.first())
        assertFalse(repository.exportProgress().contains("test.first"))
        assertEquals(1, File(root, "packs").listFiles()!!.size)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Operation should be rejected") }
        catch (expected: Exception) { /* Assert persisted state in the calling test. */ }
    }
}
