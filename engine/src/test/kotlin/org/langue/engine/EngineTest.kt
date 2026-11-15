package org.langue.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** All linguistic data here is isolated test data and is never packaged in the app. */
class EngineTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun manifest() = Manifest(
        id = "test.pack", name = "Test only", version = 1,
        sourceLanguage = Language("fr", "Français"),
        targetLanguage = Language("en", "English"),
    )

    private fun entry(id: String, target: String = id, alternatives: List<String> = emptyList()) =
        Entry(id = id, categoryId = "category", source = "source $id", target = target, alternatives = alternatives)

    private fun pack(entries: List<Entry> = listOf(entry("first"))) =
        LanguagePack(manifest(), listOf(Category("category", "Test category")), entries)

    private fun files(pack: LanguagePack = pack()): LinkedHashMap<String, ByteArray> = linkedMapOf(
        "manifest.json" to PackJson.json.encodeToString(pack.manifest).toByteArray(),
        "categories.json" to PackJson.json.encodeToString(pack.categories).toByteArray(),
        "entries.json" to PackJson.json.encodeToString(pack.entries).toByteArray(),
    )

    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun destination() = File(temporaryFolder.root, "unpacked")

    private fun reject(action: () -> Unit) {
        try {
            action()
            fail("A malformed pack must be rejected")
        } catch (_: PackException) {
            // Expected public validation exception.
        }
    }

    @Test fun validPackExtractsItsActualData() {
        val original = pack()
        val folder = destination()
        val restored = PackArchive.extract(ByteArrayInputStream(zip(files(original))), folder)
        assertEquals(original, restored)
        assertTrue(File(folder, "manifest.json").isFile)
        assertTrue(File(folder, "entries.json").isFile)
    }

    @Test fun zipTraversalIsRejectedAndPartialFilesAreRemoved() {
        val archive = files().apply { put("../escaped.txt", "bad".toByteArray()) }
        val folder = destination()
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), folder) }
        assertFalse(File(temporaryFolder.root, "escaped.txt").exists())
        assertFalse(folder.exists())
    }

    @Test fun absoluteZipPathIsRejected() {
        val archive = files().apply { put("/absolute.mp3", byteArrayOf(1)) }
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), destination()) }
    }

    @Test fun backslashTraversalIsRejected() {
        val archive = files().apply { put("..\\escaped.mp3", byteArrayOf(1)) }
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), destination()) }
    }

    @Test fun duplicateZipPathsAreRejected() {
        val archive = files().apply { put("nanifest.json", getValue("manifest.json")) }
        // ZIP writer prevents duplicate names, so change the equal-length second filename
        // in both headers. File data and its CRC remain untouched.
        val bytes = zip(archive)
        val before = "nanifest.json".toByteArray()
        for (index in 0..bytes.size - before.size) {
            if (before.indices.all { bytes[index + it] == before[it] }) bytes[index] = 'm'.code.toByte()
        }
        reject { PackArchive.extract(ByteArrayInputStream(bytes), destination()) }
    }

    @Test fun missingCentralDirectoryIsRejectedDespiteCompleteLocalEntries() {
        val bytes = zip(files())
        val signature = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2)
        val offset = (0..bytes.size - signature.size).first { index ->
            signature.indices.all { bytes[index + it] == signature[it] }
        }
        reject { PackArchive.extract(ByteArrayInputStream(bytes.copyOf(offset)), destination()) }
        assertFalse(destination().exists())
    }

    @Test fun directoryEntryCannotHideUnboundedOrUnexpectedData() {
        val archive = files().apply { put("audio/", "directory payload".toByteArray()) }
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), destination()) }
        assertFalse(destination().exists())
    }

    @Test fun deeplyNestedJsonIsRejectedBeforeSerialization() {
        val archive = files().apply {
            put("categories.json", ("[".repeat(33) + "]".repeat(33)).toByteArray())
        }
        try {
            PackArchive.extract(ByteArrayInputStream(zip(archive)), destination())
            fail("JSON deeper than 32 levels must be rejected")
        } catch (error: PackException) {
            assertTrue(error.message.orEmpty().contains("profond"))
        }
        assertFalse(destination().exists())
    }

    @Test fun malformedJsonIsRejectedWithoutLeavingAnInstalledPack() {
        val archive = files().apply { put("entries.json", "{broken".toByteArray()) }
        val folder = destination()
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), folder) }
        assertFalse(folder.exists())
    }

    @Test fun missingManifestIsRejected() {
        val archive = files().apply { remove("manifest.json") }
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), destination()) }
    }

    @Test fun missingReferencedAudioIsRejected() {
        val archive = files(pack(listOf(entry("first").copy(audio = "audio/missing.mp3"))))
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), destination()) }
    }

    @Test fun optionalSingleRootFolderIsRemovedAtImport() {
        val archive = files().mapKeys { (path, _) -> "my-pack/$path" }
        val folder = destination()
        assertEquals(pack(), PackArchive.extract(ByteArrayInputStream(zip(archive)), folder))
        assertTrue(File(folder, "manifest.json").isFile)
        assertFalse(File(folder, "my-pack").exists())
    }

    @Test fun caseInsensitiveDuplicateAudioPathsAreRejected() {
        val archive = files(pack(listOf(entry("first").copy(audio = "audio/Track.mp3")))).apply {
            put("audio/Track.mp3", byteArrayOf())
            put("audio/track.mp3", byteArrayOf())
        }
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), destination()) }
    }

    @Test fun oversizedJsonIsRejectedAndCleanedUp() {
        val archive = files().apply {
            put("entries.json", ByteArray((PackArchive.MAX_JSON_BYTES + 1).toInt()) { ' '.code.toByte() })
        }
        val folder = destination()
        reject { PackArchive.extract(ByteArrayInputStream(zip(archive)), folder) }
        assertFalse(folder.exists())
    }

    @Test fun validEmptyPackIsAcceptedWithoutInventingEntries() {
        val emptyPack = LanguagePack(manifest(), emptyList(), emptyList())
        PackValidator.validate(emptyPack)
        assertEquals(emptyPack, PackArchive.extract(ByteArrayInputStream(zip(files(emptyPack))), destination()))
    }

    @Test fun presentButEmptyAudioIsHandledAsAFileRatherThanDecodeValidated() {
        // The engine validates presence and paths; Android's player reports corrupt or
        // unsupported media without preventing the rest of the pack being usable.
        val original = pack(listOf(entry("first").copy(audio = "audio/track.mp3")))
        val archive = files(original).apply { put("audio/track.mp3", byteArrayOf()) }
        val folder = destination()
        assertEquals(original, PackArchive.extract(ByteArrayInputStream(zip(archive)), folder))
        assertEquals(0L, File(folder, "audio/track.mp3").length())
    }

    @Test fun existingDestinationIsNeverOverwrittenOrDeleted() {
        val folder = destination().apply { mkdirs() }
        val personalFile = File(folder, "keep.txt").apply { writeText("preserve me") }
        reject { PackArchive.extract(ByteArrayInputStream(zip(files())), folder) }
        assertEquals("preserve me", personalFile.readText())
    }

    @Test fun futureSchemaAndInvalidVersionAreRejected() {
        reject { PackValidator.validate(pack().let { it.copy(manifest = it.manifest.copy(schemaVersion = 999)) }) }
        reject { PackValidator.validate(pack().let { it.copy(manifest = it.manifest.copy(version = 0)) }) }
    }

    @Test fun danglingCategoryReferenceAndDuplicateEntryIdsAreRejected() {
        reject { PackValidator.validate(pack(listOf(entry("first").copy(categoryId = "absent")))) }
        reject { PackValidator.validate(pack(listOf(entry("duplicate"), entry("duplicate")))) }
    }

    @Test fun unsafeStableIdsAndEmptyTranslationsAreRejected() {
        reject { PackValidator.validate(pack(listOf(entry("../outside")))) }
        reject { PackValidator.validate(pack(listOf(entry("first", "  ")))) }
    }

    @Test fun searchMatchesAccentsAlternativesAndTransliteration() {
        val accented = entry("first", "École").copy(source = "établissement", alternatives = listOf("Lycée"), transliteration = "madrása")
        val data = pack(listOf(accented, entry("second", "other")))
        for (query in listOf("ECOLE", "etablissement", "lycee", "madrasa")) {
            assertEquals(listOf(accented), VocabularySearch.search(data, query))
        }
    }

    @Test fun searchPreservesNonLatinAndRightToLeftText() {
        val arabic = entry("first", "مرحبا").copy(source = "挨拶")
        val data = pack(listOf(arabic, entry("second")))
        assertEquals(listOf(arabic), VocabularySearch.search(data, "مرح"))
        assertEquals(listOf(arabic), VocabularySearch.search(data, "挨"))
    }

    @Test fun quizNeedsThreeIndependentTranslations() {
        assertNull(QuizEngine.next(emptyList()))
        assertNull(QuizEngine.next(listOf(entry("one"), entry("two"))))
        assertNull(QuizEngine.next(listOf(entry("one", "same"), entry("two", "SÁME"), entry("three", "different"))))
    }

    @Test fun quizProvidesExactlyOneCorrectAnswerAndThreeUniqueChoices() {
        val entries = listOf(entry("one"), entry("two"), entry("three"), entry("four"))
        repeat(30) { seed ->
            val question = requireNotNull(QuizEngine.next(entries, random = Random(seed)))
            assertEquals(3, question.options.size)
            assertEquals(3, question.options.map { it.id }.toSet().size)
            assertEquals(1, question.options.count { it.id == question.entry.id })
            assertTrue(question.options.all { it in entries })
        }
    }

    @Test fun quizNeverOffersEquivalentAlternativesTogether() {
        val entries = listOf(
            entry("one", "alpha", listOf("shared")),
            entry("two", "beta", listOf("SHÁRED")),
            entry("three", "gamma"), entry("four", "delta"),
        )
        repeat(100) { seed ->
            val question = requireNotNull(QuizEngine.next(entries, random = Random(seed)))
            assertFalse(question.options.map { it.id }.containsAll(listOf("one", "two")))
        }
    }

    @Test fun quizEquivalenceIsTransitiveAcrossAllVariants() {
        val entries = listOf(
            entry("one", "alpha", listOf("shared-one")),
            entry("two", "shared-one", listOf("shared-two")),
            entry("three", "shared-two"),
            entry("four", "delta"), entry("five", "epsilon"),
        )
        repeat(100) { seed ->
            val question = requireNotNull(QuizEngine.next(entries, random = Random(seed)))
            assertEquals(1, question.options.count { it.id in setOf("one", "two", "three") })
        }
    }

    @Test fun quizNeverPresentsEquivalentSourcesAsSeparateOptions() {
        val entries = listOf(
            entry("one", "alpha").copy(source = "établissement"),
            entry("two", "beta").copy(source = "ETABLISSEMENT"),
            entry("three", "gamma"), entry("four", "delta"),
        )
        repeat(100) { seed ->
            val question = requireNotNull(QuizEngine.next(entries, random = Random(seed)))
            assertFalse(question.options.map { it.id }.containsAll(listOf("one", "two")))
        }
    }

    @Test fun nextQuestionAvoidsImmediatelyRepeatingPreviousEntry() {
        val entries = listOf(entry("one"), entry("two"), entry("three"), entry("four"))
        repeat(100) { seed ->
            val question = requireNotNull(QuizEngine.next(entries, previousEntryId = "two", random = Random(seed)))
            assertNotEquals("two", question.entry.id)
        }
    }
}
