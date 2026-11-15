package org.langue.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import org.langue.engine.Category
import org.langue.engine.Entry
import org.langue.engine.Language
import org.langue.engine.LanguagePack
import org.langue.engine.Manifest
import org.langue.engine.PackJson

/** Entirely synthetic content; this source set is never packaged in the application. */
internal object TestPacks {
    fun pack(
        id: String = "test.pack",
        version: Int = 1,
        targetTag: String = "ar",
        entries: List<Entry> = listOf(
            Entry("item.1", "test.category", "Source alpha", "هدف ألف", listOf("بديل ألف"), "hadaf alif", "audio/item.wav"),
            Entry("item.2", "test.category", "Source bêta", "هدف باء"),
            Entry("item.3", "test.category", "Source gamma", "هدف جيم"),
        ),
    ) = LanguagePack(
        Manifest(id = id, name = "Test $id", version = version,
            sourceLanguage = Language("fr", "Source"), targetLanguage = Language(targetTag, "Target", "rtl")),
        listOf(Category("test.category", "Catégorie de test", order = 1)),
        entries,
    )

    fun zip(pack: LanguagePack): ByteArrayInputStream = ByteArrayInputStream(archive(
        linkedMapOf(
            "manifest.json" to PackJson.json.encodeToString(pack.manifest).toByteArray(),
            "categories.json" to PackJson.json.encodeToString(pack.categories).toByteArray(),
            "entries.json" to PackJson.json.encodeToString(pack.entries).toByteArray(),
        ).also { files ->
            pack.entries.mapNotNull { it.audio }.forEach { files[it] = tinyWave() }
        }
    ))

    fun archive(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    // Half-second silent PCM WAV used exclusively by automated local-file/decoder checks.
    fun tinyWave(): ByteArray {
        val payloadBytes = 8000
        val data = java.nio.ByteBuffer.allocate(44 + payloadBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + payloadBytes).put("WAVEfmt ".toByteArray()).putInt(16)
        data.putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(payloadBytes).put(ByteArray(payloadBytes))
        return data.array()
    }
}
