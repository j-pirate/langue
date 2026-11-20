package org.langue.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/** Typed failures remain actionable without exposing imported text or file paths. */
class FailureReasonTest {
    private val manifest = Manifest(id = "test.pack", name = "Test pack", version = 1,
        sourceLanguage = Language("qaa", "Source"), targetLanguage = Language("qab", "Target"))
    private val valid = LanguagePack(manifest, listOf(Category("category", "Category")),
        listOf(Entry("item", "category", "Test source", "Test target")))

    @Test fun unsupportedSchemaAndInvalidVersionHaveSeparateCodes() {
        assertReason(FailureReason.INCOMPATIBLE_PACK) {
            PackValidator.validate(valid.copy(manifest = manifest.copy(schemaVersion = 2)))
        }
        assertReason(FailureReason.INVALID_VERSION) {
            PackValidator.validate(valid.copy(manifest = manifest.copy(version = 0)))
        }
    }

    @Test fun unknownCategoryHasAnActionableCodeWithoutEchoingItsIdentifier() {
        val error = assertReason(FailureReason.UNKNOWN_CATEGORY) {
            PackValidator.validate(valid.copy(entries = listOf(valid.entries.single().copy(categoryId = "private-category"))))
        }
        assertFalse(error.message.orEmpty().contains("private-category"))
    }

    @Test fun malformedZipAndMalformedContentRemainDistinct() {
        inTemporaryDirectory { root ->
            assertReason(FailureReason.INVALID_ARCHIVE) {
                PackArchive.extract(ByteArrayInputStream(byteArrayOf(1, 2, 3)), root.resolve("bad-zip").toFile())
            }
            val malformed = zip(mapOf("manifest.json" to "{", "categories.json" to "[]", "entries.json" to "[]"))
            assertReason(FailureReason.INVALID_CONTENT) {
                PackArchive.extract(malformed.inputStream(), root.resolve("bad-json").toFile())
            }
        }
    }

    @Test fun archiveFileDirectoryConflictsRemainInvalidArchives() {
        val files = linkedMapOf(
            "manifest.json" to PackJson.json.encodeToString(valid.manifest),
            "categories.json" to PackJson.json.encodeToString(valid.categories),
            "entries.json" to PackJson.json.encodeToString(valid.entries),
            // A regular file cannot also become the parent of another archive member.
            "audio" to "not a directory",
            "audio/clip.mp3" to "test bytes",
        )
        inTemporaryDirectory { root ->
            assertReason(FailureReason.INVALID_ARCHIVE) {
                PackArchive.extract(zip(files).inputStream(), root.resolve("conflicting-paths").toFile())
            }
            assertEquals(emptyList<String>(), root.toFile().listFiles().orEmpty().map { it.name })
        }
    }

    @Test fun missingReferencedAudioHasSpecificCodeAndNoPathInItsMessage() {
        val pack = valid.copy(entries = listOf(valid.entries.single().copy(audio = "audio/private-name.mp3")))
        val files = mapOf("manifest.json" to PackJson.json.encodeToString(pack.manifest),
            "categories.json" to PackJson.json.encodeToString(pack.categories),
            "entries.json" to PackJson.json.encodeToString(pack.entries))
        inTemporaryDirectory { root ->
            val error = assertReason(FailureReason.MISSING_AUDIO) {
                PackArchive.extract(zip(files).inputStream(), root.resolve("missing-audio").toFile())
            }
            assertFalse(error.message.orEmpty().contains("private-name"))
        }
    }

    @Test fun fullDiskCauseWinsOverAnOuterArchiveError() {
        val storage = FileSystemException("private/path", null, "No space left on device")
        val error = PackException(FailureReason.INVALID_ARCHIVE, "private imported text", storage)
        assertEquals(FailureReason.INSUFFICIENT_STORAGE, FailureClassifier.reason(error))
        assertEquals(FailureReason.INSUFFICIENT_STORAGE,
            FailureClassifier.reason(IOException("write failed: ENOSPC")))
    }

    @Test fun storageAccessFailureIsRecognizedAndFallbackIsPreserved() {
        assertEquals(FailureReason.STORAGE_UNAVAILABLE,
            FailureClassifier.reason(AccessDeniedException("private/path")))
        assertEquals(FailureReason.INVALID_ARCHIVE,
            FailureClassifier.reason(IllegalStateException("private text"), FailureReason.INVALID_ARCHIVE))
    }

    @Test fun archivePreservesCancellationAndCleansItsTemporaryFiles() {
        inTemporaryDirectory { root ->
            val input = object : java.io.InputStream() {
                override fun read(): Int = throw CancellationException("Cancelled")
            }
            try {
                PackArchive.extract(input, root.resolve("cancelled").toFile())
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(emptyList<String>(), root.toFile().listFiles().orEmpty().map { it.name })
            }
        }
    }

    @Test fun causeCyclesCannotHangErrorPresentation() {
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)
        assertEquals(FailureReason.UNKNOWN, FailureClassifier.reason(first))
    }

    private fun assertReason(expected: FailureReason, block: () -> Unit): PackException {
        try { block() } catch (error: PackException) {
            assertEquals(expected, error.reason)
            return error
        }
        throw AssertionError("Expected $expected")
    }

    private fun zip(files: Map<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { archive -> files.forEach { (name, text) ->
            archive.putNextEntry(ZipEntry(name)); archive.write(text.toByteArray()); archive.closeEntry()
        } }
        output.toByteArray()
    }

    private fun inTemporaryDirectory(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("language-failure-test")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }
}
