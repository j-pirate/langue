package org.langue.engine

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import kotlinx.serialization.decodeFromString

/** A bounded ZIP reader. Extraction is staged and never overwrites an existing destination. */
object PackArchive {
    const val MAX_ARCHIVE_BYTES: Long = 64L * 1024 * 1024
    const val MAX_EXTRACTED_BYTES: Long = 128L * 1024 * 1024
    const val MAX_FILE_BYTES: Long = 32L * 1024 * 1024
    const val MAX_JSON_BYTES: Long = 8L * 1024 * 1024
    const val MAX_FILES: Int = 5_000

    fun extract(input: InputStream, destination: File): LanguagePack {
        PackValidator.requirePack(!destination.exists(), "La destination existe déjà.")
        val parent = destination.absoluteFile.parentFile
        PackValidator.requirePack(parent.isDirectory || parent.mkdirs(), "Dossier d’import inaccessible.")
        val staging = Files.createTempDirectory(parent.toPath(), ".pack-").toFile()
        try {
            val archiveFile = File(staging, "archive.zip")
            LimitedInput(input, MAX_ARCHIVE_BYTES).use { source ->
                archiveFile.outputStream().use { output -> source.copyTo(output) }
            }
            // ZipInputStream alone accepts a truncated archive without its central directory.
            // Opening ZipFile first verifies that the archive is structurally complete.
            val centralNames = ZipFile(archiveFile).use { zip ->
                PackValidator.requirePack(zip.size() <= MAX_FILES, "Trop de fichiers dans l’archive.")
                zip.entries().asSequence().map { it.name }.toList()
            }
            val extraction = File(staging, "files").apply { mkdir() }
            val localNames = mutableListOf<String>()
            val names = mutableSetOf<String>()
            var total = 0L
            var count = 0
            val files = mutableListOf<String>()
            ZipInputStream(archiveFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    localNames += item.name
                    count++
                    PackValidator.requirePack(count <= MAX_FILES, "Trop de fichiers dans l’archive.")
                    val path = if (item.isDirectory) item.name.removeSuffix("/") else item.name
                    PackValidator.requirePack(PackValidator.safeArchivePath(path), "Chemin dangereux dans l’archive.")
                    val normalized = Normalizer.normalize(path, Normalizer.Form.NFC).lowercase(Locale.ROOT)
                    PackValidator.requirePack(names.add(normalized), "Chemin dupliqué dans l’archive.")
                    val output = File(extraction, path)
                    PackValidator.requirePack(output.canonicalPath.startsWith(extraction.canonicalPath + File.separator), "Chemin hors du pack.")
                    if (item.isDirectory) {
                        PackValidator.requirePack(output.mkdirs() || output.isDirectory, "Dossier invalide.")
                        PackValidator.requirePack(zip.read() == -1, "Une entrée de dossier contient des données.")
                    } else {
                        PackValidator.requirePack(output.parentFile.mkdirs() || output.parentFile.isDirectory, "Chemins en conflit.")
                        var size = 0L
                        output.outputStream().buffered().use { target ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                size += read
                                total += read
                                PackValidator.requirePack(size <= MAX_FILE_BYTES && total <= MAX_EXTRACTED_BYTES, "Archive trop volumineuse après extraction.")
                                target.write(buffer, 0, read)
                            }
                        }
                        files += path
                    }
                    zip.closeEntry()
                }
            }
            PackValidator.requirePack(localNames.sorted() == centralNames.sorted(), "Les en-têtes de l’archive sont incohérents.")
            val manifests = files.filter { it == "manifest.json" || it.endsWith("/manifest.json") }
            PackValidator.requirePack(manifests.size == 1, "Un unique manifest.json est requis.")
            val manifestPath = manifests.single()
            val prefix = manifestPath.removeSuffix("manifest.json")
            PackValidator.requirePack(prefix.isEmpty() || prefix.count { it == '/' } == 1, "Le pack doit être à la racine ou dans un unique dossier.")
            PackValidator.requirePack(files.all { it.startsWith(prefix) }, "Plusieurs racines dans l’archive.")
            val root = if (prefix.isEmpty()) extraction else File(extraction, prefix)
            val relativeFiles = files.map { it.removePrefix(prefix) }
            val required = setOf("manifest.json", "categories.json", "entries.json")
            PackValidator.requirePack(relativeFiles.containsAll(required), "manifest.json, categories.json et entries.json sont requis.")
            PackValidator.requirePack(relativeFiles.all { it in required || PackValidator.validAudioPath(it) }, "Fichier non pris en charge dans le pack.")
            val manifest = PackJson.json.decodeFromString<Manifest>(readJson(File(root, "manifest.json")))
            val categories = PackJson.json.decodeFromString<List<Category>>(readJson(File(root, "categories.json")))
            val entries = PackJson.json.decodeFromString<List<Entry>>(readJson(File(root, "entries.json")))
            val pack = LanguagePack(manifest, categories, entries)
            PackValidator.validate(pack)
            entries.mapNotNull { it.audio }.forEach { path ->
                PackValidator.requirePack(File(root, path).isFile, "Fichier audio référencé absent : $path.")
            }
            // Same-filesystem move publishes the complete validated pack, with an optional wrapper removed.
            Files.move(root.toPath(), destination.toPath())
            return pack
        } catch (failure: Exception) {
            if (failure is PackException) throw failure
            throw PackException("Pack illisible ou malformé.", failure)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun readJson(file: File): String {
        PackValidator.requirePack(file.length() <= MAX_JSON_BYTES, "Fichier JSON trop volumineux.")
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(java.nio.ByteBuffer.wrap(file.readBytes())).toString()
        // Bound parser recursion before handing untrusted JSON to serialization.
        var depth = 0
        var inString = false
        var escaped = false
        for (character in text) {
            if (inString) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') inString = false
            } else when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    PackValidator.requirePack(depth <= 32, "JSON trop profondément imbriqué.")
                }
                '}', ']' -> depth--
            }
        }
        return text
    }

    private class LimitedInput(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) record(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, length).also { if (it > 0) record(it) }
        private fun record(amount: Int) {
            count += amount
            PackValidator.requirePack(count <= limit, "Archive compressée trop volumineuse.")
        }
    }
}
