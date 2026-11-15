package org.langue.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.langue.engine.Category
import org.langue.engine.Entry
import org.langue.engine.Language
import org.langue.engine.LanguagePack
import org.langue.engine.Manifest
import org.langue.engine.PackException
import org.langue.engine.PackJson

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun importedContentProgressUpdateBackupAndDeletionSurviveReopening() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val database = "test-$suffix.db"
        val directory = File(context.cacheDir, "packs-$suffix")
        var repository = AppRepository(context, database, directory)
        try {
            repository.initialize()
            assertTrue(repository.packs.first().isEmpty())
            assertEquals(null, repository.activePack.first())
            repository.importPack(ByteArrayInputStream(archive(pack(1))))
            assertEquals(1, repository.packs.first().size)
            assertEquals("test-pack", repository.activePack.first()?.manifest?.id)
            repository.markViewed("entry1")
            repository.markViewed("entry2")
            repository.recordAnswer(true)
            repository.recordAnswer(true)
            repository.recordAnswer(false)
            assertEquals(QuizStats(0, 2, 3, 2), repository.progress.first())
            val backup = repository.exportProgress()
            repository.close()
            repository = AppRepository(context, database, directory)
            repository.initialize()
            assertEquals(QuizStats(0, 2, 3, 2), repository.progress.first())
            assertTrue("entry1" in repository.viewedEntryIds.first())

            // An update removes one ID and adds another; old IDs keep progression.
            repository.importPack(ByteArrayInputStream(archive(pack(2, listOf("entry1", "entry3")))))
            assertEquals(setOf("entry1"), repository.viewedEntryIds.first())
            assertEquals(QuizStats(0, 2, 3, 2), repository.progress.first())
            assertEquals(1, directory.listFiles()?.size)
            repository.resetProgress()
            assertEquals(QuizStats(), repository.progress.first())
            repository.restoreProgress(backup)
            assertEquals(QuizStats(0, 2, 3, 2), repository.progress.first())
            assertEquals(setOf("entry1"), repository.viewedEntryIds.first())
            repository.deletePack("test-pack")
            assertTrue(repository.packs.first().isEmpty())
            assertEquals(QuizStats(), repository.progress.first())
        } finally {
            repository.close()
            context.deleteDatabase(database)
            directory.deleteRecursively()
        }
    }

    @Test fun failedUpdateAndInvalidRestoreLeaveInstalledStateIntact() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val database = "test-$suffix.db"
        val directory = File(context.cacheDir, "packs-$suffix")
        val repository = AppRepository(context, database, directory)
        try {
            repository.initialize()
            repository.importPack(ByteArrayInputStream(archive(pack(2))))
            repository.recordAnswer(true)
            val before = repository.exportProgress()
            var rejected = false
            try { repository.importPack(ByteArrayInputStream(archive(pack(1)))) }
            catch (_: PackException) { rejected = true }
            assertTrue(rejected)
            assertEquals(2, repository.activePack.first()?.manifest?.version)
            assertEquals(1, directory.listFiles()?.size)
            rejected = false
            try { repository.restoreProgress("""{"schemaVersion":1,"packs":[{"packId":"test-pack","currentStreak":3,"bestStreak":1,"answered":1,"correct":1}]}""") }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(before, repository.exportProgress())
        } finally {
            repository.close()
            context.deleteDatabase(database)
            directory.deleteRecursively()
        }
    }

    @Test fun audioResolverAcceptsOnlyInstalledLocalFiles() = runBlocking {
        val suffix = UUID.randomUUID().toString()
        val database = "test-$suffix.db"
        val directory = File(context.cacheDir, "packs-$suffix")
        val repository = AppRepository(context, database, directory)
        try {
            repository.initialize()
            val fixture = pack(1).let { it.copy(entries = it.entries.mapIndexed { index, entry ->
                if (index == 0) entry.copy(audio = "audio/entry.mp3") else entry
            }) }
            val installed = repository.importPack(ByteArrayInputStream(archive(fixture, withAudio = true)))
            val entry = installed.entries.first()
            assertNotNull(repository.audioFile(installed.manifest.id, entry))
            assertEquals(null, repository.audioFile(installed.manifest.id, entry.copy(audio = "../../escape.mp3")))
            assertEquals(null, repository.audioFile(installed.manifest.id, entry.copy(audio = "audio/absent.mp3")))
            repository.deletePack(installed.manifest.id)
            assertEquals(null, repository.audioFile(installed.manifest.id, entry))
            assertFalse(directory.listFiles().orEmpty().isNotEmpty())
        } finally {
            repository.close()
            context.deleteDatabase(database)
            directory.deleteRecursively()
        }
    }

    private fun pack(version: Int, ids: List<String> = listOf("entry1", "entry2")) = LanguagePack(
        Manifest(id = "test-pack", name = "Instrumentation fixture", version = version,
            sourceLanguage = Language("fr", "Français"), targetLanguage = Language("en", "Anglais")),
        listOf(Category("category", "Test")),
        ids.map { Entry(it, "category", "Source $it", "Target $it") },
    )

    private fun archive(pack: LanguagePack, withAudio: Boolean = false): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val files = mapOf(
                "manifest.json" to PackJson.json.encodeToString(pack.manifest).toByteArray(),
                "categories.json" to PackJson.json.encodeToString(pack.categories).toByteArray(),
                "entries.json" to PackJson.json.encodeToString(pack.entries).toByteArray(),
            ) + if (withAudio) mapOf("audio/entry.mp3" to byteArrayOf(0x49, 0x44, 0x33)) else emptyMap()
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(content); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
