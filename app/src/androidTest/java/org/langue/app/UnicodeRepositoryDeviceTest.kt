package org.langue.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.app.data.AppRepository
import org.langue.app.data.QuizStats
import org.langue.engine.Entry
import org.langue.engine.VocabularySearch

@RunWith(AndroidJUnit4::class)
class UnicodeRepositoryDeviceTest {
    @Test fun unicodeSearchAndProgressBackupSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "unicode-$id").also { it.mkdirs() }
        val name = "unicode-$id.db"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = PreferenceDataStoreFactory.create(scope = scope) { File(root, "prefs.preferences_pb") }
        fun open() = AppRepository(context, name, File(root, "packs"), preferences)
        var repository = open()
        val entry = Entry("unicode.1", "test.category", "E\u0301chantillon 𐐀", "العَرَبِيَّة ١",
            alternatives = listOf("עִבְרִית"), transliteration = "unicode alif")
        val fixture = TestPacks.pack(entries = listOf(entry))
        try {
            repository.importPack(TestPacks.zip(fixture))
            repository.markViewed(entry.id)
            repository.recordAnswer(true)
            val exported = repository.exportProgress()
            repository.close()
            repository = open()
            repository.initialize()
            val loaded = repository.activePack.first()!!
            assertEquals(fixture, loaded)
            assertEquals("rtl", loaded.manifest.targetLanguage.direction)
            for (query in listOf("echantillon", "العربية", "עברית", "unicode alif", "𐐀")) {
                assertEquals("Unicode query did not match: $query", listOf(entry.id), VocabularySearch.search(loaded, query).map { it.id })
            }
            assertEquals(entry.source, loaded.entries.single().source)
            assertEquals(QuizStats(1, 1, 1, 1), repository.progress.first())
            repository.resetProgress()
            repository.restoreProgress(exported)
            assertEquals(QuizStats(1, 1, 1, 1), repository.progress.first())
            assertEquals(setOf(entry.id), repository.viewedEntryIds.first())
        } finally {
            repository.close()
            scope.cancel()
            context.deleteDatabase(name)
            root.deleteRecursively()
        }
    }
}
